package com.healthtimeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private static final int MAX_REPORT_CONTEXT_CHARACTERS = 30_000;
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HealthReportRepository healthReportRepository;
    private final UserRepository userRepository;
    private final String baseUrl;
    private final String model;
    private final String key;
    private final String geminiApiKey;
    private final String geminiModel;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public ChatService(
            HealthReportRepository healthReportRepository,
            UserRepository userRepository,
            @Value("${app.ollama-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${app.ollama-model:qwen2.5:3b}") String model,
            @Value("${app.ollama-api-key:}") String key,
            @Value("${app.gemini-api-key:}") String geminiApiKey,
            @Value("${app.gemini-model:gemini-3.8-flash}") String geminiModel
    ) {
        this.healthReportRepository = healthReportRepository;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
        this.model = model;
        this.key = key;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
    }

    public ResponseEntity<?> ask(String email, String question) throws Exception {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getId() == null) {
            return ResponseEntity.status(401).body(Map.of("answer", "Your session is invalid. Please sign in again."));
        }

        String context = healthReportRepository.findByUserIdOrderByUploadedAtDesc(user.getId()).stream()
                .map(report -> {
                    String summary = report.getSummary() != null ? report.getSummary().trim() : "";
                    String extracted = report.getExtractedText() != null ? report.getExtractedText().trim() : "";
                    String body = summary.isBlank() ? extracted : summary;
                    if (body.isBlank()) {
                        return null;
                    }
                    return "REPORT: " + report.getFileName()
                            + " | Date: " + report.getReportDate()
                            + " | Type: " + report.getReportType()
                            + "\nSUMMARY:\n" + body;
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));
        if (context.length() > MAX_REPORT_CONTEXT_CHARACTERS) {
            context = context.substring(0, MAX_REPORT_CONTEXT_CHARACTERS);
        }

        if (context.isBlank()) {
            context = "No extracted text is available from this user's uploaded reports yet.";
        }

        if (!geminiApiKey.isBlank()) {
            return askGemini(context, question);
        }

        return askOllama(context, question);
    }

    /**
     * Guest conversations are deliberately stateless: the PDF bytes exist
     * only in this request and are never read from or written to the database.
     */
    public ResponseEntity<?> askGuest(String question, List<MultipartFile> files) throws Exception {
        if (geminiApiKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("answer", "Guest PDF questions require Gemini to be configured."));
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", "You are a careful health-report assistant. Answer the user's question concisely and only from the attached PDF report. Do not diagnose, prescribe, or claim certainty. Explain medical terms plainly and advise professional or urgent care when appropriate.\n\nUSER QUESTION: " + question));

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
            String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
            if (!contentType.contains("pdf") && !name.endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of("answer", "Only PDF files are supported for guest questions."));
            }
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", "application/pdf",
                    "data", java.util.Base64.getEncoder().encodeToString(file.getBytes())
            )));
        }

        if (parts.size() == 1) {
            return ResponseEntity.badRequest().body(Map.of("answer", "Upload at least one non-empty PDF before asking a question."));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", parts)));
        body.put("generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 2048));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(String.format(GEMINI_API_URL, geminiModel, geminiApiKey)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            return ResponseEntity.status(502).body(Map.of("answer", "Gemini could not answer the request. Check the API key and model configuration."));
        }
        return ResponseEntity.ok(Map.of("answer", extractGeminiAnswer(response.body())));
    }

    private ResponseEntity<?> askOllama(String context, String question) throws Exception {
        String prompt = "You are a careful health-report assistant. Answer only from the supplied report context. State when the records do not contain the answer. Do not diagnose, prescribe, or claim certainty. Explain medical terms plainly and advise professional or urgent care when appropriate.\n\nREPORT CONTEXT:\n" + context + "\n\nUSER QUESTION: " + question;
        String body = "{\"model\":\"" + esc(model) + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + esc(prompt) + "\"}],\"stream\":false,\"options\":{\"temperature\":0.2}}";

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json");
        if (!key.isBlank()) {
            builder.header("Authorization", "Bearer " + key);
        }

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            return ResponseEntity.status(502).body(Map.of("answer", "The AI service could not answer right now."));
        }

        return ResponseEntity.ok(Map.of("answer", extract(response.body())));
    }

    private ResponseEntity<?> askGemini(String context, String question) throws Exception {
        String prompt = "You are a careful health-report assistant. Answer only from the supplied report context. Mention when there is insufficient information. Do not diagnose, prescribe, or claim certainty. Explain medical terms plainly and advise professional care or urgent attention when appropriate. Provide the full answer in complete sentences. End with a complete final sentence and do not stop mid-thought. If the report lacks the needed information, say so plainly instead of guessing.\n\nREPORT CONTEXT:\n" + context + "\n\nUSER QUESTION: " + question;
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(Map.of("parts", parts)));
        body.put("generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 2048));
        String json = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(String.format(GEMINI_API_URL, geminiModel, geminiApiKey)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            return ResponseEntity.status(502).body(Map.of("answer", "Gemini could not answer the request. Check the API key and model configuration."));
        }

        return ResponseEntity.ok(Map.of("answer", extractGeminiAnswer(response.body())));
    }

    public static String buildGeminiRequest(String model, String prompt, String pdfBase64) {
        try {
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", prompt));
            if (pdfBase64 != null && !pdfBase64.isBlank() && pdfBase64.length() < 1_000_000) {
                parts.add(Map.of(
                        "inline_data", Map.of(
                                "mime_type", "application/pdf",
                                "data", pdfBase64
                        )
                ));
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("contents", List.of(Map.of("parts", parts)));
            return MAPPER.writeValueAsString(payload);
        } catch (IOException ignored) {
            return "{}";
        }
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    static String extract(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode message = root.path("message");
            if (message.has("content")) {
                return message.path("content").asText();
            }
            JsonNode content = root.path("content");
            if (content.isTextual()) {
                return content.asText();
            }
        } catch (Exception ignored) {
            return "I received a response, but could not read it.";
        }
        return "I received a response, but could not read it.";
    }

    static String extractGeminiAnswer(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode candidates = root.path("candidates");
            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate.path("content").path("parts");
                for (JsonNode part : parts) {
                    if (part.has("text")) {
                        return part.path("text").asText();
                    }
                }
            }
        } catch (Exception ignored) {
            return "Gemini returned a response, but it could not be parsed.";
        }
        return "Gemini returned a response, but it could not be parsed.";
    }
}
