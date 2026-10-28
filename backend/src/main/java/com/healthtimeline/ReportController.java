package com.healthtimeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin
public class ReportController {
    private final ReportService reportService;

    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public List<Map<String, Object>> all(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return List.of();
        }
        return reportService.getReportsForUser(principal.getName());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            Principal principal,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String reportDate,
            @RequestParam(required = false) String type
    ) throws Exception {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Sign in to save this report to your account."));
        }

        if (file == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a valid file to upload."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The selected file is empty. Please choose a valid PDF or document."));
        }

        if (file.getSize() > 1024L * 1024L) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "Uploaded report exceeds the 1MB limit. Please upload a smaller file."));
        }

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Report title is required."));
        }

        if (reportDate == null || reportDate.isBlank()) {
            reportDate = java.time.LocalDate.now().toString();
        }

        if (type == null || type.isBlank()) {
            type = "OTHER";
        }

        try {
            HealthReport saved = reportService.uploadReport(principal.getName(), file, title, reportDate, type);
            return ResponseEntity.status(HttpStatus.CREATED).body(reportService.toSummary(saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            System.err.println("UPLOAD_CONTROLLER_EXCEPTION: " + ex.getClass().getName() + " - " + ex.getMessage());
            try {
                Path debugLog = Path.of(System.getProperty("user.dir"), "uploads", "upload-debug-stacktrace.log");
                Files.createDirectories(debugLog.getParent());
                Files.writeString(debugLog, trace + System.lineSeparator());
            } catch (Exception ignored) {
                // Best effort only; do not fail the upload response.
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "The report upload failed. Please try again with a valid file."));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            HealthReport healthReport = reportService.getOwnedReport(principal.getName(), id);
            byte[] fileBytes = reportService.getReportBytes(principal.getName(), id);
            if (fileBytes == null || fileBytes.length == 0) {
                return ResponseEntity.noContent().build();
            }

            String fileName = healthReport.getFileName() == null || healthReport.getFileName().isBlank()
                    ? "report.pdf"
                    : healthReport.getFileName();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    healthReport.getContentType() == null || healthReport.getContentType().isBlank()
                            ? "application/pdf"
                            : healthReport.getContentType()));
            headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
            headers.setContentLength(fileBytes.length);

            return new ResponseEntity<>(fileBytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Sign in to delete this report."));
        }

        try {
            reportService.deleteReport(principal.getName(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    public static Map<String, Object> parseExtractionPayload(String rawJson) {
        return ReportService.parseExtractionPayload(rawJson);
    }
}

