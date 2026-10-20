package com.healthtimeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerTest {

    @Test
    void buildGeminiRequestIncludesPdfAttachmentWhenProvided() {
        String json = ChatController.buildGeminiRequest(
                "gemini-3.8-flash",
                "Use the uploaded PDF context.",
                "SGVsbG8="
        );

        assertTrue(json.contains("\"model\":\"gemini-3.8-flash\""));
        assertTrue(json.contains("\"mime_type\":\"application/pdf\""));
        assertTrue(json.contains("\"data\":\"SGVsbG8=\""));
    }
    @Test
    void buildGeminiRequestSkipsVeryLargePdfAttachment() {
        String hugePdf = "A".repeat(2_000_000);
        String json = ChatController.buildGeminiRequest(
                "gemini-3.8-flash",
                "Use the uploaded PDF context.",
                hugePdf
        );

        assertTrue(json.contains("\"model\":\"gemini-3.8-flash\""));
        assertTrue(json.contains("Use the uploaded PDF context."));
        assertTrue(!json.contains("\"mime_type\":\"application/pdf\""));
    }}
