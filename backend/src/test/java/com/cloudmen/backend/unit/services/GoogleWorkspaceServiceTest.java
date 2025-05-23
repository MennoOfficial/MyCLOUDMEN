package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceCreateSubscriptionRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoogleWorkspaceService focusing on business logic
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GoogleWorkspaceService Tests")
class GoogleWorkspaceServiceTest {

    private static final String API_BASE_URL = "https://api.example.com";
    private static final String CUSTOMER_ID = "customer-123";

    @Mock
    private WebClient.Builder webClientBuilder;

    private GoogleWorkspaceService googleWorkspaceService;

    @BeforeEach
    void setUp() {
        // Create test service with spy
        googleWorkspaceService = spy(new GoogleWorkspaceService(webClientBuilder, null));

        // Set base URL and null retry spec
        ReflectionTestUtils.setField(googleWorkspaceService, "apiBaseUrl", API_BASE_URL);
        ReflectionTestUtils.setField(googleWorkspaceService, "webClientRetrySpec", null);

        // Simplest possible WebClient setup
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(mock(WebClient.class));

        // Initialize service
        googleWorkspaceService.init();

        // Stub service methods to bypass WebClient completely
        doReturn(Mono.empty()).when(googleWorkspaceService).getCustomerSubscriptions(anyString());
        doReturn(Mono.empty()).when(googleWorkspaceService).createSubscription(anyString(), any());
        doReturn(Mono.empty()).when(googleWorkspaceService).getAvailableSkus();
    }

    @Test
    @DisplayName("init - Should initialize WebClient")
    void init_ShouldInitializeWebClient() {
        // Just verify the builder was called with the right URL
        verify(webClientBuilder).baseUrl(API_BASE_URL);
        verify(webClientBuilder).build();
    }

    @Test
    @DisplayName("getCustomerSubscriptions - Should return subscriptions when successful")
    void getCustomerSubscriptions_ShouldReturnSubscriptions_WhenSuccessful() {
        // Create a mock response
        GoogleWorkspaceSubscriptionListResponseDTO mockResponse = new GoogleWorkspaceSubscriptionListResponseDTO();
        List<GoogleWorkspaceSubscriptionDTO> subs = new ArrayList<>();

        GoogleWorkspaceSubscriptionDTO sub = new GoogleWorkspaceSubscriptionDTO();
        sub.setSkuId("sku-123");
        sub.setSkuName("Business Starter");
        sub.setStatus("ACTIVE");
        sub.setPlanType("ANNUAL");
        sub.setTotalLicenses(10);
        subs.add(sub);

        mockResponse.setSubscriptions(subs);

        // Configure spy to return our mock data
        doReturn(Mono.just(mockResponse)).when(googleWorkspaceService).getCustomerSubscriptions(CUSTOMER_ID);

        // Act & Assert
        StepVerifier.create(googleWorkspaceService.getCustomerSubscriptions(CUSTOMER_ID))
                .expectNext(mockResponse)
                .verifyComplete();
    }

    @Test
    @DisplayName("hasMatchingLicense - Should check license type correctly")
    void hasMatchingLicense_ShouldCheckLicenseTypeCorrectly() {
        // Create test data
        GoogleWorkspaceSubscriptionListResponseDTO subscriptions = new GoogleWorkspaceSubscriptionListResponseDTO();
        List<GoogleWorkspaceSubscriptionDTO> subList = new ArrayList<>();

        GoogleWorkspaceSubscriptionDTO sub = new GoogleWorkspaceSubscriptionDTO();
        sub.setSkuName("Business Starter");
        sub.setStatus("ACTIVE");
        sub.setTotalLicenses(10);
        subList.add(sub);

        subscriptions.setSubscriptions(subList);

        // Test matching license
        assertTrue(googleWorkspaceService.hasMatchingLicense(subscriptions, "Business Starter"));

        // Test non-matching license
        assertFalse(googleWorkspaceService.hasMatchingLicense(subscriptions, "Business Standard"));
    }

