package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Controller for Google Workspace API endpoints.
 */
@RestController
@RequestMapping("/api/google-workspace")
public class GoogleWorkspaceController {

    private static final Logger logger = LoggerFactory.getLogger(GoogleWorkspaceController.class);
    private final GoogleWorkspaceService googleWorkspaceService;

    public GoogleWorkspaceController(GoogleWorkspaceService googleWorkspaceService) {
        this.googleWorkspaceService = googleWorkspaceService;
        logger.info("GoogleWorkspaceController initialized");
    }

    /**
     * Get all license information for a customer.
     *
     * @param customerId The Google customer ID
     * @return List of licenses by subscription type
     */
    @GetMapping("/customers/{customerId}/licenses")
    public Mono<ResponseEntity<GoogleWorkspaceSubscriptionDTO.SubscriptionListResponse>> getCustomerLicenses(
            @PathVariable String customerId) {
        logger.info("Received request to get license information for customer: {}", customerId);

        return googleWorkspaceService.getCustomerSubscriptions(customerId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(e -> logger.error("Error processing get licenses request: {}", e.getMessage()));
    }

    /**
     * Add new licenses for a customer.
     *
     * @param customerId The Google customer ID
     * @param request    The license request with SKU, plan type and number of
     *                   licenses
     * @return The updated license information
     */
    @PostMapping("customers/{customerId}/licenses")
    public Mono<ResponseEntity<GoogleWorkspaceSubscriptionDTO>> addLicenses(
            @PathVariable String customerId,
            @RequestBody GoogleWorkspaceSubscriptionDTO.CreateSubscriptionRequest request) {
        logger.info("Received request to add {} licenses of SKU {} for customer: {}",
                request.getNumberOfLicenses(), request.getSkuId(), customerId);

        return googleWorkspaceService.createSubscription(customerId, request)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.badRequest().build())
                .doOnError(e -> logger.error("Error processing add licenses request: {}", e.getMessage()));
    }

    /**
     * Get available SKUs.
     *
     * @return List of available SKUs
     */
    @GetMapping("/skus")
    public Mono<ResponseEntity<Object>> getAvailableSkus() {
        logger.info("Received request to get available SKUs");

        return googleWorkspaceService.getAvailableSkus()
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(e -> logger.error("Error processing get SKUs request: {}", e.getMessage()));
    }
}