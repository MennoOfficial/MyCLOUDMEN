package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriCreditsDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseRequestDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseResponseDTO;
import com.cloudmen.backend.services.SignatureSatoriService;
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

import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for SignatureSatoriController
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("SignatureSatoriController Integration Tests")
public class SignatureSatoriControllerIntegrationTest {

        @Autowired
        private WebTestClient webTestClient;

        @MockBean
        private SignatureSatoriService signatureSatoriService;

        @Test
        @DisplayName("GET /api/signaturesatori/customers/{customerId}/credits - Success")
        void getCustomerCredits_Success() {
                // Arrange
                String customerId = "test-customer-id";

                SignatureSatoriCreditsDTO credits = new SignatureSatoriCreditsDTO();
                credits.setCustomerId(customerId);
                credits.setCreditBalance(100);
                credits.setOwnerEmail("test@example.com");
                credits.setCreditDiscountPercent(10.0);
                credits.setDomains(Arrays.asList("example.com", "test.com"));

                when(signatureSatoriService.getCustomerCredits(eq(customerId)))
                                .thenReturn(Mono.just(credits));

                // Act & Assert
                webTestClient.get()
                                .uri("/api/signaturesatori/customers/{customerId}/credits", customerId)
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.customerId").isEqualTo(customerId)
                                .jsonPath("$.creditBalance").isEqualTo(100)
                                .jsonPath("$.ownerEmail").isEqualTo("test@example.com")
                                .jsonPath("$.creditDiscountPercent").isEqualTo(10.0)
                                .jsonPath("$.domains").isArray()
                                .jsonPath("$.domains[0]").isEqualTo("example.com")
                                .jsonPath("$.domains[1]").isEqualTo("test.com");
        }

        @Test
        @DisplayName("GET /api/signaturesatori/customers/{customerId}/credits - Not Found")
        void getCustomerCredits_NotFound() {
                // Arrange
                String customerId = "non-existent-customer";

                when(signatureSatoriService.getCustomerCredits(eq(customerId)))
                                .thenReturn(Mono.empty());

                // Act & Assert
                webTestClient.get()
                                .uri("/api/signaturesatori/customers/{customerId}/credits", customerId)
                                .accept(MediaType.APPLICATION_JSON)
                                .exchange()
                                .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("POST /api/signaturesatori/customers/{customerId}/credits - Success")
        void purchaseCredits_Success() {
                // Arrange
                String customerId = "test-customer-id";

                SignatureSatoriPurchaseRequestDTO request = new SignatureSatoriPurchaseRequestDTO();
                request.setCount(100);

                SignatureSatoriPurchaseResponseDTO response = new SignatureSatoriPurchaseResponseDTO();
                response.setType("credit_purchase");
                response.setCount(100);
                response.setMessage("Credits purchased successfully");
                response.setDate(ZonedDateTime.parse("2023-04-15T10:30:00Z"));

                when(signatureSatoriService.purchaseCredits(eq(customerId),
                                any(SignatureSatoriPurchaseRequestDTO.class)))
                                .thenReturn(Mono.just(response));

                // Act & Assert
                webTestClient.post()
                                .uri("/api/signaturesatori/customers/{customerId}/credits", customerId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody()
                                .jsonPath("$.type").isEqualTo("credit_purchase")
                                .jsonPath("$.count").isEqualTo(100)
                                .jsonPath("$.message").isEqualTo("Credits purchased successfully")
                                .jsonPath("$.date").isEqualTo("2023-04-15T10:30:00Z");
        }
}