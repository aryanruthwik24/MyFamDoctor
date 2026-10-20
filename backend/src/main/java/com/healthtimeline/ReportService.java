package com.healthtimeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class ReportService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final String MEDICAL_REPORT_PROMPT = """
Extract data from this medical report. Return ONLY valid JSON, no markdown:
{
  "report_type": "BLOOD_TEST | XRAY | MRI | PRESCRIPTION | DISCHARGE_SUMMARY | OTHER",
  "report_date": "YYYY-MM-DD or null",
  "provider_name": "string or null",
  "summary": "3-5 line plain-language summary",
  "extracted_text": "full text of the report",
  "values": [
    {"test_name": "", "value_num": 0.0, "value_text": null,
     "unit": "", "ref_low": null, "ref_high": null,
     "flag": "LOW | HIGH | NORMAL | null"}
  ]
}
Do not invent anything. Use null when a field is not in the report.
""";

    private final HealthReportRepository healthReportRepository;
    private final UserRepository userRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final Path uploadDirectory;
    private final Tika tika = new Tika();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Value("${app.gemini-api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini-model:gemini-3.8-flash}")
    private String geminiModel;

    public ReportService(
            HealthReportRepository healthReportRepository,
            UserRepository userRepository,
            PdfTextExtractor pdfTextExtractor,
            @Value("${app.upload-dir}") String uploadDirectory
    ) {
        this.healthReportRepository = healthReportRepository;
        this.userRepository = userRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.uploadDirectory = Paths.get(uploadDirectory);
    }

    public User getUser(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public List<Map<String, Object>> getReportsForUser(String email) {
        User user = getUser(email);
        if (user == null || user.getId() == null) {
            return List.of();
        }

        return healthReportRepository.findByUserIdOrderByUploadedAtDesc(user.getId()).stream()
                .map(this::toSummary)
                .toList();
    }

    public HealthReport uploadReport(String email, MultipartFile file, String title, String reportDate, String type) throws Exception {
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Your account could not be found.");
        }

        if (file == null) {
            throw new IllegalArgumentException("Please select a valid file to upload.");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("The selected file is empty. Please choose a valid PDF or document.");
        }

        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("Report title is required.");
        }

        User user = getUser(email);
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Your account could not be found.");
        }

        Path root = uploadDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "report");
        String safeName = Paths.get(originalName).getFileName().toString();
        if (safeName.isBlank()) {
            safeName = "report";
        }

        String storedName = UUID.randomUUID() + "-" + safeName;
        Path target = root.resolve(storedName).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file name.");
        }

        byte[] uploadedBytes = file.getBytes();
        if (uploadedBytes == null || uploadedBytes.length == 0) {
            throw new IllegalArgumentException("The selected file is empty.");
        }

        // Keep the original upload. Rendering and recompressing every PDF page
        // blocks the request for a long time and can reduce extraction quality.
        byte[] storedBytes = uploadedBytes;

        System.err.println("UPLOAD_DEBUG: writing file to disk: " + target);
        file.transferTo(target);

        HealthReport healthReport = new HealthReport();
        healthReport.setUserId(user.getId());
        healthReport.setFileName(safeName);
        healthReport.setContentType(file.getContentType() == null ? "application/pdf" : file.getContentType());
        if (reportDate != null && !reportDate.isBlank()) {
            try {
                healthReport.setReportDate(LocalDate.parse(reportDate));
            } catch (Exception ignored) {
                healthReport.setReportDate(LocalDate.now());
            }
        } else {
            healthReport.setReportDate(LocalDate.now());
        }
        if (type != null && !type.isBlank()) {
            healthReport.setReportType(type.trim());
        } else {
            healthReport.setReportType("OTHER");
        }
        healthReport.setFileSize((long) storedBytes.length);
        healthReport.setData(storedBytes);
        healthReport.setUploadedAt(LocalDateTime.now());

        // Most digital PDFs already contain selectable text. Persist it right
        // away so the user can ask questions without waiting for the LLM.
        if (isPdfFile(file.getContentType(), safeName)) {
            String embeddedText = extractEmbeddedPdfText(uploadedBytes);
            if (!embeddedText.isBlank()) {
                healthReport.setExtractedText(embeddedText);
            }
        }

        System.err.println("UPLOAD_DEBUG: saving HealthReport to database: userId=" + user.getId() + ", fileName=" + safeName + ", size=" + storedBytes.length);
        HealthReport saved = healthReportRepository.saveAndFlush(healthReport);
        if (saved == null) {
            throw new IllegalStateException("The report could not be saved to the database.");
        }

        // Wait for the full upload pipeline to finish before returning a success
        // response. Otherwise the UI shows the report as uploaded before the
        // Gemini metadata is persisted to the database.
        if (isPdfFile(file.getContentType(), safeName)) {
            enrichReportAfterUpload(saved.getId(), uploadedBytes);
        }
        return saved;
    }

    public HealthReport getOwnedReport(String email, Long reportId) {
        User user = getUser(email);
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Your account could not be found.");
        }

        HealthReport report = healthReportRepository.findById(reportId).orElse(null);
        if (report == null) {
            throw new IllegalArgumentException("Report not found.");
        }
        if (!report.getUserId().equals(user.getId())) {
            throw new SecurityException("This report does not belong to the signed-in user.");
        }
        return report;
    }

    public byte[] getReportBytes(String email, Long reportId) {
        HealthReport report = getOwnedReport(email, reportId);
        return report.getData();
    }

    public void deleteReport(String email, Long reportId) {
        HealthReport report = getOwnedReport(email, reportId);
        healthReportRepository.delete(report);
    }

    public Map<String, Object> toSummary(HealthReport report) {
        String title = report.getFileName();
        if (title != null && title.contains(".")) {
            title = title.substring(0, title.lastIndexOf('.'));
        }
        if (title == null || title.isBlank()) {
            title = "Uploaded report";
        }

        LocalDate reportDate = report.getReportDate() != null
                ? report.getReportDate()
                : report.getUploadedAt().toLocalDate();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", report.getId());
        summary.put("title", title);
        summary.put("reportDate", reportDate.toString());
        summary.put("type", report.getReportType() != null ? report.getReportType() : "Health report");
        summary.put("fileName", report.getFileName());
        summary.put("createdAt", report.getUploadedAt() == null ? null : report.getUploadedAt().toString());
        summary.put("fileSize", report.getFileSize());
        summary.put("contentType", report.getContentType());
        summary.put("summary", report.getSummary() == null ? "" : report.getSummary());
        summary.put("extractedText", report.getExtractedText() == null ? "" : report.getExtractedText());
        summary.put("values", report.getValues() == null ? "[]" : report.getValues());
        summary.put("biomarkers", List.of());
        return summary;
    }

    private boolean isPdfFile(String contentType, String fileName) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase();
        return lowerContentType.contains("pdf") || lowerFileName.endsWith(".pdf");
    }

    private String extractEmbeddedPdfText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "";
        }

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            if (document.isEncrypted()) {
                return "";
            }
            return new PDFTextStripper().getText(document).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private Map<String, Object> extractMedicalReportMetadata(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0 || geminiApiKey == null || geminiApiKey.isBlank()) {
            return Map.of();
        }

        try {
            String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("contents", List.of(Map.of(
                    "parts", List.of(
                            Map.of("text", MEDICAL_REPORT_PROMPT),
                            Map.of("inline_data", Map.of(
                                    "mime_type", "application/pdf",
                                    "data", pdfBase64
                            ))
                    )
            )));

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(String.format(GEMINI_API_URL, geminiModel, geminiApiKey)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[GEMINI_UPLOAD_RESPONSE] status=" + response.statusCode());
            System.out.println("[GEMINI_UPLOAD_RESPONSE_BODY] " + response.body());
            if (response.statusCode() / 100 != 2) {
                System.err.println("LLM extraction failed with HTTP status " + response.statusCode());
                return Map.of();
            }

            return parseExtractionPayload(response.body());
        } catch (Exception exception) {
            // Preserve the uploaded report even when the external LLM is unavailable.
            System.err.println("LLM extraction failed: " + exception.getMessage());
            return Map.of();
        }
    }

    private void enrichReportAfterUpload(Long reportId, byte[] pdfBytes) {
        Map<String, Object> extractedMetadata = extractMedicalReportMetadata(pdfBytes);
        if (extractedMetadata.isEmpty()) {
            return;
        }

        HealthReport report = healthReportRepository.findById(reportId).orElse(null);
        if (report == null) {
            return;
        }

        applyExtractionMetadata(report, extractedMetadata);
        healthReportRepository.save(report);
    }

    private void applyExtractionMetadata(HealthReport report, Map<String, Object> extractedMetadata) {
        if (extractedMetadata == null || extractedMetadata.isEmpty()) {
            return;
        }

        String summary = extractedMetadata.get("summary") == null
                ? ""
                : String.valueOf(extractedMetadata.get("summary")).trim();
        String extractedText = extractedMetadata.get("extracted_text") == null
                ? ""
                : String.valueOf(extractedMetadata.get("extracted_text")).trim();
        Object values = extractedMetadata.get("values");

        if (!summary.isBlank()) {
            report.setSummary(summary);
        }
        if (!extractedText.isBlank()) {
            report.setExtractedText(extractedText);
        }
        if (values != null) {
            String valuesJson = values instanceof String
                    ? ((String) values).trim()
                    : serializeJsonValue(values);
            if (!valuesJson.isBlank()) {
                report.setValues(valuesJson);
            }
        }
    }

    public static Map<String, Object> parseExtractionPayload(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }

        String cleaned = rawJson.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("(?s)^```(?:json)?\\s*", "")
                    .replaceFirst("(?s)\\s*```$", "");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(cleaned);
            Map<String, Object> result = new LinkedHashMap<>();
            addFieldIfPresent(result, root, "summary");
            addFieldIfPresent(result, root, "extracted_text");
            addFieldIfPresent(result, root, "report_type");
            addFieldIfPresent(result, root, "provider_name");
            addJsonArrayFieldIfPresent(result, root, "values");
            if (!result.isEmpty()) {
                return result;
            }

            JsonNode candidates = root.path("candidates");
            if (candidates.isArray()) {
                for (JsonNode candidate : candidates) {
                    JsonNode parts = candidate.path("content").path("parts");
                    for (JsonNode part : parts) {
                        if (!part.has("text")) {
                            continue;
                        }
                        String text = part.path("text").asText();
                        if (text == null || text.isBlank()) {
                            continue;
                        }
                        Map<String, Object> nestedResult = parseExtractionPayload(text);
                        if (!nestedResult.isEmpty()) {
                            return nestedResult;
                        }
                    }
                }
            }

            return Map.of();
        } catch (Exception ignored) {
            return parseRawFields(cleaned);
        }
    }

    private static void addFieldIfPresent(Map<String, Object> target, JsonNode root, String fieldName) {
        if (root == null || !root.isObject() || !root.has(fieldName)) {
            return;
        }
        JsonNode valueNode = root.path(fieldName);
        String textValue = valueNode == null || valueNode.isNull() ? null : valueNode.asText();
        if (textValue != null && !textValue.isBlank()) {
            target.put(fieldName, textValue);
        }
    }

    private static void addJsonArrayFieldIfPresent(Map<String, Object> target, JsonNode root, String fieldName) {
        if (root == null || !root.isObject() || !root.has(fieldName)) {
            return;
        }
        JsonNode valueNode = root.path(fieldName);
        if (valueNode == null || valueNode.isNull() || !valueNode.isArray()) {
            return;
        }
        target.put(fieldName, serializeJsonValue(valueNode));
    }

    private static String serializeJsonValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private static Map<String, Object> parseRawFields(String rawJson) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String fieldName : List.of("summary", "extracted_text", "report_type", "provider_name", "values")) {
            String pattern = "\\\"" + fieldName + "\\\"\\s*:\\s*(\"((?:\\\\.|[^\\\"\\\\])*)\"|\\[[\\s\\S]*?\\])";
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(rawJson);
            if (matcher.find()) {
                String value = matcher.group(1);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1).replace("\\n", "\n").replace("\\\"", "\"");
                }
                result.put(fieldName, value);
            }
        }
        return result;
    }

    private String extractText(Path file, String originalName) {
        try {
            if (isPdf(file, originalName)) {
                String text = pdfTextExtractor.extract(file);
                return text.isBlank() ? noTextMessage() : text;
            }
            return tika.parseToString(file.toFile()).trim();
        } catch (Exception exception) {
            return noTextMessage();
        }
    }

    private boolean isPdf(Path file, String originalName) {
        if (originalName.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        try {
            return "application/pdf".equalsIgnoreCase(tika.detect(file));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String noTextMessage() {
        return "[No readable text was extracted. This may be an image-only scan.]";
    }
}
