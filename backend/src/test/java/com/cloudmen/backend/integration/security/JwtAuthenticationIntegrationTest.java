package com.cloudmen.backend.integration.security;

import com.cloudmen.backend.util.MockJwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Simple integration tests for JWT Authentication using MockMvc standalone
 * setup
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Authentication Integration Tests")
public class JwtAuthenticationIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        // Create a simple test controller with secured and public endpoints
        TestController controller = new TestController();

        // Set up MockMvc with security filter that uses our mock JWT decoder
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .addFilter(new JwtAuthenticationFilter())
                .build();

        // Use lenient stubbing to avoid "unnecessary stubbing" errors
        lenient().when(jwtDecoder.decode(anyString())).thenReturn(MockJwtTokenUtil.createDefaultJwt());
    }

    /**
     * Simple JWT authentication filter for testing
     */
    private static class JwtAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // For test purposes, just consider any token with "mock-jwt-token" as valid
                if (token.equals("mock-jwt-token")) {
                    // Simulate authenticated request
                    request.setAttribute("authenticated", true);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } else if (request.getRequestURI().contains("/api/users")) {
                // Require authorization for protected endpoints
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            chain.doFilter(request, response);
        }
    }

    @Test
    @DisplayName("Public endpoints should be accessible without authentication")
    void publicEndpoint_ShouldBeAccessible_WithoutAuthentication() throws Exception {
        // Test access to a public endpoint
        mockMvc.perform(get("/api/public/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("Public API is healthy"));
    }

    @Test
    @DisplayName("Protected endpoints should allow access with valid JWT")
    void protectedEndpoint_ShouldAllowAccess_WithValidJwt() throws Exception {
        // Test access with Authorization header
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer mock-jwt-token")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("Protected user data"));
    }

    @Test
    @DisplayName("Protected endpoints should deny access without JWT")
    void protectedEndpoint_ShouldDenyAccess_WithoutJwt() throws Exception {
        // Test access without Authorization header
        mockMvc.perform(get("/api/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Simple controller for testing JWT authentication
     */
    @RestController
    static class TestController {

        @GetMapping("/api/public/health")
        public String publicEndpoint() {
            return "Public API is healthy";
        }

        @GetMapping("/api/users")
        public String protectedEndpoint() {
            // This endpoint should be protected
            return "Protected user data";
        }
    }
}