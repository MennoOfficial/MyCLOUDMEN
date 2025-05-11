package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceCreateSubscriptionRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for GoogleWorkspaceController
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("GoogleWorkspaceController Integration Tests")
public class GoogleWorkspaceControllerIntegrationTest {

        @Autowired
        private WebTestClient webTestClient;

        @MockBean
        private GoogleWorkspaceService googleWorkspaceService;

        @Test
        @DisplayName("GET /api/google-workspace/customers/{customerId}/licenses - Success")
        void getCustomerLicenses_Success() {
                // Arrange
                String customerId = "test-customer-id";

                GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
                subscription.setSkuId("1");
                subscription.setSkuName("Google Workspace Business Standard");
                subscription.setStatus("ACTIVE");
                subscription.setTotalLicenses(5);

                GoogleWorkspaceSubscriptionListResponseDTO response = new GoogleWorkspaceSubscriptionListResponseDTO();
                response.setSubscriptions(Collections.singletonList(subscription));

                when(googleWorkspaceService.getCustomerSubscriptions(eq(customerId)))
                                .thenReturn(Mono.just(response));

                // Act & Assert
                webTestClient.get()
                                .uri("/api/google-workspace/customers/{customerId}/licenses", customerId)
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.subscriptions").isArray()
                                .jsonPath("$.subscriptions[0].skuId").isEqualTo("1")
                                .jsonPath("$.subscriptions[0].skuName").isEqualTo("Google Workspace Business Standard")
                                .jsonPath("$.subscriptions[0].status").isEqualTo("ACTIVE")
                                .jsonPath("$.subscriptions[0].totalLicenses").isEqualTo(5);
        }

        @Test
        @DisplayName("GET /api/google-workspace/customers/{customerId}/licenses - Not Found")
        void getCustomerLicenses_NotFound() {
                // Arrange
                String customerId = "non-existent-customer";

                when(googleWorkspaceService.getCustomerSubscriptions(eq(customerId)))
                                .thenReturn(Mono.empty());

                // Act & Assert
                webTestClient.get()
                                .uri("/api/google-workspace/customers/{customerId}/licenses", customerId)
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange()
                                .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("POST /api/google-workspace/customers/{customerId}/licenses - Success")
        void addLicenses_Success() {
                // Arrange
                String customerId = "test-customer-id";

                GoogleWorkspaceCreateSubscriptionRequestDTO request = new GoogleWorkspaceCreateSubscriptionRequestDTO();
                request.setSkuId("1");
                request.setPlanType("ANNUAL");
                request.setNumberOfLicenses(5);

                GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
                subscription.setSkuId("1");
                subscription.setSkuName("Google Workspace Business Standard");
                subscription.setStatus("ACTIVE");
                subscription.setTotalLicenses(5);

                when(googleWorkspaceService.createSubscription(eq(customerId),
                                any(GoogleWorkspaceCreateSubscriptionRequestDTO.class)))
                                .thenReturn(Mono.just(subscription));

                // Act & Assert
                webTestClient.post()
                                .uri("/api/google-workspace/customers/{customerId}/licenses", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.skuId").isEqualTo("1")
                                .jsonPath("$.skuName").isEqualTo("Google Workspace Business Standard")
                                .jsonPath("$.status").isEqualTo("ACTIVE")
                                .jsonPath("$.totalLicenses").isEqualTo(5);
        }
}