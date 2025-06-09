package com.cloudmen.backend.repositories;

import com.cloudmen.backend.domain.models.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PurchaseRequest operations.
 */
@Repository
public interface PurchaseRequestRepository extends MongoRepository<PurchaseRequest, String> {

    /**
     * Find all purchase requests for a user.
     * 
     * @param userEmail The user's email
     * @return List of purchase requests
     */
    List<PurchaseRequest> findByUserEmail(String userEmail);

    /**
     * Find all purchase requests for a user with pagination.
     * 
     * @param userEmail The user's email
     * @param pageable  The pagination information
     * @return Page of purchase requests
     */
    Page<PurchaseRequest> findByUserEmail(String userEmail, Pageable pageable);

    /**
     * Find all pending purchase requests.
     * 
     * @return List of pending purchase requests
     */
    List<PurchaseRequest> findByStatus(String status);

    /**
     * Find the most recent purchase requests, ordered by request date.
     * 
     * @return List of the 10 most recent purchase requests
     */
    List<PurchaseRequest> findTop10ByOrderByRequestDateDesc();

    /**
     * Find all purchase requests for a domain with pagination.
     * This allows filtering by company domain to show requests from all users in
     * the same company.
     * 
     * @param domain   The domain to filter by
     * @param pageable The pagination information
     * @return Page of purchase requests for the domain
     */
    Page<PurchaseRequest> findByDomain(String domain, Pageable pageable);

    /**
     * Find all purchase requests for a domain.
     * 
     * @param domain The domain to filter by
     * @return List of purchase requests for the domain
     */
    List<PurchaseRequest> findByDomain(String domain);
}