package com.sentinelpay.fraudengine.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.sentinelpay.fraudengine.config.JwtTokenProvider;
import com.sentinelpay.fraudengine.dto.AuthResponse;
import com.sentinelpay.fraudengine.dto.LoginRequest;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtTokenProvider tokenProvider, PasswordEncoder passwordEncoder) {
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@RequestBody @Valid LoginRequest request) {
        return validateUserCredentials(request.getUsername(), request.getPassword())
                .map(valid -> {
                    if (valid) {
                        String token = tokenProvider.generateToken(request.getUsername(),
                                List.of("ROLE_USER"));
                        return ResponseEntity.ok(new AuthResponse(token, "Bearer"));
                    } else {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                    }
                });
    }

    private Mono<Boolean> validateUserCredentials(String username, String password) {
        // Mock validation - replace with real user service
        String mockStoredPassword = passwordEncoder.encode("password123");
        return Mono.just(passwordEncoder.matches(password, mockStoredPassword));
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<Map<String, Object>>> validateToken(@RequestHeader("Authorization") String authHeader) {
        return Mono.fromCallable(() -> {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                boolean isValid = tokenProvider.validateToken(token);

                Map<String, Object> response = new HashMap<>();
                response.put("valid", isValid);
                if (isValid) {
                    response.put("username", tokenProvider.getUsernameFromToken(token));
                }
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", "Missing token"));
        });
    }
}