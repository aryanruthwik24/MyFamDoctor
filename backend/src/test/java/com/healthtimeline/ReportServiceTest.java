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
                    {"test_name": "HbA1c", "value_num": 5.6, "unit": "%", "flag": "NORMAL"}
                  ]
                }
                """;

        Map<String, Object> payload = ReportService.parseExtractionPayload(rawJson);

        assertEquals("Blood sugar is normal.", payload.get("summary"));
        assertEquals("Patient report text", payload.get("extracted_text"));
        assertEquals("[{\"test_name\":\"HbA1c\",\"value_num\":5.6,\"unit\":\"%\",\"flag\":\"NORMAL\"}]", payload.get("values"));
    }

    private static void setUserId(User user, long id) throws Exception {
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
