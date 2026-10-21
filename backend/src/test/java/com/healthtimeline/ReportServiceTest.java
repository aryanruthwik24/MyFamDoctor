package com.healthtimeline;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void uploadReportRejectsBlankTitle() throws Exception {
        HealthReportRepository healthReportRepository = mock(HealthReportRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        Path uploadDir = Files.createTempDirectory("report-upload-test");
        User user = new User("Tester", "tester@example.com", "encoded");
        user.setEnabled(true);
        setUserId(user, 1L);
        when(userRepository.findByEmail("tester@example.com")).thenReturn(Optional.of(user));

        ReportService service = new ReportService(
                healthReportRepository,
                userRepository,
                pdfTextExtractor,
                uploadDir.toString()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadReport("tester@example.com", file, "   ", "2026-10-20", "LAB"));
    }

    @Test
    void uploadReportRejectsEmptyFile() throws Exception {
        HealthReportRepository healthReportRepository = mock(HealthReportRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        Path uploadDir = Files.createTempDirectory("report-upload-empty");
        User user = new User("Tester", "tester@example.com", "encoded");
        user.setEnabled(true);
        setUserId(user, 1L);
        when(userRepository.findByEmail("tester@example.com")).thenReturn(Optional.of(user));

        ReportService service = new ReportService(
                healthReportRepository,
                userRepository,
                pdfTextExtractor,
                uploadDir.toString()
        );

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                new byte[0]
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadReport("tester@example.com", emptyFile, "Sample", "2026-10-20", "LAB"));
    }

    @Test
    void uploadReportAcceptsValidPdf() throws Exception {
        HealthReportRepository healthReportRepository = mock(HealthReportRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        Path uploadDir = Files.createTempDirectory("report-upload-valid");
        User user = new User("Tester", "tester@example.com", "encoded");
        user.setEnabled(true);
        setUserId(user, 1L);
        when(userRepository.findByEmail("tester@example.com")).thenReturn(Optional.of(user));
        when(healthReportRepository.save(any(HealthReport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(healthReportRepository.saveAndFlush(any(HealthReport.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportService service = new ReportService(
                healthReportRepository,
                userRepository,
                pdfTextExtractor,
                uploadDir.toString()
        );

        MockMultipartFile validPdf = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes()
        );

        assertDoesNotThrow(() -> service.uploadReport("tester@example.com", validPdf, "Sample", "2026-10-20", "LAB"));
    }

    @Test
    void parseExtractionPayloadPreservesGeminiValuesArray() {
        String rawJson = """
                {
                  "summary": "Blood sugar is normal.",
                  "extracted_text": "Patient report text",
                  "values": [
                    {"test_name": "HbA1c", "value_num": 5.6, "unit": "%", "flag": "NORMAL", "value_summary": "Keep regular meals and movement."}
                  ]
                }
                """;

        Map<String, Object> payload = ReportService.parseExtractionPayload(rawJson);

        assertEquals("Blood sugar is normal.", payload.get("summary"));
        assertEquals("Patient report text", payload.get("extracted_text"));
        assertEquals("[{\"test_name\":\"HbA1c\",\"value_num\":5.6,\"unit\":\"%\",\"flag\":\"NORMAL\",\"value_summary\":\"Keep regular meals and movement.\"}]", payload.get("values"));
    }

    @Test
    void toSummaryExposesNestedValueSummaryInResponseValues() throws Exception {
        HealthReport report = new HealthReport();
        Field idField = HealthReport.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(report, 12L);
        report.setFileName("lab-report.pdf");
        report.setReportType("BLOOD_TEST");
        report.setReportDate(java.time.LocalDate.of(2026, 10, 21));
        report.setUploadedAt(java.time.LocalDateTime.of(2026, 10, 21, 12, 0));
        report.setFileSize(128L);
        report.setContentType("application/pdf");
        report.setValues("[{\"test_name\":\"HbA1c\",\"value_num\":5.6,\"unit\":\"%\",\"flag\":\"NORMAL\",\"value_summary\":\"Keep regular meals and movement.\"}]");

        Map<String, Object> response = new ReportService(null, null, null, Files.createTempDirectory("report-summary-values").toString()).toSummary(report);

        assertEquals(1, ((java.util.List<?>) response.get("values")).size());
        Map<?, ?> labValue = (Map<?, ?>) ((java.util.List<?>) response.get("values")).get(0);
        assertEquals("Keep regular meals and movement.", labValue.get("value_summary"));
    }

    @Test
    void uploadReportReturnsTheEnrichedReportAfterGeminiMetadataIsSaved() throws Exception {
        HealthReportRepository healthReportRepository = mock(HealthReportRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PdfTextExtractor pdfTextExtractor = mock(PdfTextExtractor.class);
        Path uploadDir = Files.createTempDirectory("report-upload-enriched");
        User user = new User("Tester", "tester@example.com", "encoded");
        user.setEnabled(true);
        setUserId(user, 1L);
        when(userRepository.findByEmail("tester@example.com")).thenReturn(Optional.of(user));

        HealthReport savedReport = new HealthReport();
        setUserId(savedReport, 99L);
        savedReport.setUserId(1L);
        savedReport.setFileName("sample.pdf");
        savedReport.setContentType("application/pdf");
        savedReport.setFileSize(10L);
        savedReport.setUploadedAt(java.time.LocalDateTime.now());
        savedReport.setData("abc".getBytes());

        HealthReport enrichedReport = new HealthReport();
        setUserId(enrichedReport, 99L);
        enrichedReport.setUserId(1L);
        enrichedReport.setFileName("sample.pdf");
        enrichedReport.setContentType("application/pdf");
        enrichedReport.setFileSize(10L);
        enrichedReport.setUploadedAt(savedReport.getUploadedAt());
        enrichedReport.setData("abc".getBytes());
        enrichedReport.setValues("[{\"test_name\":\"HbA1c\",\"value_summary\":\"Keep regular meals and movement.\"}]");

        when(healthReportRepository.saveAndFlush(any(HealthReport.class))).thenReturn(savedReport);
        when(healthReportRepository.save(any(HealthReport.class))).thenReturn(enrichedReport);
        when(healthReportRepository.findById(99L)).thenReturn(Optional.of(enrichedReport));

        ReportService service = new ReportService(
                healthReportRepository,
                userRepository,
                pdfTextExtractor,
                uploadDir.toString()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes()
        );

        HealthReport result = service.uploadReport("tester@example.com", file, "Sample", "2026-10-20", "LAB");

        assertEquals("[{\"test_name\":\"HbA1c\",\"value_summary\":\"Keep regular meals and movement.\"}]", result.getValues());
    }

    private static void setUserId(User user, long id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }

    private static void setUserId(HealthReport report, long id) throws Exception {
        Field field = HealthReport.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(report, id);
    }
}
