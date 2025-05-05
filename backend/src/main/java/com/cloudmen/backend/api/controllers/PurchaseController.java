package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseRequestDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseResponseDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import com.cloudmen.backend.services.PurchaseEmailService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for handling purchase requests.
 */
@RestController
@RequestMapping("/api/purchase")
public class PurchaseController {

    private static final Logger log = LoggerFactory.getLogger(PurchaseController.class);

    // Simple in-memory store to track pending requests
    private final ConcurrentHashMap<String, String> pendingRequests = new ConcurrentHashMap<>();

    // In-memory store to track Google Workspace license requests with details
    private final ConcurrentHashMap<String, GoogleWorkspaceLicenseRequestDTO> pendingGoogleWorkspaceRequests = new ConcurrentHashMap<>();

    // Map to store user emails for each request
    private final ConcurrentHashMap<String, String> requestUsers = new ConcurrentHashMap<>();

    private final PurchaseEmailService emailService;
    private final GoogleWorkspaceService googleWorkspaceService;

    @Value("${user.admin.email:admin@example.com}")
    private String adminEmail;

    public PurchaseController(PurchaseEmailService emailService, GoogleWorkspaceService googleWorkspaceService) {
        this.emailService = emailService;
        this.googleWorkspaceService = googleWorkspaceService;
    }

    /**
     * Endpoint to create a new purchase request and send notification email.
     * 
     * @param userEmail The email of the user making the request
     * @return A success message with the request ID
     */
    @PostMapping("/request")
    public String createPurchaseRequest(@RequestParam String userEmail) {
        String requestId = UUID.randomUUID().toString();
        log.info("Creating purchase request with ID: {} for user: {}", requestId, userEmail);

        // Store the request
        pendingRequests.put(requestId, userEmail);

        try {
            // Send email to admin for approval
            emailService.sendPurchaseRequest(adminEmail, requestId);
            return "Purchase request created. Request ID: " + requestId;
        } catch (MessagingException e) {
            log.error("Failed to send purchase request email", e);
            return "Purchase request created but failed to send notification. Request ID: " + requestId;
        }
    }

    /**
     * Endpoint to accept a purchase request.
     * 
     * @param requestId The ID of the purchase request to accept
     * @return A success or error message
     */
    @GetMapping("/accept")
    public String acceptPurchase(@RequestParam String requestId) {
        log.info("Processing purchase acceptance for request ID: {}", requestId);

        // Check if the request exists
        String userEmail = pendingRequests.get(requestId);
        if (userEmail == null) {
            log.warn("Purchase request not found: {}", requestId);
            return "Purchase request not found or already processed.";
        }

        // Remove the request from pending
        pendingRequests.remove(requestId);

        // Process the purchase (in a real application, you would do more here)
        log.info("Purchase request {} accepted for user {}", requestId, userEmail);

        // Send confirmation email to the user
        try {
            emailService.sendConfirmationEmail(userEmail);
        } catch (MessagingException e) {
            log.error("Failed to send confirmation email", e);
            // Continue processing even if email fails
        }

        return "Purchase request " + requestId + " has been accepted.";
    }

