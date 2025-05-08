package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.PurchaseController;
import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.cloudmen.backend.services.PurchaseEmailService;
import com.cloudmen.backend.services.PurchaseRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
        public PurchaseController purchaseController(
                PurchaseEmailService emailService,
                GoogleWorkspaceService googleWorkspaceService,
                PurchaseRequestService purchaseRequestService) {
            return new PurchaseController(emailService, googleWorkspaceService, purchaseRequestService);
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll());
            return http.build();
        }

        @Bean
        public WebClient webClient() {
            return mock(WebClient.class);
        }

        @Bean
        public AuthenticationLogService authenticationLogService() {
            return mock(AuthenticationLogService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PurchaseEmailService emailService;

    @MockBean
    private GoogleWorkspaceService googleWorkspaceService;

    @MockBean
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/purchase/accept should accept request and return success")
    public void acceptPurchase_shouldAcceptRequestAndReturnSuccess() throws Exception {
        // Arrange
        String requestId = "req-123";
        String userEmail = "user@example.com";

        // Create a mock purchase request
        PurchaseRequest mockRequest = new PurchaseRequest(requestId, userEmail);
        mockRequest.setType("credits");
        mockRequest.setStatus("PENDING");
        mockRequest.setQuantity(100);
        mockRequest.setCost(50.0);

        // Mock service behavior
        when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.of(mockRequest));
        when(purchaseRequestService.savePurchaseRequest(any(PurchaseRequest.class))).thenReturn(mockRequest);
        doNothing().when(emailService).sendConfirmationEmail(anyString());

        // Act & Assert
        mockMvc.perform(get("/api/purchase/accept")
                .param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.type").value("purchase"));

        // Verify service calls
        verify(purchaseRequestService).getPurchaseRequestById(requestId);
        verify(purchaseRequestService).savePurchaseRequest(any(PurchaseRequest.class));
        verify(emailService).sendConfirmationEmail(userEmail);
    }

    @Test
    @DisplayName("GET /api/purchase/accept should return error when request doesn't exist")
    public void acceptPurchase_shouldReturnErrorWhenRequestDoesntExist() throws Exception {
        // Arrange
        String requestId = "non-existent-request";

        // Mock service behavior
        when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/purchase/accept")
                .param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Request not found"))
                .andExpect(jsonPath("$.requestId").value(requestId));

        // Verify service call
        verify(purchaseRequestService).getPurchaseRequestById(requestId);
    }

    @Test
    @DisplayName("GET /api/purchase/signature-satori/accept should accept credits request and return success")
    public void acceptSignatureSatoriCreditsRequest_shouldAcceptAndReturnSuccess() throws Exception {
        // Arrange
        String requestId = "req-456";
        String userEmail = "user@example.com";

        // Create a mock purchase request
        PurchaseRequest mockRequest = new PurchaseRequest(requestId, userEmail);
        mockRequest.setType("credits");
        mockRequest.setStatus("PENDING");
        mockRequest.setQuantity(100);
        mockRequest.setCost(50.0);

        // Mock service behavior
        when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.of(mockRequest));
        when(purchaseRequestService.savePurchaseRequest(any(PurchaseRequest.class))).thenReturn(mockRequest);
        doNothing().when(emailService).sendConfirmationEmail(anyString());

        // Act & Assert
        mockMvc.perform(get("/api/purchase/signature-satori/accept")
                .param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.cost").value(50.0))
                .andExpect(jsonPath("$.type").value("credits"));

        // Verify service calls
        verify(purchaseRequestService).getPurchaseRequestById(requestId);
        verify(purchaseRequestService).savePurchaseRequest(any(PurchaseRequest.class));
        verify(emailService).sendConfirmationEmail(userEmail);
    }
}