package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.GoogleWorkspaceSubscriptionDTO;
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
    private final ObjectMapper objectMapper;

    @Value("${google.workspace.api.baseUrl:https://mycloudmen.mennoplochaet.be/mock-api}")
    private String apiBaseUrl;

    public GoogleWorkspaceService(WebClient.Builder webClientBuilder,
            @Qualifier("webClientRetrySpec") Retry webClientRetrySpec,
            ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.webClientRetrySpec = webClientRetrySpec;
        this.objectMapper = objectMapper;
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
    public Mono<GoogleWorkspaceSubscriptionDTO.SubscriptionListResponse> getCustomerSubscriptions(String customerId) {
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
            GoogleWorkspaceSubscriptionDTO.CreateSubscriptionRequest request) {
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
    private GoogleWorkspaceSubscriptionDTO.SubscriptionListResponse convertToSimplifiedSubscriptionList(
            JsonNode jsonNode) {
        GoogleWorkspaceSubscriptionDTO.SubscriptionListResponse response = new GoogleWorkspaceSubscriptionDTO.SubscriptionListResponse();
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
    private GoogleWorkspaceSubscriptionDTO convertToSimplifiedSubscription(JsonNode jsonNode) {
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
}