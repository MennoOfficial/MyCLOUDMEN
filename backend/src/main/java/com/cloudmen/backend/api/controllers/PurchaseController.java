package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceCreateSubscriptionRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseResponseDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.api.dtos.signaturesatori.SignatureSatoriPurchaseRequestDTO;
import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.repositories.PurchaseRequestRepository;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.cloudmen.backend.services.PurchaseEmailService;
import com.cloudmen.backend.services.PurchaseRequestService;
import com.cloudmen.backend.services.SignatureSatoriService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;

/**
 * Controller for handling purchase requests.
 */
@RestController
@RequestMapping("/api")
public class PurchaseController {

    private static final Logger log = LoggerFactory.getLogger(PurchaseController.class);

    private final PurchaseEmailService emailService;
    private final GoogleWorkspaceService googleWorkspaceService;
    private final PurchaseRequestService purchaseRequestService;
    private final WebClient webClient;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    public PurchaseController(
            PurchaseEmailService emailService,
            GoogleWorkspaceService googleWorkspaceService,
            PurchaseRequestService purchaseRequestService) {
        this.emailService = emailService;
        this.googleWorkspaceService = googleWorkspaceService;
        this.purchaseRequestService = purchaseRequestService;
        this.webClient = WebClient.builder().build();
    }

