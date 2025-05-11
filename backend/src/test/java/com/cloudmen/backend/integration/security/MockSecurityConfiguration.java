package com.cloudmen.backend.integration.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration for mocking security components
 * This provides mock implementations of security beans for testing
 */
@TestConfiguration
public class MockSecurityConfiguration {

    /**
     * Create a mock JwtDecoder that always returns a valid JWT
     * This allows tests to bypass actual JWT validation
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            Map<String, Object> headers = new HashMap<>();
            headers.put("alg", "HS256");

            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "test-user");
            claims.put("email", "test@example.com");
            claims.put("scope", "read:users write:users");

            // Add Auth0 specific claims
            claims.put("iss", "https://dev-example.us.auth0.com/");
            claims.put("aud", "https://mycloudmen-api");

            return Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(headers))
                    .claims(c -> c.putAll(claims))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        };
    }
}