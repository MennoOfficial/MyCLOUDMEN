package com.cloudmen.backend.repositories;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for TeamleaderCompany entities
 */
@Repository
public interface TeamleaderCompanyRepository extends MongoRepository<TeamleaderCompany, String> {

    /**
     * Find a company by its Teamleader ID
     * 
     * @param teamleaderId The Teamleader ID
     * @return Optional containing the company if found
     */
    Optional<TeamleaderCompany> findByTeamleaderId(String teamleaderId);

    /**
     * Find companies by name (case-insensitive, partial match)
     * 
     * @param name The company name to search for
     * @return List of companies matching the name
     */
    Iterable<TeamleaderCompany> findByNameContainingIgnoreCase(String name);

    /**
     * Find companies by name with pagination (case-insensitive, partial match)
     * 
     * @param name     The company name to search for
     * @param pageable Pagination information
     * @return Page of companies matching the name
     */
    Page<TeamleaderCompany> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Find companies with MyCLOUDMEN access
     * 
     * @param pageable Pagination information
     * @return Page of companies with MyCLOUDMEN access
     */
    @Query("{'customFields.9faf2006-c6ed-07ec-b25d-131116783b7b.value': true}")
    Page<TeamleaderCompany> findAllWithMyCLOUDMENAccess(Pageable pageable);

    /**
     * Find companies by name with MyCLOUDMEN access
     * 
     * @param name     The company name to search for
     * @param pageable Pagination information
     * @return Page of companies matching the name with MyCLOUDMEN access
     */
    @Query("{'name': {$regex: ?0, $options: 'i'}, 'customFields.9faf2006-c6ed-07ec-b25d-131116783b7b.value': true}")
    Page<TeamleaderCompany> findByNameContainingIgnoreCaseWithMyCLOUDMENAccess(String name, Pageable pageable);

    /**
     * Delete a company by its Teamleader ID
     * 
     * @param teamleaderId The Teamleader ID
     */
    void deleteByTeamleaderId(String teamleaderId);
}