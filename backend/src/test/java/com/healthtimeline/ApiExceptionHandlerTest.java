package com.healthtimeline;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiExceptionHandlerTest {

    @Test
    void handleMaxUploadSizeExceededReturnsPayloadTooLarge() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        ResponseEntity<Map<String, String>> response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(100L)
        );

        Map<String, String> body = response.getBody();
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertNotNull(body);
        assertEquals("Uploaded file is too large. Please use a PDF under 100MB.", body.get("error"));
    }
}
