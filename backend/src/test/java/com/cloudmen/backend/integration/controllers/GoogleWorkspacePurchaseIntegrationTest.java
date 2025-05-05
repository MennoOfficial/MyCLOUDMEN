package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.PurchaseController;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.cloudmen.backend.services.PurchaseEmailService;
import com.cloudmen.backend.services.SignatureSatoriService;
import com.cloudmen.backend.services.UserService;
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
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Google Workspace license purchase functionality.
 */
@WebMvcTest(PurchaseController.class)
@ActiveProfiles("test")
@DisplayName("Google Workspace License Purchase Integration Tests")
public class GoogleWorkspacePurchaseIntegrationTest {

    @TestConfiguration
    static class GoogleWorkspacePurchaseTestConfig {
        @Bean
        public AuthenticationLogService authenticationLogService() {
            return mock(AuthenticationLogService.class);
        }

        @Bean
        public PurchaseController purchaseController(PurchaseEmailService emailService,
                GoogleWorkspaceService googleWorkspaceService) {
            PurchaseController controller = new PurchaseController(emailService, googleWorkspaceService);

            // Pre-populate the pendingGoogleWorkspaceRequests map with a test request
            try {
                // Create a test request
                GoogleWorkspaceLicenseRequestDTO request = new GoogleWorkspaceLicenseRequestDTO();
                request.setCount(5);
                request.setLicenseType("Business Standard");
                request.setDomain("example.com");

                // Get access to the private field
                Field pendingRequestsField = PurchaseController.class
                        .getDeclaredField("pendingGoogleWorkspaceRequests");
                pendingRequestsField.setAccessible(true);
                ConcurrentHashMap<String, GoogleWorkspaceLicenseRequestDTO> pendingRequests = (ConcurrentHashMap<String, GoogleWorkspaceLicenseRequestDTO>) pendingRequestsField
                        .get(controller);
                pendingRequests.put("gw-req-123", request);

                // Set the user email mapping
                Field requestUsersField = PurchaseController.class.getDeclaredField("requestUsers");
                requestUsersField.setAccessible(true);
                ConcurrentHashMap<String, String> requestUsers = (ConcurrentHashMap<String, String>) requestUsersField
                        .get(controller);
                requestUsers.put("gw-req-123", "user@example.com");
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

    @MockBean
    private GoogleWorkspaceService googleWorkspaceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/purchase/google-workspace/request should create request and return 201 when licenses exist")
    public void requestGoogleWorkspaceLicenses_shouldCreateRequestAndReturn201_whenLicensesExist() throws Exception {
        // Arrange
        GoogleWorkspaceLicenseRequestDTO request = new GoogleWorkspaceLicenseRequestDTO();
        request.setCount(10);
        request.setLicenseType("Business Standard");
        // Let domain be detected from email

        // Mock existing subscription response
        GoogleWorkspaceSubscriptionListResponseDTO subscriptionList = new GoogleWorkspaceSubscriptionListResponseDTO();
        List<GoogleWorkspaceSubscriptionDTO> subscriptions = new ArrayList<>();

        GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
        subscription.setSkuName("Business Standard");
        subscription.setStatus("ACTIVE");
        subscription.setTotalLicenses(5);
        subscriptions.add(subscription);

        subscriptionList.setSubscriptions(subscriptions);

        when(googleWorkspaceService.getCustomerSubscriptions(anyString()))
                .thenReturn(Mono.just(subscriptionList));

        doNothing().when(emailService).sendGoogleWorkspaceLicenseRequest(
                anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/purchase/google-workspace/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .param("userEmail", "user@example.com")
                .param("customerId", "cust-123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.licenseType").value("Business Standard"))
                .andExpect(jsonPath("$.domain").value("example.com")) // Extracted from email
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/purchase/google-workspace/request should return 400 when no licenses exist")
    public void requestGoogleWorkspaceLicenses_shouldReturn400_whenNoLicensesExist() throws Exception {
        // Arrange
        GoogleWorkspaceLicenseRequestDTO request = new GoogleWorkspaceLicenseRequestDTO();
        request.setCount(10);
        request.setLicenseType("Business Standard");

        // Mock empty subscription response
        GoogleWorkspaceSubscriptionListResponseDTO subscriptionList = new GoogleWorkspaceSubscriptionListResponseDTO();
        subscriptionList.setSubscriptions(new ArrayList<>());

        when(googleWorkspaceService.getCustomerSubscriptions(anyString()))
                .thenReturn(Mono.just(subscriptionList));

        // Act & Assert
        mockMvc.perform(post("/api/purchase/google-workspace/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .param("userEmail", "user@example.com")
                .param("customerId", "cust-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("must have at least one license")));
    }

    @Test
    @DisplayName("GET /api/purchase/google-workspace/accept should accept request and return 200")
    public void acceptGoogleWorkspaceLicense_shouldAcceptRequestAndReturn200() throws Exception {
        // Arrange
        String requestId = "gw-req-123";

        doNothing().when(emailService).sendGoogleWorkspaceLicenseConfirmation(
                anyString(), anyInt(), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(get("/api/purchase/google-workspace/accept")
                .param("requestId", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.licenseType").value("Business Standard"))
                .andExpect(jsonPath("$.domain").value("example.com"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    @DisplayName("GET /api/purchase/google-workspace/accept should return 404 when request doesn't exist")
    public void acceptGoogleWorkspaceLicense_shouldReturn404WhenRequestDoesntExist() throws Exception {
        // Arrange
        String requestId = "non-existent-request";

        // Act & Assert
        mockMvc.perform(get("/api/purchase/google-workspace/accept")
                .param("requestId", requestId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not found or already processed")))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }
}