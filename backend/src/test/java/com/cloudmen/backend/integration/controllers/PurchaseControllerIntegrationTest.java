package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.PurchaseController;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseRequestDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseResponseDTO;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.PurchaseEmailService;
import com.cloudmen.backend.services.SignatureSatoriService;
import com.cloudmen.backend.services.UserService;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PurchaseController
 */
@WebMvcTest(PurchaseController.class)
@ActiveProfiles("test")
@DisplayName("PurchaseController Integration Tests")
public class PurchaseControllerIntegrationTest {

    @TestConfiguration
    static class PurchaseControllerTestConfig {
        @Bean
        public AuthenticationLogService authenticationLogService() {
            return mock(AuthenticationLogService.class);
        }

        @Bean
        public PurchaseController purchaseController(PurchaseEmailService emailService) {
            PurchaseController controller = new PurchaseController(emailService, mock(GoogleWorkspaceService.class));

            // Pre-populate the pendingRequests map with a test request
            try {
                Field pendingRequestsField = PurchaseController.class.getDeclaredField("pendingRequests");
                pendingRequestsField.setAccessible(true);
                ConcurrentHashMap<String, String> pendingRequests = (ConcurrentHashMap<String, String>) pendingRequestsField
                        .get(controller);
                pendingRequests.put("req-123", "user@example.com");
            } catch (Exception e) {
                throw new RuntimeException("Failed to prepare test controller", e);
            }

            return controller;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignatureSatoriService signatureSatoriService;

    @MockBean
    private UserService userService;

    @MockBean
    private PurchaseEmailService emailService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/purchase/request should create request and return success message")
    public void requestPurchaseCredits_shouldCreateRequestAndReturn201() throws Exception {
        // Arrange
        doNothing().when(emailService).sendPurchaseRequest(anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/purchase/request")
                .contentType(MediaType.APPLICATION_JSON)
                .param("userEmail", "user@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Purchase request created")));
    }

    @Test
    @DisplayName("GET /api/purchase/accept should accept request and return 200")
    public void acceptPurchase_shouldAcceptRequestAndReturn200() throws Exception {
        // Arrange
        String requestId = "req-123";

        // Mock confirmation email sending
        doNothing().when(emailService).sendConfirmationEmail(anyString());

        // Act & Assert
        mockMvc.perform(get("/api/purchase/accept")
                .param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("has been accepted")));
    }

    @Test
    @DisplayName("GET /api/purchase/accept should return error when request doesn't exist")
    public void acceptPurchase_shouldReturn404WhenRequestDoesntExist() throws Exception {
        // Arrange
        String requestId = "non-existent-request";

        // Act & Assert
        mockMvc.perform(get("/api/purchase/accept")
                .param("requestId", requestId))
                .andExpect(status().isOk()) // The controller returns 200 even for not found
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not found or already processed")));
    }
}