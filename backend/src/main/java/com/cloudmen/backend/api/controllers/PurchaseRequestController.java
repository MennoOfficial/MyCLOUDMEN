package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.services.PurchaseRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for managing purchase requests.
 */
@RestController
@RequestMapping("/api/purchase-requests")
public class PurchaseRequestController {
    private static final Logger log = LoggerFactory.getLogger(PurchaseRequestController.class);

    private final PurchaseRequestService purchaseRequestService;

    public PurchaseRequestController(PurchaseRequestService purchaseRequestService) {
        this.purchaseRequestService = purchaseRequestService;
        log.info("PurchaseRequestController initialized");
    }

    /**
     * Get all purchase requests with pagination.
     *
     * @param page  Page number (zero-based)
     * @param size  Page size
     * @param sort  Field to sort by
     * @param order Sort direction (asc or desc)
     * @return ResponseEntity with paginated purchase requests
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPurchaseRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "requestDate") String sort,
            @RequestParam(defaultValue = "desc") String order) {

        log.info("Getting all purchase requests, page: {}, size: {}, sort: {}, order: {}", page, size, sort, order);

        try {
            Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort));

            Page<PurchaseRequest> purchaseRequests = purchaseRequestService.getAllPurchaseRequests(pageable);

            Map<String, Object> response = createPaginatedResponse(purchaseRequests);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting purchase requests", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get purchase requests for a specific user.
     *
     * @param email User's email
     * @param page  Page number (zero-based)
     * @param size  Page size
     * @return ResponseEntity with paginated purchase requests for the user
     */
    @GetMapping("/user/{email}")
    public ResponseEntity<Map<String, Object>> getPurchaseRequestsByUser(
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Getting purchase requests for user: {}, page: {}, size: {}", email, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestDate"));
            Page<PurchaseRequest> purchaseRequests = purchaseRequestService.getPurchaseRequestsByUserEmail(email,
                    pageable);

            Map<String, Object> response = createPaginatedResponse(purchaseRequests);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting purchase requests for user: " + email, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get a purchase request by ID.
     *
     * @param id Purchase request ID
     * @return ResponseEntity with the purchase request or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseRequest> getPurchaseRequestById(@PathVariable String id) {
        log.info("Getting purchase request with ID: {}", id);

        Optional<PurchaseRequest> purchaseRequest = purchaseRequestService.getPurchaseRequestById(id);

        return purchaseRequest
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get recent purchase requests.
     *
     * @return ResponseEntity with the 10 most recent purchase requests
     */
    @GetMapping("/recent")
    public ResponseEntity<List<PurchaseRequest>> getRecentPurchaseRequests() {
        log.info("Getting recent purchase requests");

        List<PurchaseRequest> recentRequests = purchaseRequestService.getRecentPurchaseRequests(10);
        return ResponseEntity.ok(recentRequests);
    }

    /**
     * Update a purchase request's status.
     *
     * @param id     Purchase request ID
     * @param status New status (PENDING, APPROVED, REJECTED)
     * @return ResponseEntity with the updated purchase request or 404 if not found
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<PurchaseRequest> updatePurchaseRequestStatus(
            @PathVariable String id,
            @RequestParam String status) {

        log.info("Updating purchase request {} status to: {}", id, status);

        // Validate status value
        if (!isValidStatus(status)) {
            log.warn("Invalid status value: {}", status);
            return ResponseEntity.badRequest().build();
        }

        Optional<PurchaseRequest> updatedRequest = purchaseRequestService.updatePurchaseRequestStatus(id, status);

        return updatedRequest
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Helper method to validate status values.
     */
    private boolean isValidStatus(String status) {
        return "PENDING".equals(status) ||
                "AWAITING_CONFIRMATION".equals(status) ||
                "APPROVED".equals(status) ||
                "REJECTED".equals(status);
    }

    /**
     * Helper method to create a paginated response.
     */
    private Map<String, Object> createPaginatedResponse(Page<PurchaseRequest> page) {
        Map<String, Object> response = new HashMap<>();
        response.put("items", page.getContent());
        response.put("currentPage", page.getNumber());
        response.put("totalItems", page.getTotalElements());
        response.put("totalPages", page.getTotalPages());
        return response;
    }
}