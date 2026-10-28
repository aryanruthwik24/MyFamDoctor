package com.healthtimeline;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(String fullName, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        String trimmedName = fullName == null ? "" : fullName.trim();
        String trimmedPassword = password == null ? "" : password;

        if (normalizedEmail.isBlank() || trimmedPassword.isBlank() || trimmedName.isBlank()) {
            throw new IllegalArgumentException("Full name, email and password are required.");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        User user = new User(trimmedName, normalizedEmail, passwordEncoder.encode(trimmedPassword));
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        String trimmedPassword = password == null ? "" : password;

        if (normalizedEmail.isBlank() || trimmedPassword.isBlank()) {
            throw new IllegalArgumentException("Email and password are required.");
        }

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        if (!passwordEncoder.matches(trimmedPassword, user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse me(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Session is invalid.");
        }
        return buildAuthResponse(user);
    }

    public AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getFullName(), user.getEmail(), user.getRole());
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
