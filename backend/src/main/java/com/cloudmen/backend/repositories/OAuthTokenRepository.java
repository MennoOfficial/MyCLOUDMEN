package com.cloudmen.backend.repositories;

import com.cloudmen.backend.domain.models.OAuthToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for managing OAuth tokens in MongoDB.
 */
@Repository
public interface OAuthTokenRepository extends MongoRepository<OAuthToken, String> {

    /**
     * Find a token by its provider name (e.g., "teamleader")
     */
    Optional<OAuthToken> findByProvider(String provider);
}