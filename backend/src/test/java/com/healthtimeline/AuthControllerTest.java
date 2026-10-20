package com.healthtimeline;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void loginReturnsUnauthorizedWhenUserDoesNotExist() {
        AuthService authService = mock(AuthService.class);
        when(authService.login("missing@example.com", "Password123!")).thenThrow(new IllegalArgumentException("Invalid email or password."));

        AuthController authController = new AuthController(authService);
        ResponseEntity<?> response = authController.login(new AuthRequest("missing@example.com", "Password123!"));

        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    void loginReturnsTokenWhenCredentialsAreValid() {
        String rawPassword = "Password123!";
        AuthService authService = mock(AuthService.class);
        when(authService.login("test@example.com", rawPassword)).thenReturn(new AuthResponse("test-token", "Test User", "test@example.com", "USER"));

        AuthController authController = new AuthController(authService);
        ResponseEntity<?> response = authController.login(new AuthRequest("test@example.com", rawPassword));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(((AuthResponse) response.getBody()).getToken());
    }
}
