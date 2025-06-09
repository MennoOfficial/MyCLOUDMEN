package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceCreateSubscriptionRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with the Google Workspace Reseller API.
 */
@Service
public class GoogleWorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleWorkspaceService.class);
    private WebClient webClient;
    private final WebClient.Builder webClientBuilder;
    private final Retry webClientRetrySpec;

    @Value("${google.workspace.api.baseUrl:${GOOGLE_WORKSPACE_API_URL:https://mycloudmen.mennoplochaet.be/google-workspace-api}}")
    private String apiBaseUrl;

    public GoogleWorkspaceService(WebClient.Builder webClientBuilder,
            @Qualifier("webClientRetrySpec") Retry webClientRetrySpec) {
        this.webClientBuilder = webClientBuilder;
        this.webClientRetrySpec = webClientRetrySpec;
    }

    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder
                .baseUrl(apiBaseUrl)
                .build();
        logger.info("GoogleWorkspaceService initialized with baseUrl: {}", apiBaseUrl);
    }

    /**
     * Get all subscriptions for a customer.
     *
     * @param customerId The Google customer ID
     * @return DTO containing the list of subscriptions
     */
    public Mono<GoogleWorkspaceSubscriptionListResponseDTO> getCustomerSubscriptions(String customerId) {
        logger.info("Fetching subscriptions for customer: {}", customerId);

        return webClient.get()
                .uri("/apps/reseller/v1/subscriptions?customerId={customerId}", customerId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::convertToSimplifiedSubscriptionList)
                .retryWhen(webClientRetrySpec)
                .doOnSuccess(response -> logger.info("Successfully retrieved {} subscriptions for customer {}",
                        response.getSubscriptions() != null ? response.getSubscriptions().size() : 0, customerId))
                .doOnError(e -> logger.error("Error fetching subscriptions for customer {}: {}", customerId,
                        e.getMessage()));
    }

    /**
     * Create a new subscription for a customer.
     *
     * @param customerId The Google customer ID
     * @param request    The subscription creation request
     * @return The newly created subscription
     */
    public Mono<GoogleWorkspaceSubscriptionDTO> createSubscription(String customerId,
            GoogleWorkspaceCreateSubscriptionRequestDTO request) {
        logger.info("Creating subscription for customer: {}, SKU: {}", customerId, request.getSkuId());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("skuId", request.getSkuId());

        Map<String, Object> plan = new HashMap<>();
        plan.put("planName", request.getPlanType());
        requestBody.put("plan", plan);

        Map<String, Object> seats = new HashMap<>();
        seats.put("numberOfSeats", request.getNumberOfLicenses());
        seats.put("licensedNumberOfSeats", request.getNumberOfLicenses());
        requestBody.put("seats", seats);

        return webClient.post()
                .uri("/apps/reseller/v1/customers/{customerId}/subscriptions", customerId)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::convertToSimplifiedSubscription)
                .retryWhen(webClientRetrySpec)
                .doOnSuccess(
                        subscription -> logger.info("Successfully created subscription for SKU {} with {} licenses",
                                subscription.getSkuId(), subscription.getTotalLicenses()))
                .doOnError(e -> logger.error("Error creating subscription for customer {}: {}", customerId,
                        e.getMessage()));
    }

    /**
     * Get available SKUs.
     *
     * @return List of available SKUs
     */
    public Mono<Object> getAvailableSkus() {
        logger.info("Fetching available SKUs");

        return webClient.get()
                .uri("/apps/reseller/v1/skus")
                .retrieve()
                .bodyToMono(Object.class)
                .retryWhen(webClientRetrySpec)
                .doOnSuccess(response -> logger.info("Successfully retrieved available SKUs"))
                .doOnError(e -> logger.error("Error fetching available SKUs: {}", e.getMessage()));
    }

    /**
     * Converts the Google API response to our simplified subscription list DTO
     */
    public GoogleWorkspaceSubscriptionListResponseDTO convertToSimplifiedSubscriptionList(
            JsonNode jsonNode) {
        GoogleWorkspaceSubscriptionListResponseDTO response = new GoogleWorkspaceSubscriptionListResponseDTO();
        List<GoogleWorkspaceSubscriptionDTO> subscriptions = new ArrayList<>();

        if (jsonNode.has("subscriptions") && jsonNode.get("subscriptions").isArray()) {
            for (JsonNode subNode : jsonNode.get("subscriptions")) {
                subscriptions.add(convertToSimplifiedSubscription(subNode));
            }
        }

        response.setSubscriptions(subscriptions);
        return response;
    }

    /**
     * Converts a single subscription from the API response to our simplified DTO
     */
    public GoogleWorkspaceSubscriptionDTO convertToSimplifiedSubscription(JsonNode jsonNode) {
        if (jsonNode == null) {
            return null;
        }

        GoogleWorkspaceSubscriptionDTO dto = new GoogleWorkspaceSubscriptionDTO();

        if (jsonNode.has("skuId")) {
            dto.setSkuId(jsonNode.get("skuId").asText());
        }

        if (jsonNode.has("skuName")) {
            dto.setSkuName(jsonNode.get("skuName").asText());
        }

        if (jsonNode.has("status")) {
            dto.setStatus(jsonNode.get("status").asText());
        }

        // Extract plan type
        if (jsonNode.has("plan") && jsonNode.get("plan").has("planName")) {
            dto.setPlanType(jsonNode.get("plan").get("planName").asText());
        }

        // Extract license counts
        if (jsonNode.has("seats")) {
            JsonNode seats = jsonNode.get("seats");
            if (seats.has("numberOfSeats")) {
                dto.setTotalLicenses(seats.get("numberOfSeats").asInt());
            } else if (seats.has("licensedNumberOfSeats")) {
                dto.setTotalLicenses(seats.get("licensedNumberOfSeats").asInt());
            }
        }

        return dto;
    }

    /**
     * Check if the customer has at least one license of the requested type.
     * 
     * @param subscriptions The customer's current subscriptions
     * @param licenseType   The type of license being requested
     * @return true if the customer has at least one matching license
     */
    public boolean hasMatchingLicense(GoogleWorkspaceSubscriptionListResponseDTO subscriptions, String licenseType) {
        if (subscriptions == null || subscriptions.getSubscriptions() == null) {
            logger.warn("No subscriptions found or subscriptions list is null");
            return false;
        }

        List<GoogleWorkspaceSubscriptionDTO> customerSubscriptions = subscriptions.getSubscriptions();
        logger.info("Checking for license type: {} among {} subscriptions", licenseType, customerSubscriptions.size());

        customerSubscriptions
                .forEach(sub -> logger.info("Available subscription: ID={}, Name={}, Status={}, Licenses={}",
                        sub.getSkuId(), sub.getSkuName(), sub.getStatus(), sub.getTotalLicenses()));

        // Normalize the requested license type for more flexible matching
        String normalizedRequestedType = normalizeGoogleLicenseType(licenseType);
        logger.info("Normalized requested license type: {}", normalizedRequestedType);

        // Check for matching licenses
        boolean hasMatch = customerSubscriptions.stream()
                .anyMatch(subscription -> {
                    if (subscription.getSkuName() == null) {
                        return false;
                    }

                    // Normalize the subscription name for comparison
                    String normalizedSkuName = normalizeGoogleLicenseType(subscription.getSkuName());
                    logger.info("Comparing normalized license types: {} with {}", normalizedSkuName,
                            normalizedRequestedType);

                    boolean isMatching = normalizedSkuName.equals(normalizedRequestedType);
                    boolean isActive = "ACTIVE".equalsIgnoreCase(subscription.getStatus());
                    boolean hasLicenses = subscription.getTotalLicenses() > 0;

                    logger.info("License match: {}, Active: {}, Has licenses: {}", isMatching, isActive, hasLicenses);

                    return isMatching && isActive && hasLicenses;
                });

        logger.info("License match result for {}: {}", licenseType, hasMatch);
        return hasMatch;
    }

    /**
     * Standardize license type names for comparison.
     * This strips "Google Workspace" prefix and normalizes case.
     */
    public String normalizeGoogleLicenseType(String licenseType) {
        if (licenseType == null) {
            return "";
        }

        // Remove "Google Workspace" prefix if present
        String normalized = licenseType.replaceAll("(?i)Google\\s+Workspace\\s+", "");

        // For common license types, standardize capitalization
        if (normalized.equalsIgnoreCase("business starter")) {
            return "Business Starter";
        } else if (normalized.equalsIgnoreCase("business standard")) {
            return "Business Standard";
        } else if (normalized.equalsIgnoreCase("business plus")) {
            return "Business Plus";
        } else if (normalized.equalsIgnoreCase("enterprise essentials")) {
            return "Enterprise Essentials";
        } else if (normalized.equalsIgnoreCase("enterprise standard")) {
            return "Enterprise Standard";
        } else if (normalized.equalsIgnoreCase("enterprise plus")) {
            return "Enterprise Plus";
        }

        // Return as-is for custom license types
        return normalized;
    }
}