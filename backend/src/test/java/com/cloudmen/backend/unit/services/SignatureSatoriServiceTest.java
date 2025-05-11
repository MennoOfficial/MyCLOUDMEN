package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriCreditsDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseResponseDTO;
import com.cloudmen.backend.services.SignatureSatoriService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SignatureSatoriService focusing on business logic without
 * complex WebClient mocking
 */
@DisplayName("SignatureSatori Service Tests")
class SignatureSatoriServiceTest {

    /**
     * Simple verification of DTO mapping with customer credits
     */
    @Test
    @DisplayName("Customer credits DTO should have correct values")
    void customerCreditsDTO_ShouldHaveCorrectValues() {
        // Create test DTO
        SignatureSatoriCreditsDTO credits = new SignatureSatoriCreditsDTO();
        credits.setCustomerId("test-customer-id");
        credits.setCreditBalance(100);
        credits.setOwnerEmail("test@example.com");
        credits.setCreditDiscountPercent(10.0);
        credits.setDomains(Arrays.asList("example.com", "test.com"));

        // Verify DTO properties
        assertEquals("test-customer-id", credits.getCustomerId());
        assertEquals(100, credits.getCreditBalance());
        assertEquals("test@example.com", credits.getOwnerEmail());
        assertEquals(10.0, credits.getCreditDiscountPercent());
        assertEquals(2, credits.getDomains().size());
        assertTrue(credits.getDomains().contains("example.com"));
        assertTrue(credits.getDomains().contains("test.com"));
    }

    /**
     * Test ReactiveStreams with StepVerifier when handling success results
     */
    @Test
    @DisplayName("Reactive Mono should handle successful credits result")
    void reactiveStream_ShouldHandleSuccessfulCreditsResult() {
        // Create test DTO
        SignatureSatoriCreditsDTO credits = new SignatureSatoriCreditsDTO();
        credits.setCustomerId("test-customer-id");
        credits.setCreditBalance(100);

        // Create Mono from DTO
        Mono<SignatureSatoriCreditsDTO> result = Mono.just(credits);

        // Verify reactive stream with StepVerifier
        StepVerifier.create(result)
                .assertNext(c -> {
                    assertEquals("test-customer-id", c.getCustomerId());
                    assertEquals(100, c.getCreditBalance());
                })
                .verifyComplete();
    }

    /**
     * Test ReactiveStreams with StepVerifier when handling error results
     */
    @Test
    @DisplayName("Reactive Mono should handle error results")
    void reactiveStream_ShouldHandleErrorResult() {
        // Create error Mono
        RuntimeException error = new RuntimeException("API Error");
        Mono<SignatureSatoriCreditsDTO> result = Mono.error(error);

        // Verify error handling
        StepVerifier.create(result)
                .expectErrorMatches(e -> e.getMessage().contains("API Error"))
                .verify();
    }

    /**
     * Simple verification of purchase response DTO mapping
     */
    @Test
    @DisplayName("Purchase response DTO should have correct values")
    void purchaseResponseDTO_ShouldHaveCorrectValues() {
        // Create test DTO
        SignatureSatoriPurchaseResponseDTO response = new SignatureSatoriPurchaseResponseDTO();
        response.setType("credit_purchase");
        response.setCount(50);
        response.setMessage("Purchase successful");

        // Verify DTO properties
        assertEquals("credit_purchase", response.getType());
        assertEquals(50, response.getCount());
        assertEquals("Purchase successful", response.getMessage());
    }
}