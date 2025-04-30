package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriCreditsDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseRequestDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with SignatureSatori API for credit management
 */
@Service
public class SignatureSatoriService {

    private static final Logger logger = LoggerFactory.getLogger(SignatureSatoriService.class);
    private WebClient webClient;
    private final WebClient.Builder webClientBuilder;
    private final Retry webClientRetrySpec;
    private final ObjectMapper objectMapper;

    @Value("${signaturesatori.api.baseUrl:${SIGNATURESATORI_API_URL:https://mycloudmen.mennoplochaet.be/signature-satori-api}}")
    private String apiBaseUrl;

    @Value("${signaturesatori.api.token:${SIGNATURESATORI_API_TOKEN:}}")
    private String apiToken;

    public SignatureSatoriService(WebClient.Builder webClientBuilder,
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
        logger.info("SignatureSatoriService initialized with baseUrl: {}", apiBaseUrl);
    }

    /**
     * Get customer credit information
     * 
     * @param customerId The customer ID
     * @return Customer details including credit balance
     */
    public Mono<SignatureSatoriCreditsDTO> getCustomerCredits(String customerId) {
        logger.info("Fetching credits for customer: {}", customerId);

        return webClient.get()
                .uri("/reseller/customers/{customerId}", customerId)
                .headers(headers -> {
                    if (apiToken != null && !apiToken.isEmpty()) {
                        headers.setBearerAuth(apiToken);
                    }
                })
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    if (response.has("customer")) {
                        return extractCustomerInfo(response.get("customer"));
                    }
                    throw new RuntimeException("Invalid response format");
                })
                .retryWhen(webClientRetrySpec)
                .doOnSuccess(customer -> logger.info("Successfully retrieved credits for customer {}: {}",
                        customerId, customer.getCreditBalance()))
                .doOnError(e -> logger.error("Error fetching customer credits: {}", e.getMessage()));
    }

    /**
     * Purchase credits for a customer
     * 
     * @param customerId The customer ID
     * @param request    The purchase request with credit count
     * @return Transaction details
     */
    public Mono<SignatureSatoriPurchaseResponseDTO> purchaseCredits(String customerId,
            SignatureSatoriPurchaseRequestDTO request) {
        logger.info("Purchasing {} credits for customer: {}", request.getCount(), customerId);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("count", request.getCount());

        return webClient.post()
                .uri("/reseller/customers/{customerId}/credits/new", customerId)
                .headers(headers -> {
                    if (apiToken != null && !apiToken.isEmpty()) {
                        headers.setBearerAuth(apiToken);
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> {
                    if (response.has("transaction")) {
                        return objectMapper.convertValue(
                                response.get("transaction"),
                                SignatureSatoriPurchaseResponseDTO.class);
                    }
                    throw new RuntimeException("Invalid response format");
                })
                .retryWhen(webClientRetrySpec)
                .doOnSuccess(transaction -> logger.info("Successfully purchased {} credits for customer {}",
                        transaction.getCount(), customerId))
                .doOnError(e -> logger.error("Error purchasing credits: {}", e.getMessage()));
    }

    /**
     * Extract customer information from JSON response
     */
    private SignatureSatoriCreditsDTO extractCustomerInfo(JsonNode customerNode) {
        SignatureSatoriCreditsDTO dto = new SignatureSatoriCreditsDTO();

        if (customerNode.has("customerId")) {
            dto.setCustomerId(customerNode.get("customerId").asText());
        }

        if (customerNode.has("creditBalance")) {
            dto.setCreditBalance(customerNode.get("creditBalance").asInt());
        }

        if (customerNode.has("ownerEmail")) {
            dto.setOwnerEmail(customerNode.get("ownerEmail").asText());
        }

        if (customerNode.has("creditDiscountPercent")) {
            dto.setCreditDiscountPercent(customerNode.get("creditDiscountPercent").asDouble());
        }

        // Extract domains array if present
        if (customerNode.has("domains") && customerNode.get("domains").isArray()) {
            dto.setDomains(objectMapper.convertValue(
                    customerNode.get("domains"),
                    new TypeReference<List<String>>() {
                    }));
        }

        return dto;
    }
}