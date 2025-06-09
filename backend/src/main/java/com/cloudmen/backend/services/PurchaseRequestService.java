package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseRequestDTO;
import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.repositories.PurchaseRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing purchase requests.
 */
@Service
public class PurchaseRequestService {
    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestService.class);

    private final PurchaseRequestRepository purchaseRequestRepository;

    public PurchaseRequestService(PurchaseRequestRepository purchaseRequestRepository) {
        this.purchaseRequestRepository = purchaseRequestRepository;
    }

    /**
     * Get all purchase requests with pagination.
     *
     * @param pageable Pagination information
     * @return Page of purchase requests
     */
    public Page<PurchaseRequest> getAllPurchaseRequests(Pageable pageable) {
        return purchaseRequestRepository.findAll(pageable);
    }

    /**
     * Get purchase requests by user email with pagination.
     *
     * @param email    The user's email
     * @param pageable Pagination information
     * @return Page of purchase requests for the user
     */
    public Page<PurchaseRequest> getPurchaseRequestsByUserEmail(String email, Pageable pageable) {
        return purchaseRequestRepository.findByUserEmail(email, pageable);
    }

    /**
     * Get purchase requests by domain with pagination.
     * This allows filtering by company domain to show requests from all users in
     * the same company.
     *
     * @param domain   The domain to filter by
     * @param pageable Pagination information
     * @return Page of purchase requests for the domain
     */
    public Page<PurchaseRequest> getPurchaseRequestsByDomain(String domain, Pageable pageable) {
        log.info("Getting purchase requests for domain: {}, page: {}, size: {}",
                domain, pageable.getPageNumber(), pageable.getPageSize());
        return purchaseRequestRepository.findByDomain(domain, pageable);
    }

    /**
     * Get a purchase request by ID.
     *
     * @param id The purchase request ID
     * @return Optional containing the purchase request, if found
     */
    public Optional<PurchaseRequest> getPurchaseRequestById(String id) {
        return purchaseRequestRepository.findById(id);
    }

    /**
     * Save a purchase request.
     *
     * @param purchaseRequest The purchase request to save
     * @return The saved purchase request
     */
    public PurchaseRequest savePurchaseRequest(PurchaseRequest purchaseRequest) {
        log.info("Saving purchase request with ID: {}, Status: {}, Type: {}",
                purchaseRequest.getId(), purchaseRequest.getStatus(), purchaseRequest.getType());

        try {
            // Set the processed date if the status is either approved or rejected
            if ("APPROVED".equals(purchaseRequest.getStatus()) || "REJECTED".equals(purchaseRequest.getStatus())) {
                purchaseRequest.setProcessedDate(new Date());
            }

            PurchaseRequest savedRequest = purchaseRequestRepository.save(purchaseRequest);

            if (savedRequest == null) {
                throw new RuntimeException("Repository save returned null");
            }

            log.info("Successfully saved purchase request with ID: {}, Status: {}",
                    savedRequest.getId(), savedRequest.getStatus());
            return savedRequest;

        } catch (Exception e) {
            log.error("Error saving purchase request with ID {}: {}",
                    purchaseRequest.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save purchase request: " + e.getMessage(), e);
        }
    }

    /**
     * Update a purchase request's status.
     *
     * @param id     The purchase request ID
     * @param status The new status
     * @return The updated purchase request, or empty if not found
     */
    public Optional<PurchaseRequest> updatePurchaseRequestStatus(String id, String status) {
        log.info("Updating status for purchase request ID: {} to: {}", id, status);

        try {
            Optional<PurchaseRequest> requestOpt = purchaseRequestRepository.findById(id);
            if (requestOpt.isPresent()) {
                PurchaseRequest request = requestOpt.get();
                log.info("Found purchase request with ID: {}, current status: {}", id, request.getStatus());

                request.setStatus(status);
                if ("APPROVED".equals(status) || "REJECTED".equals(status)) {
                    request.setProcessedDate(new Date());
                }

                PurchaseRequest savedRequest = purchaseRequestRepository.save(request);
                log.info("Successfully updated purchase request ID: {} to status: {}", id, savedRequest.getStatus());

                return Optional.of(savedRequest);
            } else {
                log.warn("Purchase request with ID: {} not found for status update", id);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Error updating status for purchase request ID {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the most recent purchase requests.
     *
     * @param limit Maximum number of requests to retrieve
     * @return List of recent purchase requests
     */
    public List<PurchaseRequest> getRecentPurchaseRequests(int limit) {
        return purchaseRequestRepository.findTop10ByOrderByRequestDateDesc();
    }

    /**
     * Create a Google Workspace license request.
     *
     * @param userEmail The user's email
     * @param request   The license request DTO
     * @return The created purchase request
     */
    public PurchaseRequest createGoogleWorkspaceLicenseRequest(String userEmail,
            GoogleWorkspaceLicenseRequestDTO request) {
        log.info("Creating Google Workspace license request for user: {}", userEmail);

        // Generate a new unique ID
        String requestId = UUID.randomUUID().toString();

        // Ensure domain is set - use from request or extract from email
        String domain = request.getDomain();
        if (domain == null || domain.trim().isEmpty()) {
            domain = extractDomainFromEmail(userEmail);
            log.info("Domain not provided in request, extracted from email: {}", domain);
        }

        // Create a new purchase request
        PurchaseRequest purchaseRequest = new PurchaseRequest(requestId, userEmail);
        purchaseRequest.setType("licenses");
        purchaseRequest.setSkuId(request.getSkuId());
        purchaseRequest.setLicenseType(request.getLicenseType());
        purchaseRequest.setQuantity(request.getCount());
        purchaseRequest.setDomain(domain);
        purchaseRequest.setCost(request.getCost());
        purchaseRequest.setStatus("PENDING");
        purchaseRequest.setRequestDate(new Date());

        log.info("Created license request with SKU ID: {} and license type: {} for domain: {}",
                request.getSkuId(), request.getLicenseType(), domain);

        // Save and return the request
        return savePurchaseRequest(purchaseRequest);
    }

    /**
     * Create a Signature Satori credits request.
     *
     * @param userEmail The user's email
     * @param quantity  The number of credits
     * @param cost      The cost of the credits
     * @return The created purchase request
     */
    public PurchaseRequest createSignatureSatoriCreditsRequest(String userEmail, Integer quantity, Double cost) {
        log.info("Creating Signature Satori credits request for user: {}", userEmail);

        // Generate a new unique ID
        String requestId = UUID.randomUUID().toString();

        // Extract domain from user email
        String domain = extractDomainFromEmail(userEmail);

        // Create a new purchase request
        PurchaseRequest purchaseRequest = new PurchaseRequest(requestId, userEmail);
        purchaseRequest.setType("credits");
        purchaseRequest.setQuantity(quantity);
        purchaseRequest.setCost(cost);
        purchaseRequest.setDomain(domain);
        purchaseRequest.setStatus("PENDING");
        purchaseRequest.setRequestDate(new Date());

        log.info("Created credits request for domain: {}", domain);

        // Save and return the request
        return savePurchaseRequest(purchaseRequest);
    }

    /**
     * Extract domain from email address.
     * 
     * @param email The email address
     * @return The domain part of the email
     */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "unknown.com";
        }
        return email.substring(email.indexOf("@") + 1);
    }
}