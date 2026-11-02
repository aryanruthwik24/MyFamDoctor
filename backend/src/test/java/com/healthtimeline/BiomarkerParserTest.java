package com.healthtimeline;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BiomarkerParserTest {
    private final BiomarkerParser parser = new BiomarkerParser();

    @Test
    void parsesMarkersWhenTheyAppearInsideAReportLine() {
        String text = "Lab summary: Hemoglobin 11.7 g/dL and HbA1c 6.4% are within the reported ranges.";
        List<Biomarker> biomarkers = parser.parse(text, new Report());

        assertFalse(biomarkers.isEmpty(), "Expected the parser to extract biomarkers from inline report text.");
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.HEMOGLOBIN));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.HBA1C));
    }

    @Test
    void parsesMarkersWhenValueAndUnitAppearOnTheNextLine() {
        String text = "Lab Report\nHemoglobin\n11.7 g/dL\nReference range 12.0 - 16.0 g/dL\nHbA1c\n6.4 %";
        List<Biomarker> biomarkers = parser.parse(text, new Report());

        assertFalse(biomarkers.isEmpty(), "Expected the parser to recover biomarkers even when the PDF text wraps across lines.");
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.HEMOGLOBIN && item.value.compareTo(new java.math.BigDecimal("11.7")) == 0));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.HBA1C && item.value.compareTo(new java.math.BigDecimal("6.4")) == 0));
    }

    @Test
    void parsesTheRequestedLabBiomarkersFromPdfStyleText() {
        String text = "Complete blood count\nHaemoglobin 15.2 g/dL (safe range 12-17)\nPlatelets 245 10^9/L (safe range 150-450)\nLDL 96 mg/dL (safe range 0-100)\nHDL 46 mg/dL (safe range 40-80)\nVitamin D 42 ng/mL (safe range 30-80)\nVitamin B12 680 pg/mL (safe range 200-900)";
        List<Biomarker> biomarkers = parser.parse(text, new Report());

        assertFalse(biomarkers.isEmpty(), "Expected the parser to find the required lab biomarkers.");
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.HEMOGLOBIN && item.value.compareTo(new java.math.BigDecimal("15.2")) == 0));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.PLATELET_COUNT && item.value.compareTo(new java.math.BigDecimal("245")) == 0));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.LDL && item.value.compareTo(new java.math.BigDecimal("96")) == 0));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.HDL && item.value.compareTo(new java.math.BigDecimal("46")) == 0));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.VITAMIN_D && item.value.compareTo(new java.math.BigDecimal("42")) == 0));
        assertTrue(biomarkers.stream().anyMatch(item -> item.name == BiomarkerName.VITAMIN_B12 && item.value.compareTo(new java.math.BigDecimal("680")) == 0));
    }

    @Test
    void parseExtractionPayloadIgnoresLegacyExtractedTextField() {
        String json = """
                {
                  "report_type": "BLOOD_TEST",
                  "report_date": "2026-10-01",
                  "provider_name": "Dr. Smith",
                  "summary": "Hemoglobin and platelet counts are within the expected range.",
                  "values": [
                    {"test_name": "Hemoglobin", "value_num": 14.2, "value_text": null, "unit": "g/dL", "ref_low": 12.0, "ref_high": 16.0, "flag": "NORMAL"}
                  ]
                }
                """;

        var parsed = ReportController.parseExtractionPayload(json);

        assertEquals("Hemoglobin and platelet counts are within the expected range.", parsed.get("summary"));
        assertFalse(parsed.containsKey("extracted_text"));
        assertEquals("[{\"test_name\":\"Hemoglobin\",\"value_num\":14.2,\"value_text\":null,\"unit\":\"g/dL\",\"ref_low\":12.0,\"ref_high\":16.0,\"flag\":\"NORMAL\"}]", parsed.get("values"));
    }

    @SuppressWarnings("null")
    @Test
    void uploadCreatesAnAuthenticatedReportRecord() throws Exception {
        ReportService reportService = mock(ReportService.class);
        ReportController controller = new ReportController(reportService);

        HealthReport savedReport = new HealthReport();
        org.springframework.test.util.ReflectionTestUtils.setField(savedReport, "id", 77L);
        savedReport.setUserId(99L);
        savedReport.setFileName("lab-report.pdf");
        savedReport.setContentType("application/pdf");
        savedReport.setUploadedAt(java.time.LocalDateTime.now());

        when(reportService.uploadReport(eq("upload@example.com"), any(), eq("Lab report"), eq("2026-10-02"), eq("PRESCRIPTION")))
                .thenReturn(savedReport);
        when(reportService.toSummary(savedReport)).thenReturn(Map.of("id", 77L, "title", "Lab report"));

        MockMultipartFile file = new MockMultipartFile("file", "lab-report.pdf", "application/pdf", new byte[] {1, 2, 3, 4});
        ResponseEntity<?> response = controller.upload(() -> "upload@example.com", file, "Lab report", "2026-10-02", "PRESCRIPTION");

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        Map<?, ?> payload = (Map<?, ?>) response.getBody();
        assertNotNull(payload);
        Object payloadId = payload.get("id");
        assertNotNull(payloadId);
        assertEquals(77L, payloadId);
    }

    @Test
    void parsesSummaryAndValuesFromGeminiMedicalReportJson() {
        String json = """
                {
                  "report_type": "BLOOD_TEST",
                  "report_date": "2026-10-01",
                  "provider_name": "Dr. Smith",
                  "summary": "Hemoglobin and platelet counts are within the expected range. Vitamin D is slightly low but not severe. The patient has no urgent abnormal findings in this report.",
                  "values": [
                    {"test_name": "Hemoglobin", "value_num": 14.2, "value_text": null, "unit": "g/dL", "ref_low": 12.0, "ref_high": 16.0, "flag": "NORMAL"}
                  ]
                }
                """;

        var parsed = ReportController.parseExtractionPayload(json);

        assertEquals("Hemoglobin and platelet counts are within the expected range. Vitamin D is slightly low but not severe. The patient has no urgent abnormal findings in this report.", parsed.get("summary"));
        assertFalse(parsed.containsKey("extracted_text"));
        assertEquals("[{\"test_name\":\"Hemoglobin\",\"value_num\":14.2,\"value_text\":null,\"unit\":\"g/dL\",\"ref_low\":12.0,\"ref_high\":16.0,\"flag\":\"NORMAL\"}]", parsed.get("values"));
    }

    @Test
    void malformedJwtDoesNotCrashTheRequest() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserRepository userRepository = mock(UserRepository.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtService.extractEmail("invalid-token")).thenThrow(new RuntimeException("bad jwt"));

        filter.doFilter(request, response, chain);

        org.mockito.Mockito.verify(chain).doFilter(request, response);
    }
}