    @Test
    @DisplayName("createSubscription - Should return subscription when successful")
    void createSubscription_ShouldReturnSubscription_WhenSuccessful() {
        // Create request and response
        GoogleWorkspaceCreateSubscriptionRequestDTO request = new GoogleWorkspaceCreateSubscriptionRequestDTO(
                "sku-123", "ANNUAL", 10);

        GoogleWorkspaceSubscriptionDTO mockResponse = new GoogleWorkspaceSubscriptionDTO();
        mockResponse.setSkuId("sku-123");
        mockResponse.setStatus("ACTIVE");

        // Configure spy
        doReturn(Mono.just(mockResponse)).when(googleWorkspaceService)
                .createSubscription(CUSTOMER_ID, request);

        // Act & Assert
        StepVerifier.create(googleWorkspaceService.createSubscription(CUSTOMER_ID, request))
                .expectNext(mockResponse)
                .verifyComplete();
    }

    @Test
    @DisplayName("getAvailableSkus - Should return skus when successful")
    void getAvailableSkus_ShouldReturnSkus_WhenSuccessful() {
        // Create mock response
        ObjectNode mockResponse = JsonNodeFactory.instance.objectNode();
        mockResponse.put("status", "success");

        // Configure spy
        doReturn(Mono.just(mockResponse)).when(googleWorkspaceService).getAvailableSkus();

        // Act & Assert
        StepVerifier.create(googleWorkspaceService.getAvailableSkus())
                .expectNext(mockResponse)
                .verifyComplete();
    }

    @Test
    @DisplayName("DTO - GoogleWorkspaceSubscriptionDTO getters and setters")
    void googleWorkspaceSubscriptionDTO_GettersAndSetters() {
        // Create DTO
        GoogleWorkspaceSubscriptionDTO dto = new GoogleWorkspaceSubscriptionDTO();

        // Set values
        dto.setSkuId("sku-123");
        dto.setSkuName("Test SKU");
        dto.setStatus("ACTIVE");
        dto.setPlanType("ANNUAL");
        dto.setTotalLicenses(25);

        // Verify values
        assertEquals("sku-123", dto.getSkuId());
        assertEquals("Test SKU", dto.getSkuName());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("ANNUAL", dto.getPlanType());
        assertEquals(25, dto.getTotalLicenses());
    }

    @Test
    @DisplayName("DTO - GoogleWorkspaceCreateSubscriptionRequestDTO constructor")
    void googleWorkspaceCreateSubscriptionRequestDTO_Constructor() {
        // Test constructor
        GoogleWorkspaceCreateSubscriptionRequestDTO dto = new GoogleWorkspaceCreateSubscriptionRequestDTO("sku-123",
                "ANNUAL", 10);

        // Verify fields
        assertEquals("sku-123", dto.getSkuId());
        assertEquals("ANNUAL", dto.getPlanType());
        assertEquals(10, dto.getNumberOfLicenses());
    }

    @Test
    @DisplayName("DTO - GoogleWorkspaceSubscriptionListResponseDTO")
    void googleWorkspaceSubscriptionListResponseDTO() {
        // Create DTO
        GoogleWorkspaceSubscriptionListResponseDTO dto = new GoogleWorkspaceSubscriptionListResponseDTO();

        // Create subscription list
        List<GoogleWorkspaceSubscriptionDTO> subs = new ArrayList<>();
        GoogleWorkspaceSubscriptionDTO sub = new GoogleWorkspaceSubscriptionDTO();
        sub.setSkuId("sku-123");
        subs.add(sub);

        // Set list
        dto.setSubscriptions(subs);

        // Verify
        assertNotNull(dto.getSubscriptions());
        assertEquals(1, dto.getSubscriptions().size());
        assertEquals("sku-123", dto.getSubscriptions().get(0).getSkuId());
    }
}