    /**
     * Endpoint to accept a purchase request.
     * 
     * @param requestId The ID of the purchase request to accept
     * @return A success or error message
     */
    @GetMapping("/purchase/accept")
    public ResponseEntity<Map<String, Object>> acceptPurchase(@RequestParam String requestId) {
        log.info("Processing purchase acceptance for request ID: {}", requestId);

        try {
            // Get the request from the database
            Optional<PurchaseRequest> requestOpt = purchaseRequestService.getPurchaseRequestById(requestId);

            if (requestOpt.isEmpty()) {
                // Return error response
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Request not found");
                errorResponse.put("requestId", requestId);
                return ResponseEntity.ok(errorResponse);
            }

            PurchaseRequest request = requestOpt.get();
            String userEmail = request.getUserEmail();
            String type = request.getType();

            // Check request type and redirect to appropriate handler
            if ("licenses".equals(type)) {
                // This is a Google Workspace license request
                log.info("Redirecting to Google Workspace license acceptance endpoint for request: {}", requestId);
                return acceptGoogleWorkspaceLicenseRequest(requestId);
            } else if ("credits".equals(type)) {
                // This is a Signature Satori credits request
                return acceptSignatureSatoriCreditsRequest(requestId);
            } else {
                // Generic purchase flow for other types
                // Update the request status
                request.setStatus("APPROVED");
                purchaseRequestService.savePurchaseRequest(request);

                // Send confirmation email to the user
                try {
                    emailService.sendConfirmationEmail(userEmail);
                } catch (MessagingException e) {
                    log.error("Failed to send confirmation email", e);
                    // Continue processing even if email fails
                }

                // Return success response
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("requestId", requestId);
                response.put("email", userEmail);
                response.put("type", "purchase");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            log.error("Error processing purchase acceptance: {}", e.getMessage(), e);

            // Return error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("requestId", requestId);
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Endpoint to request Google Workspace licenses.
     * 
     * @param request    The license request details
     * @param userEmail  The email of the user making the request
     * @param customerId The customer ID in Google Workspace
     * @return ResponseEntity with the request details
     */
    @PostMapping("/purchase/google-workspace/request")
    public Mono<ResponseEntity<GoogleWorkspaceLicenseResponseDTO>> requestGoogleWorkspaceLicenses(
            @Valid @RequestBody GoogleWorkspaceLicenseRequestDTO request,
            @RequestParam String userEmail,
            @RequestParam String customerId) {

        log.info("Creating Google Workspace license request for user: {}, customer: {}", userEmail, customerId);

        // Determine domain if not specified
        if (request.getDomain() == null || request.getDomain().isEmpty()) {
            String domain = extractDomainFromEmail(userEmail);
            request.setDomain(domain);
            log.info("Using domain extracted from email: {}", domain);
        }

        // Check if the customer already has at least one license of this type
        return googleWorkspaceService.getCustomerSubscriptions(customerId)
                .flatMap(subscriptions -> {
                    // Check if customer has at least one subscription of the requested type
                    boolean hasExistingLicense = googleWorkspaceService.hasMatchingLicense(
                            subscriptions, request.getLicenseType());

                    if (!hasExistingLicense) {
                        log.warn("Customer {} does not have any existing licenses of type: {}",
                                customerId, request.getLicenseType());

                        GoogleWorkspaceLicenseResponseDTO errorResponse = new GoogleWorkspaceLicenseResponseDTO();
                        errorResponse.setStatus("REJECTED");
                        errorResponse.setMessage("Company must have at least one license of type " +
                                request.getLicenseType() + " before purchasing additional licenses");

                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
                    }

                    // Create and save the purchase request
                    PurchaseRequest purchaseRequest = purchaseRequestService.createGoogleWorkspaceLicenseRequest(
                            userEmail, request);

                    // Create response DTO
                    GoogleWorkspaceLicenseResponseDTO response = new GoogleWorkspaceLicenseResponseDTO();
                    response.setCount(request.getCount());
                    response.setLicenseType(request.getLicenseType());
                    response.setDomain(request.getDomain());
                    response.setCost(request.getCost());
                    response.setRequestId(purchaseRequest.getId());
                    response.setStatus("PENDING");
                    response.setMessage("Google Workspace license request has been submitted and is pending approval");

                    try {
                        // Send email notification
                        emailService.sendGoogleWorkspaceLicenseRequest(
                                userEmail,
                                purchaseRequest.getId(),
                                request.getCount(),
                                request.getLicenseType(),
                                request.getDomain(),
                                userEmail,
                                customerId,
                                request.getCost(),
                                getCompanyNameFromDomain(userEmail));

                        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(response));
                    } catch (MessagingException e) {
                        log.error("Failed to send Google Workspace license request email", e);
                        response.setMessage("Request created but failed to send notification email");
                        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).body(response));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error checking existing licenses for customer {}: {}", customerId, e.getMessage());
                    GoogleWorkspaceLicenseResponseDTO errorResponse = new GoogleWorkspaceLicenseResponseDTO();
                    errorResponse.setStatus("ERROR");
                    errorResponse.setMessage("Error checking existing licenses: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
                });
    }

    /**
     * Endpoint to accept a Google Workspace license purchase request.
     * 
     * @param requestId The ID of the purchase request to accept
     * @return ResponseEntity with the result
     */
    @GetMapping("/purchase/google-workspace/accept")
    public ResponseEntity<Map<String, Object>> acceptGoogleWorkspaceLicenseRequest(@RequestParam String requestId) {
        try {
            // Check if the request exists in the database
            Optional<PurchaseRequest> requestOpt = purchaseRequestService.getPurchaseRequestById(requestId);

            if (requestOpt.isEmpty()) {
                // Return error response
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "License request not found");
                errorResponse.put("requestId", requestId);
                return ResponseEntity.ok(errorResponse);
            }

            PurchaseRequest request = requestOpt.get();
            String userEmail = request.getUserEmail();

            // Update the request status
            request.setStatus("APPROVED");
            purchaseRequestService.savePurchaseRequest(request);

            // Send confirmation email
            try {
                emailService.sendGoogleWorkspaceLicenseConfirmation(
                        userEmail,
                        request.getQuantity(),
                        request.getLicenseType(),
                        request.getDomain(),
                        request.getCost());
            } catch (MessagingException e) {
                log.error("Failed to send Google Workspace license confirmation email", e);
            }

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("requestId", requestId);
            response.put("email", userEmail);
            response.put("licenseType", request.getLicenseType());
            response.put("count", request.getQuantity());
            response.put("domain", request.getDomain());
            response.put("type", "license");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing Google Workspace license request acceptance: {}", e.getMessage(), e);

            // Return error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("requestId", requestId);
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Endpoint to request Signature Satori credits.
     * 
     * @param request    The credits request details
     * @param userEmail  The email of the user making the request
     * @param customerId The customer ID in Signature Satori
     * @return ResponseEntity with the request details
     */
    @PostMapping("/purchase/signature-satori/request")
    public ResponseEntity<Map<String, Object>> requestSignatureSatoriCredits(
            @RequestBody Map<String, Object> request,
            @RequestParam String userEmail,
            @RequestParam String customerId) {

        log.info("Creating Signature Satori credits request for user: {}, customer: {}", userEmail, customerId);

        try {
            // Extract request parameters - handle potential Number types safely
            Integer quantity = null;
            Double cost = null;

            // Parse parameters from request body
            Object quantityObj = request.get("quantity");
            Object costObj = request.get("cost");

            if (quantityObj instanceof Integer) {
                quantity = (Integer) quantityObj;
            } else if (quantityObj instanceof Number) {
                quantity = ((Number) quantityObj).intValue();
            } else if (quantityObj instanceof String) {
                quantity = Integer.parseInt((String) quantityObj);
            }

            if (costObj instanceof Double) {
                cost = (Double) costObj;
            } else if (costObj instanceof Number) {
                cost = ((Number) costObj).doubleValue();
            } else if (costObj instanceof String) {
                cost = Double.parseDouble((String) costObj);
            }

            // Validate quantity
            if (quantity == null || quantity < 100) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "ERROR");
                errorResponse.put("message", "Quantity must be at least 100 credits");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Create and save purchase request
            PurchaseRequest purchaseRequest = purchaseRequestService.createSignatureSatoriCreditsRequest(
                    userEmail, quantity, cost);

            // Create response
            Map<String, Object> response = new HashMap<>();
            response.put("requestId", purchaseRequest.getId());
            response.put("quantity", quantity);
            response.put("cost", cost);
            response.put("status", "PENDING");
            response.put("message", "Signature Satori credits request has been submitted and is pending approval");

            try {
                // Send purchase request email to the user
                emailService.sendPurchaseRequest(userEmail, purchaseRequest.getId());
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } catch (MessagingException e) {
                log.error("Failed to send Signature Satori credits request email", e);
                response.put("message", "Request created but failed to send notification email");
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            }
        } catch (Exception e) {
            log.error("Error creating Signature Satori credits request: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "ERROR");
            errorResponse.put("message", "Failed to create request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Endpoint to accept a Signature Satori credits purchase request.
     * 
     * @param requestId The ID of the purchase request to accept
     * @return ResponseEntity with the result
     */
    @GetMapping("/purchase/signature-satori/accept")
    public ResponseEntity<Map<String, Object>> acceptSignatureSatoriCreditsRequest(@RequestParam String requestId) {
        try {
            // Check if the request exists in the database
            Optional<PurchaseRequest> requestOpt = purchaseRequestService.getPurchaseRequestById(requestId);

            if (requestOpt.isEmpty()) {
                // Return error response
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Credits request not found");
                errorResponse.put("requestId", requestId);
                return ResponseEntity.ok(errorResponse);
            }

            PurchaseRequest request = requestOpt.get();
            String userEmail = request.getUserEmail();

            // Update the request status
            request.setStatus("APPROVED");
            purchaseRequestService.savePurchaseRequest(request);

            // Send confirmation email
            try {
                emailService.sendConfirmationEmail(userEmail);
            } catch (MessagingException e) {
                log.error("Failed to send credits confirmation email", e);
            }

            // Return success response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("requestId", requestId);
            response.put("email", userEmail);
            response.put("quantity", request.getQuantity());
            response.put("cost", request.getCost());
            response.put("type", "credits");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing Signature Satori credits request acceptance: {}", e.getMessage(), e);

            // Return error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("requestId", requestId);
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Extract domain from an email address.
     * 
     * @param email The email address
     * @return The domain part of the email
     */
    private String extractDomainFromEmail(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf('@') + 1);
        }
        return "";
    }

    /**
     * Get company name from domain using Teamleader API.
     * 
     * @param emailOrDomain Email or domain to get company name for
     * @return Company name or fallback value
     */
    private String getCompanyNameFromDomain(String emailOrDomain) {
        String domain = emailOrDomain;
        if (emailOrDomain.contains("@")) {
            domain = extractDomainFromEmail(emailOrDomain);
        }

        try {
            // Try to get company from Teamleader by domain
            String apiUrl = baseUrl + "/api/teamleader/companies/domain/" + domain;
            log.info("Fetching company for domain: {} from: {}", domain, apiUrl);

            // Make a synchronous request to the teamleader company endpoint
            String response = webClient.get()
                    .uri(apiUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null && response.contains("\"name\"")) {
                // Extract name from JSON response - a simple approach
                String nameField = "\"name\"";
                int nameStart = response.indexOf(nameField) + nameField.length();
                int valueStart = response.indexOf("\"", nameStart) + 1;
                int valueEnd = response.indexOf("\"", valueStart);
                if (valueStart > 0 && valueEnd > 0) {
                    return response.substring(valueStart, valueEnd);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get company name for domain {}: {}", domain, e.getMessage());
        }

        // Fallback to domain name if company wasn't found
        return "Company for " + domain;
    }
}