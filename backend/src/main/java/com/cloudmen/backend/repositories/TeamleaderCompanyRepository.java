package com.cloudmen.backend.repositories;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import org.springframework.data.mongodb.repository.MongoRepository;
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
     * Delete a company by its Teamleader ID
     * 
     * @param teamleaderId The Teamleader ID
     */
    void deleteByTeamleaderId(String teamleaderId);
}