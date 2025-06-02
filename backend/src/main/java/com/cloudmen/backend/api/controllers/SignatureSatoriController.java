package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriCreditsDTO;
import com.cloudmen.backend.services.SignatureSatoriService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Controller for SignatureSatori API endpoints
 * Note: Credit purchases now go through the purchase request flow in
 * PurchaseController
 */
@RestController
@RequestMapping("/api/signaturesatori")
public class SignatureSatoriController {

    private static final Logger logger = LoggerFactory.getLogger(SignatureSatoriController.class);
    private final SignatureSatoriService signatureSatoriService;

    public SignatureSatoriController(SignatureSatoriService signatureSatoriService) {
        this.signatureSatoriService = signatureSatoriService;
        logger.info("SignatureSatoriController initialized");
    }

    /**
     * Get customer credits information
     * 
     * @param customerId The customer ID
     * @return Customer details including credit balance
     */
    @GetMapping("/customers/{customerId}/credits")
    public Mono<ResponseEntity<SignatureSatoriCreditsDTO>> getCustomerCredits(
            @PathVariable String customerId) {
        logger.info("Received request to get credits for customer: {}", customerId);

        return signatureSatoriService.getCustomerCredits(customerId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(e -> logger.error("Error processing get credits request: {}", e.getMessage()));
    }

}