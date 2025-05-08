package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.PurchaseController;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Google Workspace license purchase functionality
 * in PurchaseController
 */
@WebMvcTest(PurchaseController.class)
@ActiveProfiles("test")
@DisplayName("Google Workspace Purchase Integration Tests")
public class GoogleWorkspacePurchaseIntegrationTest {

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
        @DisplayName("POST /api/purchase/google-workspace/request should create request when valid")
        public void requestGoogleWorkspaceLicenses_shouldCreateRequestWhenValid() throws Exception {
                // Arrange
                String customerId = "cust-123";
                String userEmail = "user@example.com";
                String requestId = "license-req-123";

                GoogleWorkspaceLicenseRequestDTO request = new GoogleWorkspaceLicenseRequestDTO();
                request.setCount(5);
                request.setLicenseType("Google Workspace Business Standard");
                request.setDomain("example.com");
                request.setCost(70.0); // $14 per license * 5 licenses

                // Mock purchase request creation
                PurchaseRequest mockPurchaseRequest = new PurchaseRequest(requestId, userEmail);
                mockPurchaseRequest.setType("licenses");
                mockPurchaseRequest.setLicenseType(request.getLicenseType());
                mockPurchaseRequest.setQuantity(request.getCount());
                mockPurchaseRequest.setDomain(request.getDomain());
                mockPurchaseRequest.setCost(request.getCost());
                mockPurchaseRequest.setStatus("PENDING");
                mockPurchaseRequest.setRequestDate(new Date());

                // Mock service response for checking existing licenses
                GoogleWorkspaceSubscriptionListResponseDTO subscriptions = new GoogleWorkspaceSubscriptionListResponseDTO();
                GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
                subscription.setSkuName("Google Workspace Business Standard");
                subscription.setStatus("ACTIVE");
                subscription.setTotalLicenses(10);
                subscriptions.setSubscriptions(Arrays.asList(subscription));

                when(googleWorkspaceService.getCustomerSubscriptions(eq(customerId)))
                                .thenReturn(Mono.just(subscriptions));

                when(googleWorkspaceService.hasMatchingLicense(any(), eq(request.getLicenseType())))
                                .thenReturn(true);

                when(purchaseRequestService.createGoogleWorkspaceLicenseRequest(eq(userEmail),
                                any(GoogleWorkspaceLicenseRequestDTO.class)))
                                .thenReturn(mockPurchaseRequest);

                doNothing().when(emailService).sendGoogleWorkspaceLicenseRequest(
                                anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(),
                                anyDouble(), anyString());

                // Act & Assert
                mockMvc.perform(post("/api/purchase/google-workspace/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .param("userEmail", userEmail)
                                .param("customerId", customerId)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.count").value(5))
                                .andExpect(jsonPath("$.licenseType").value("Google Workspace Business Standard"))
                                .andExpect(jsonPath("$.domain").value("example.com"))
                                .andExpect(jsonPath("$.requestId").value(requestId));

                // Verify service calls
                verify(googleWorkspaceService).getCustomerSubscriptions(customerId);
                verify(googleWorkspaceService).hasMatchingLicense(any(), eq(request.getLicenseType()));
                verify(purchaseRequestService).createGoogleWorkspaceLicenseRequest(eq(userEmail),
                                any(GoogleWorkspaceLicenseRequestDTO.class));
                verify(emailService).sendGoogleWorkspaceLicenseRequest(
                                eq(userEmail), eq(requestId), eq(5), eq(request.getLicenseType()),
                                eq(request.getDomain()), eq(userEmail), eq(customerId),
                                eq(request.getCost()), anyString());
        }

        @Test
        @DisplayName("POST /api/purchase/google-workspace/request should reject when no existing license")
        public void requestGoogleWorkspaceLicenses_shouldRejectWhenNoExistingLicense() throws Exception {
                // Arrange
                String customerId = "cust-123";
                String userEmail = "user@example.com";
                GoogleWorkspaceLicenseRequestDTO request = new GoogleWorkspaceLicenseRequestDTO();
                request.setCount(5);
                request.setLicenseType("Google Workspace Business Plus");
                request.setDomain("example.com");

                // Mock service response with no matching license
                GoogleWorkspaceSubscriptionListResponseDTO subscriptions = new GoogleWorkspaceSubscriptionListResponseDTO();
                GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
                subscription.setSkuName("Google Workspace Business Standard"); // Different type
                subscription.setStatus("ACTIVE");
                subscription.setTotalLicenses(10);
                subscriptions.setSubscriptions(Arrays.asList(subscription));

                when(googleWorkspaceService.getCustomerSubscriptions(eq(customerId)))
                                .thenReturn(Mono.just(subscriptions));

                when(googleWorkspaceService.hasMatchingLicense(any(), eq(request.getLicenseType())))
                                .thenReturn(false); // No matching license

                // Act & Assert
                mockMvc.perform(post("/api/purchase/google-workspace/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .param("userEmail", userEmail)
                                .param("customerId", customerId)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.status").value("REJECTED"))
                                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                                                "Company must have at least one license of type Google Workspace Business Plus")));

                // Verify service calls
                verify(googleWorkspaceService).getCustomerSubscriptions(customerId);
                verify(googleWorkspaceService).hasMatchingLicense(any(), eq(request.getLicenseType()));
                // Should not attempt to create a purchase request
                verify(purchaseRequestService, never()).createGoogleWorkspaceLicenseRequest(anyString(), any());
        }

        @Test
        @DisplayName("GET /api/purchase/google-workspace/accept should accept when request exists")
        public void acceptGoogleWorkspaceLicenseRequest_shouldAcceptWhenRequestExists() throws Exception {
                // Arrange
                String requestId = "license-req-456";
                String userEmail = "user@example.com";

                // Create a mock license purchase request
                PurchaseRequest mockRequest = new PurchaseRequest(requestId, userEmail);
                mockRequest.setType("licenses");
                mockRequest.setStatus("PENDING");
                mockRequest.setQuantity(5);
                mockRequest.setLicenseType("Google Workspace Business Standard");
                mockRequest.setDomain("example.com");
                mockRequest.setCost(70.0);

                // Mock service behavior
                when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.of(mockRequest));
                when(purchaseRequestService.savePurchaseRequest(any(PurchaseRequest.class))).thenReturn(mockRequest);
                doNothing().when(emailService).sendGoogleWorkspaceLicenseConfirmation(
                                anyString(), anyInt(), anyString(), anyString(), anyDouble());

                // Act & Assert
                mockMvc.perform(get("/api/purchase/google-workspace/accept")
                                .param("requestId", requestId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("success"))
                                .andExpect(jsonPath("$.requestId").value(requestId))
                                .andExpect(jsonPath("$.email").value(userEmail))
                                .andExpect(jsonPath("$.licenseType").value("Google Workspace Business Standard"))
                                .andExpect(jsonPath("$.count").value(5))
                                .andExpect(jsonPath("$.domain").value("example.com"))
                                .andExpect(jsonPath("$.type").value("license"));

                // Verify service calls
                verify(purchaseRequestService).getPurchaseRequestById(requestId);
                verify(purchaseRequestService).savePurchaseRequest(any(PurchaseRequest.class));
                verify(emailService).sendGoogleWorkspaceLicenseConfirmation(
                                eq(userEmail), eq(5), eq("Google Workspace Business Standard"),
                                eq("example.com"), eq(70.0));
        }

        @Test
        @DisplayName("GET /api/purchase/google-workspace/accept should return error when request doesn't exist")
        public void acceptGoogleWorkspaceLicenseRequest_shouldReturnErrorWhenRequestDoesntExist() throws Exception {
                // Arrange
                String requestId = "non-existent-request";

                // Mock service behavior
                when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.empty());

                // Act & Assert
                mockMvc.perform(get("/api/purchase/google-workspace/accept")
                                .param("requestId", requestId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("error"))
                                .andExpect(jsonPath("$.message").value("License request not found"))
                                .andExpect(jsonPath("$.requestId").value(requestId));

                // Verify service call
                verify(purchaseRequestService).getPurchaseRequestById(requestId);
                verify(emailService, never()).sendGoogleWorkspaceLicenseConfirmation(
                                anyString(), anyInt(), anyString(), anyString(), anyDouble());
        }
}