    /**
     * Endpoint to request Google Workspace licenses.
     * 
     * @param request   The license request details
     * @param userEmail The email of the user making the request
     * @return ResponseEntity with the request details
     */
    @PostMapping("/google-workspace/request")
    public Mono<ResponseEntity<GoogleWorkspaceLicenseResponseDTO>> requestGoogleWorkspaceLicenses(
            @Valid @RequestBody GoogleWorkspaceLicenseRequestDTO request,
            @RequestParam String userEmail,
            @RequestParam String customerId) {

        String requestId = UUID.randomUUID().toString();
        log.info("Creating Google Workspace license request with ID: {} for user: {}, customer: {}",
                requestId, userEmail, customerId);

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
                    boolean hasExistingLicense = hasMatchingLicense(subscriptions, request.getLicenseType());

                    if (!hasExistingLicense) {
                        log.warn("Customer {} does not have any existing licenses of type: {}",
                                customerId, request.getLicenseType());

                        GoogleWorkspaceLicenseResponseDTO errorResponse = new GoogleWorkspaceLicenseResponseDTO();
                        errorResponse.setRequestId(requestId);
                        errorResponse.setStatus("REJECTED");
                        errorResponse.setMessage("Company must have at least one license of type " +
                                request.getLicenseType() + " before purchasing additional licenses");

                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
                    }

                    // Company has existing licenses, proceed with the request
                    // Store the request details
                    pendingGoogleWorkspaceRequests.put(requestId, request);
                    requestUsers.put(requestId, userEmail);

                    GoogleWorkspaceLicenseResponseDTO response = new GoogleWorkspaceLicenseResponseDTO();
                    response.setCount(request.getCount());
                    response.setLicenseType(request.getLicenseType());
                    response.setDomain(request.getDomain());
                    response.setPurchaseDate(ZonedDateTime.now());
                    response.setRequestId(requestId);
                    response.setStatus("PENDING");
                    response.setMessage("Google Workspace license request has been submitted and is pending approval");

                    try {
                        // Send email to admin for approval
                        emailService.sendGoogleWorkspaceLicenseRequest(
                                adminEmail,
                                requestId,
                                request.getCount(),
                                request.getLicenseType(),
                                request.getDomain(),
                                userEmail,
                                customerId);

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
                    errorResponse.setRequestId(requestId);
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
    @GetMapping("/google-workspace/accept")
    public ResponseEntity<GoogleWorkspaceLicenseResponseDTO> acceptGoogleWorkspaceLicenseRequest(
            @RequestParam String requestId) {

        log.info("Processing Google Workspace license acceptance for request ID: {}", requestId);

        // Check if the request exists
        GoogleWorkspaceLicenseRequestDTO request = pendingGoogleWorkspaceRequests.get(requestId);
        String userEmail = requestUsers.get(requestId);

        if (request == null || userEmail == null) {
            log.warn("Google Workspace license request not found: {}", requestId);

            GoogleWorkspaceLicenseResponseDTO response = new GoogleWorkspaceLicenseResponseDTO();
            response.setRequestId(requestId);
            response.setStatus("NOT_FOUND");
            response.setMessage("Google Workspace license request not found or already processed");

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Remove the request from pending
        pendingGoogleWorkspaceRequests.remove(requestId);
        requestUsers.remove(requestId);

        // Process the license purchase (in a real application, you would do more here)
        log.info("Google Workspace license request {} accepted for user {}", requestId, userEmail);

        GoogleWorkspaceLicenseResponseDTO response = new GoogleWorkspaceLicenseResponseDTO();
        response.setCount(request.getCount());
        response.setLicenseType(request.getLicenseType());
        response.setDomain(request.getDomain());
        response.setPurchaseDate(ZonedDateTime.now());
        response.setRequestId(requestId);
        response.setStatus("APPROVED");
        response.setMessage("Google Workspace license request has been approved and processed");

        // Send confirmation email to the user
        try {
            emailService.sendGoogleWorkspaceLicenseConfirmation(
                    userEmail,
                    request.getCount(),
                    request.getLicenseType(),
                    request.getDomain());
        } catch (MessagingException e) {
            log.error("Failed to send Google Workspace license confirmation email", e);
            // Continue processing even if email fails
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Check if the customer has at least one license of the requested type.
     * 
     * @param subscriptions The customer's current subscriptions
     * @param licenseType   The type of license being requested
     * @return true if the customer has at least one matching license
     */
    private boolean hasMatchingLicense(GoogleWorkspaceSubscriptionListResponseDTO subscriptions, String licenseType) {
        if (subscriptions == null || subscriptions.getSubscriptions() == null) {
            return false;
        }

        List<GoogleWorkspaceSubscriptionDTO> customerSubscriptions = subscriptions.getSubscriptions();

        return customerSubscriptions.stream()
                .anyMatch(subscription ->
                // Check if the subscription matches the requested type
                // Note: You may need to adjust this logic based on how license types are
                // represented
                subscription.getSkuName() != null &&
                        subscription.getSkuName().equalsIgnoreCase(licenseType) &&
                        subscription.getStatus() != null &&
                        subscription.getStatus().equalsIgnoreCase("ACTIVE") &&
                        subscription.getTotalLicenses() > 0);
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
}