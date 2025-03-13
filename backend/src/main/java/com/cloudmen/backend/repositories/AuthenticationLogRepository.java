package com.cloudmen.backend.repositories;

import com.cloudmen.backend.domain.models.AuthenticationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for AuthenticationLog entities.
 * Provides methods to query authentication logs from the MongoDB database.
 */
@Repository
public interface AuthenticationLogRepository extends MongoRepository<AuthenticationLog, String> {

        // Find logs by email
        List<AuthenticationLog> findByEmail(String email);

        Page<AuthenticationLog> findByEmail(String email, Pageable pageable);

        // Find logs by user ID
        List<AuthenticationLog> findByUserId(String userId);

        Page<AuthenticationLog> findByUserId(String userId, Pageable pageable);

        // Find logs by primary domain
        List<AuthenticationLog> findByPrimaryDomain(String primaryDomain);

        Page<AuthenticationLog> findByPrimaryDomain(String primaryDomain, Pageable pageable);

        // Find logs by success status
        List<AuthenticationLog> findBySuccessful(boolean successful);

        Page<AuthenticationLog> findBySuccessful(boolean successful, Pageable pageable);

        // Find logs by IP address
        List<AuthenticationLog> findByIpAddress(String ipAddress);

        Page<AuthenticationLog> findByIpAddress(String ipAddress, Pageable pageable);

        // Find logs within a time range
        List<AuthenticationLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

        Page<AuthenticationLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

        // Find logs by email and success status
        List<AuthenticationLog> findByEmailAndSuccessful(String email, boolean successful);

        Page<AuthenticationLog> findByEmailAndSuccessful(String email, boolean successful, Pageable pageable);

        // Find logs by primary domain and success status
        List<AuthenticationLog> findByPrimaryDomainAndSuccessful(String primaryDomain, boolean successful);

        Page<AuthenticationLog> findByPrimaryDomainAndSuccessful(String primaryDomain, boolean successful,
                        Pageable pageable);

        // Combined filters
        Page<AuthenticationLog> findByEmailAndPrimaryDomain(String email, String primaryDomain, Pageable pageable);

        Page<AuthenticationLog> findByEmailAndSuccessfulAndTimestampBetween(
                        String email, boolean successful, LocalDateTime start, LocalDateTime end, Pageable pageable);

        Page<AuthenticationLog> findByPrimaryDomainAndSuccessfulAndTimestampBetween(
                        String primaryDomain, boolean successful, LocalDateTime start, LocalDateTime end,
                        Pageable pageable);

        Page<AuthenticationLog> findByEmailAndPrimaryDomainAndSuccessful(
                        String email, String primaryDomain, boolean successful, Pageable pageable);

        Page<AuthenticationLog> findByEmailAndPrimaryDomainAndSuccessfulAndTimestampBetween(
                        String email, String primaryDomain, boolean successful,
                        LocalDateTime start, LocalDateTime end, Pageable pageable);
}