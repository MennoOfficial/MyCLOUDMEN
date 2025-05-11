package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceCreateSubscriptionRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GoogleWorkspaceService focusing on business logic
 */
@DisplayName("Google Workspace Service Tests")
@ExtendWith(MockitoExtension.class)
class GoogleWorkspaceServiceTest {

    /**
     * Test DTO property validation
     */
    @Test
    @DisplayName("Subscription DTO should have correct values")
    void subscriptionDTO_ShouldHaveCorrectValues() {
        // Create test DTO
        GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
        subscription.setSkuId("1");
        subscription.setSkuName("Google Workspace Business Standard");
        subscription.setStatus("ACTIVE");
        subscription.setTotalLicenses(5);

        // Verify DTO properties
        assertEquals("1", subscription.getSkuId());
        assertEquals("Google Workspace Business Standard", subscription.getSkuName());
        assertEquals("ACTIVE", subscription.getStatus());
        assertEquals(5, subscription.getTotalLicenses());
    }

    /**
     * Test subscription list DTO
     */
    @Test
    @DisplayName("Subscription list DTO should contain correct subscriptions")
    void subscriptionListDTO_ShouldContainCorrectSubscriptions() {
        // Create test DTOs
        GoogleWorkspaceSubscriptionDTO sub1 = new GoogleWorkspaceSubscriptionDTO();
        sub1.setSkuId("1");
        sub1.setSkuName("Google Workspace Business Standard");
        sub1.setStatus("ACTIVE");
        sub1.setTotalLicenses(5);

        GoogleWorkspaceSubscriptionDTO sub2 = new GoogleWorkspaceSubscriptionDTO();
        sub2.setSkuId("2");
        sub2.setSkuName("Google Workspace Business Plus");
        sub2.setStatus("ACTIVE");
        sub2.setTotalLicenses(2);

        // Create subscription list DTO
        GoogleWorkspaceSubscriptionListResponseDTO response = new GoogleWorkspaceSubscriptionListResponseDTO();
        response.setSubscriptions(Arrays.asList(sub1, sub2));

        // Verify list properties
        assertNotNull(response.getSubscriptions());
        assertEquals(2, response.getSubscriptions().size());
        assertEquals("Google Workspace Business Standard", response.getSubscriptions().get(0).getSkuName());
        assertEquals("Google Workspace Business Plus", response.getSubscriptions().get(1).getSkuName());
    }

    /**
     * Test ReactiveStreams with StepVerifier for subscription list
     */
    @Test
    @DisplayName("Reactive Mono should handle subscription list correctly")
    void reactiveStream_ShouldHandleSubscriptionList() {
        // Create test DTOs
        GoogleWorkspaceSubscriptionDTO sub1 = new GoogleWorkspaceSubscriptionDTO();
        sub1.setSkuId("1");
        sub1.setSkuName("Google Workspace Business Standard");

        // Create subscription list
        GoogleWorkspaceSubscriptionListResponseDTO response = new GoogleWorkspaceSubscriptionListResponseDTO();
        response.setSubscriptions(Collections.singletonList(sub1));

        // Create Mono
        Mono<GoogleWorkspaceSubscriptionListResponseDTO> result = Mono.just(response);

        // Verify with StepVerifier
        StepVerifier.create(result)
                .assertNext(r -> {
                    assertNotNull(r.getSubscriptions());
                    assertEquals(1, r.getSubscriptions().size());
                    assertEquals("1", r.getSubscriptions().get(0).getSkuId());
                    assertEquals("Google Workspace Business Standard", r.getSubscriptions().get(0).getSkuName());
                })
                .verifyComplete();
    }

    /**
     * Test ReactiveStreams error handling
     */
    @Test
    @DisplayName("Reactive Mono should handle errors")
    void reactiveStream_ShouldHandleErrors() {
        // Create error Mono
        RuntimeException error = new RuntimeException("API Error");
        Mono<GoogleWorkspaceSubscriptionListResponseDTO> result = Mono.error(error);

        // Verify with StepVerifier
        StepVerifier.create(result)
                .expectErrorMatches(e -> e.getMessage().contains("API Error"))
                .verify();
    }

    /**
     * Test CreateSubscriptionRequestDTO
     */
    @Test
    @DisplayName("Create subscription request DTO should have correct values")
    void createSubscriptionRequestDTO_ShouldHaveCorrectValues() {
        // Create test DTO
        GoogleWorkspaceCreateSubscriptionRequestDTO request = new GoogleWorkspaceCreateSubscriptionRequestDTO();
        request.setSkuId("1");
        request.setPlanType("ANNUAL");
        request.setNumberOfLicenses(5);

        // Verify properties
        assertEquals("1", request.getSkuId());
        assertEquals("ANNUAL", request.getPlanType());
        assertEquals(5, request.getNumberOfLicenses());
    }

    /**
     * Simple test for license matching logic without mocks
     */
    @Test
    @DisplayName("License matching should work correctly")
    void licenseMatching_ShouldWorkCorrectly() {
        // Create a subclass for testing
        GoogleWorkspaceService service = new GoogleWorkspaceService(null, null) {
            // Override the methods that would normally use WebClient
            @Override
            public void init() {
                // Do nothing - we're not testing WebClient
            }
        };

        // Add active subscription with licenses > 0
        GoogleWorkspaceSubscriptionDTO activeSubscription = new GoogleWorkspaceSubscriptionDTO();
        activeSubscription.setSkuName("Google Workspace Business Standard");
        activeSubscription.setStatus("ACTIVE");
        activeSubscription.setTotalLicenses(5);

        // Add inactive subscription
        GoogleWorkspaceSubscriptionDTO inactiveSubscription = new GoogleWorkspaceSubscriptionDTO();
        inactiveSubscription.setSkuName("Google Workspace Enterprise");
        inactiveSubscription.setStatus("SUSPENDED");
        inactiveSubscription.setTotalLicenses(3);

        // Add subscription with no licenses
        GoogleWorkspaceSubscriptionDTO zeroLicensesSubscription = new GoogleWorkspaceSubscriptionDTO();
        zeroLicensesSubscription.setSkuName("Google Workspace Enterprise Plus");
        zeroLicensesSubscription.setStatus("ACTIVE");
        zeroLicensesSubscription.setTotalLicenses(0);

        // Create DTO with all subscriptions
        GoogleWorkspaceSubscriptionListResponseDTO subscriptions = new GoogleWorkspaceSubscriptionListResponseDTO();
        subscriptions.setSubscriptions(Arrays.asList(
                activeSubscription, inactiveSubscription, zeroLicensesSubscription));

        // Test matching
        assertTrue(service.hasMatchingLicense(subscriptions, "Google Workspace Business Standard"));
        assertTrue(service.hasMatchingLicense(subscriptions, "Business Standard")); // Normalized

        // Test non-matching
        assertFalse(service.hasMatchingLicense(subscriptions, "Google Workspace Enterprise")); // Inactive
        assertFalse(service.hasMatchingLicense(subscriptions, "Google Workspace Enterprise Plus")); // Zero licenses
        assertFalse(service.hasMatchingLicense(subscriptions, "Google Workspace Essentials")); // Non-existent
    }
}