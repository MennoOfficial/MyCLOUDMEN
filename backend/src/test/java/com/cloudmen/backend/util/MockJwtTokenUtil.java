package com.cloudmen.backend.util;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for creating mock JWT tokens for testing
 */
public class MockJwtTokenUtil {

    /**
     * Creates a mock JWT with default claims
     * 
     * @return A mock JWT with standard claims
     */
    public static Jwt createDefaultJwt() {
        return createJwt("test-user", "test@example.com");
    }

    /**
     * Creates a mock JWT with the specified subject and email
     * 
     * @param subject The subject claim (Auth0 user ID)
     * @param email   The email claim
     * @return A mock JWT with the specified claims
     */
    public static Jwt createJwt(String subject, String email) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "HS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("scope", "read:users write:users");
        claims.put("iss", "https://dev-example.us.auth0.com/");
        claims.put("aud", "https://mycloudmen-api");

        return Jwt.withTokenValue("mock-jwt-token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    /**
     * Creates a JWT with custom claims
     * 
     * @param tokenValue The raw token value
     * @param claims     The claims to include in the token
     * @return A mock JWT with the specified claims
     */
    public static Jwt createJwtWithCustomClaims(String tokenValue, Map<String, Object> claims) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "HS256");
        headers.put("typ", "JWT");

        return Jwt.withTokenValue(tokenValue)
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    /**
     * Creates an expired JWT token
     * 
     * @param subject The subject claim
     * @param email   The email claim
     * @return An expired mock JWT
     */
    public static Jwt createExpiredJwt(String subject, String email) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "HS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("scope", "read:users write:users");
        claims.put("iss", "https://dev-example.us.auth0.com/");
        claims.put("aud", "https://mycloudmen-api");

        return Jwt.withTokenValue("expired-mock-jwt-token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
    }

    /**
     * Creates a JWT with invalid audience
     * 
     * @param subject The subject claim
     * @param email   The email claim
     * @return A mock JWT with invalid audience
     */
    public static Jwt createJwtWithInvalidAudience(String subject, String email) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "HS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("scope", "read:users write:users");
        claims.put("iss", "https://dev-example.us.auth0.com/");
        claims.put("aud", "https://invalid-audience");

        return Jwt.withTokenValue("invalid-audience-token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}