package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.PurchaseController;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.cloudmen.backend.services.PurchaseEmailService;
import com.cloudmen.backend.services.PurchaseRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for PurchaseController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseController Tests")
public class PurchaseControllerIntegrationTest {

        private MockMvc mockMvc;

        @Mock
        private PurchaseEmailService emailService;

        @Mock
        private GoogleWorkspaceService googleWorkspaceService;

        @Mock
        private PurchaseRequestService purchaseRequestService;

        // Use a real ObjectMapper
        private final ObjectMapper objectMapper = new ObjectMapper();

        // Create controller directly
        private PurchaseController purchaseController;

        @BeforeEach
        void setUp() {
                // Create a new controller for each test
                purchaseController = new PurchaseController(
                                emailService,
                                googleWorkspaceService,
                                purchaseRequestService);

                // Create standalone MockMvc to avoid loading full application context
                mockMvc = MockMvcBuilders
                                .standaloneSetup(purchaseController)
                                .build();
        }

        @Test
        @DisplayName("GET /api/purchase/accept - Returns error when request not found")
        void acceptPurchase_ReturnsError_WhenRequestNotFound() throws Exception {
                // Arrange
                String requestId = "non-existent-id";
                when(purchaseRequestService.getPurchaseRequestById(requestId))
                                .thenReturn(Optional.empty());

                // Act
                MvcResult result = mockMvc.perform(get("/api/purchase/accept")
                                .param("requestId", requestId))
                                .andExpect(status().isOk())
                                .andReturn();

                // Assert
                String responseBody = result.getResponse().getContentAsString();
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                assertEquals("error", responseMap.get("status"));
                assertEquals("Request not found", responseMap.get("message"));

                // Verify no emails were sent
                verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("GET /api/purchase/accept - Success for generic purchase")
        void acceptPurchase_Success_ForGenericPurchase() throws Exception {
                // Arrange
                String requestId = "test-request-id";
                PurchaseRequest purchaseRequest = new PurchaseRequest();
                purchaseRequest.setId(requestId);
                purchaseRequest.setUserEmail("user@example.com");
                purchaseRequest.setType("other");
                purchaseRequest.setStatus("PENDING");

                when(purchaseRequestService.getPurchaseRequestById(requestId))
                                .thenReturn(Optional.of(purchaseRequest));
                when(purchaseRequestService.savePurchaseRequest(any()))
                                .thenReturn(purchaseRequest); // Return the same request for simplicity

                doNothing().when(emailService).sendConfirmationEmail(anyString());

                // Act
                MvcResult result = mockMvc.perform(get("/api/purchase/accept")
                                .param("requestId", requestId))
                                .andExpect(status().isOk())
                                .andReturn();

                // Assert
                String responseBody = result.getResponse().getContentAsString();
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                assertEquals("success", responseMap.get("status"));
                assertEquals(requestId, responseMap.get("requestId"));
                assertEquals("user@example.com", responseMap.get("email"));
                assertEquals("purchase", responseMap.get("type"));

                // Verify request was updated and email was sent
                verify(purchaseRequestService).savePurchaseRequest(argThat(req -> "APPROVED".equals(req.getStatus())));
                verify(emailService).sendConfirmationEmail("user@example.com");
        }

        @Test
        @DisplayName("GET /api/purchase/google-workspace/accept - Success")
        void acceptGoogleWorkspaceLicense_Success() throws Exception {
                // Arrange
                String requestId = "test-license-id";
                PurchaseRequest purchaseRequest = new PurchaseRequest();
                purchaseRequest.setId(requestId);
                purchaseRequest.setUserEmail("user@example.com");
                purchaseRequest.setType("licenses");
                purchaseRequest.setStatus("PENDING");
                purchaseRequest.setQuantity(5);
                purchaseRequest.setLicenseType("G Suite Business");
                purchaseRequest.setDomain("example.com");
                purchaseRequest.setCost(50.0);

                when(purchaseRequestService.getPurchaseRequestById(requestId))
                                .thenReturn(Optional.of(purchaseRequest));
                when(purchaseRequestService.savePurchaseRequest(any()))
                                .thenReturn(purchaseRequest); // Return the same request for simplicity

                doNothing().when(emailService).sendGoogleWorkspaceLicenseConfirmation(
                                anyString(), anyInt(), anyString(), anyString(), anyDouble());

                // Act
                MvcResult result = mockMvc.perform(get("/api/purchase/google-workspace/accept")
                                .param("requestId", requestId))
                                .andExpect(status().isOk())
                                .andReturn();

                // Assert
                String responseBody = result.getResponse().getContentAsString();
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

                assertEquals("success", responseMap.get("status"));
                assertEquals(requestId, responseMap.get("requestId"));
                assertEquals("user@example.com", responseMap.get("email"));
                assertEquals("G Suite Business", responseMap.get("licenseType"));
                assertEquals(5, responseMap.get("count"));
                assertEquals("example.com", responseMap.get("domain"));
                assertEquals("license", responseMap.get("type"));

                // Verify request was updated
                verify(purchaseRequestService).savePurchaseRequest(argThat(req -> "APPROVED".equals(req.getStatus())));

                // Verify email confirmation was sent
                verify(emailService).sendGoogleWorkspaceLicenseConfirmation(
                                "user@example.com", 5, "G Suite Business", "example.com", 50.0);
        }

        @Test
        @DisplayName("POST /api/purchase/google-workspace/request - Basic functionality test")
        void requestGoogleWorkspaceLicenses_BasicTest() throws Exception {
                // Arrange
                String userEmail = "user@example.com";
                String customerId = "test-customer-id";

                // Create subscription response
                GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
                subscription.setSkuName("G Suite Business");
                subscription.setStatus("ACTIVE");

                GoogleWorkspaceSubscriptionListResponseDTO subscriptionList = new GoogleWorkspaceSubscriptionListResponseDTO();
                subscriptionList.setSubscriptions(java.util.Collections.singletonList(subscription));

                // Mock service behavior - return an active subscription
                when(googleWorkspaceService.getCustomerSubscriptions(anyString()))
                                .thenReturn(Mono.just(subscriptionList));
                when(googleWorkspaceService.hasMatchingLicense(any(), anyString()))
                                .thenReturn(true);

                // Mock email service - just verify it's called
                doNothing().when(emailService).sendGoogleWorkspaceLicenseRequest(
                                anyString(), anyString(), anyInt(), anyString(), anyString(),
                                anyString(), anyString(), anyDouble(), anyString());

                // Mock purchase request service
                PurchaseRequest mockRequest = new PurchaseRequest();
                mockRequest.setId("test-id");
                mockRequest.setStatus("PENDING");
                when(purchaseRequestService.createGoogleWorkspaceLicenseRequest(anyString(), any()))
                                .thenReturn(mockRequest);

                // Create a simple request body
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("count", 5);
                requestBody.put("licenseType", "G Suite Business");
                requestBody.put("domain", "example.com");

                // Act
                MvcResult result = mockMvc.perform(post("/api/purchase/google-workspace/request")
                                .param("userEmail", userEmail)
                                .param("customerId", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestBody)))
                                .andExpect(status().is2xxSuccessful())
                                .andReturn();

                // Verify the purchase request was created
                verify(purchaseRequestService).createGoogleWorkspaceLicenseRequest(eq(userEmail), any());
        }
}