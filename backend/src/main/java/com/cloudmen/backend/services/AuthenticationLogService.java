package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.AuthenticationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing authentication logs in the system.
 * Provides methods for creating, retrieving, and managing log entries.
 */
@Service
public class AuthenticationLogService {

    // Constants for log messages
    private static final String LOG_SUCCESS_AUTH = "Logging successful authentication for email: {}, IP: {}";
    private static final String LOG_FAILED_AUTH = "Logging failed authentication for email: {}, IP: {}, Reason: {}";
    private static final String LOG_ERROR_SUCCESS_AUTH = "Error logging successful authentication";
    private static final String LOG_ERROR_FAILED_AUTH = "Error logging failed authentication";
    private static final String LOG_FILTERED_QUERY = "Getting filtered logs - Email: {}, Domain: {}, Successful: {}, StartDate: {}, EndDate: {}";
    private static final String LOG_SERVICE_INIT = "AuthenticationLogService initialized";
    private static final String LOG_DELETE_OLD = "Deleting logs older than: {}";
    private static final String LOG_USER_FOUND = "User found: {}, ID: {}";
    private static final String LOG_NO_USER = "No user found for email: {}. Creating basic log entry.";
    private static final String LOG_SAVE_SUCCESS = "Successfully saved authentication log with ID: {}";

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationLogService.class);
    private final AuthenticationLogRepository authenticationLogRepository;
    private final UserService userService;

    public AuthenticationLogService(AuthenticationLogRepository authenticationLogRepository, UserService userService) {
        this.authenticationLogRepository = authenticationLogRepository;
        this.userService = userService;
        logger.info(LOG_SERVICE_INIT);
    }

    /**
     * Log a successful authentication
     * 
     * @param email     The user's email
     * @param ipAddress The IP address of the request
     * @param userAgent The user agent string
     * @return The created AuthenticationLog
     */
    @Transactional
    public AuthenticationLog logSuccessfulAuthentication(String email, String ipAddress, String userAgent) {
        logger.info(LOG_SUCCESS_AUTH, email, ipAddress);

        try {
            Optional<User> userOpt = userService.getUserByEmail(email);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.info(LOG_USER_FOUND, user.getEmail(), user.getId());

                AuthenticationLog log = new AuthenticationLog(
                        email,
                        user.getId(),
                        user.getPrimaryDomain(),
                        user.getCustomerGoogleId(),
                        ipAddress,
                        userAgent);

                AuthenticationLog savedLog = authenticationLogRepository.save(log);
                logger.info(LOG_SAVE_SUCCESS, savedLog.getId());
                return savedLog;
            } else {
                logger.info(LOG_NO_USER, email);

                AuthenticationLog log = new AuthenticationLog();
                log.setEmail(email);
                log.setIpAddress(ipAddress);
                log.setUserAgent(userAgent);
                log.setSuccessful(true);
                log.setTimestamp(LocalDateTime.now());

                AuthenticationLog savedLog = authenticationLogRepository.save(log);
                logger.info(LOG_SAVE_SUCCESS, savedLog.getId());
                return savedLog;
            }
        } catch (Exception e) {
            logger.error(LOG_ERROR_SUCCESS_AUTH, e);
            throw new RuntimeException("Failed to log successful authentication", e);
        }
    }

    /**
     * Log a failed authentication
     * 
     * @param email         The user's email (can be null)
     * @param ipAddress     The IP address of the request
     * @param userAgent     The user agent string
     * @param failureReason The reason for the authentication failure
     * @return The created AuthenticationLog
     */
    @Transactional
    public AuthenticationLog logFailedAuthentication(String email, String ipAddress, String userAgent,
            String failureReason) {
        logger.info(LOG_FAILED_AUTH, email, ipAddress, failureReason);

        try {
            AuthenticationLog log = new AuthenticationLog(email, ipAddress, userAgent, failureReason);
            AuthenticationLog savedLog = authenticationLogRepository.save(log);
            logger.info(LOG_SAVE_SUCCESS, savedLog.getId());
            return savedLog;
        } catch (Exception e) {
            logger.error(LOG_ERROR_FAILED_AUTH, e);
            throw new RuntimeException("Failed to log failed authentication", e);
        }
    }

    /**
     * Get all logs with pagination
     * 
     * @param pageable Pagination information
     * @return Page of authentication logs
     */
    public Page<AuthenticationLog> getLogsPaginated(Pageable pageable) {
        return authenticationLogRepository.findAll(pageable);
    }

    /**
     * Generic method to handle paginated and non-paginated queries
     * 
     * @param <T>      The return type (List or Page)
     * @param queryFn  Function to execute the query
     * @param errorMsg Error message in case of failure
     * @return Query results
     */
    private <T> T executeQuery(QueryFunction<T> queryFn, String errorMsg) {
        try {
            return queryFn.execute();
        } catch (Exception e) {
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    // Functional interface for query execution
    @FunctionalInterface
    private interface QueryFunction<T> {
        T execute();
    }

    /**
     * Get logs by user ID
     * 
     * @param userId The user ID
     * @return List of authentication logs for the user
     */
    public List<AuthenticationLog> getLogsByUserId(String userId) {
        return executeQuery(() -> authenticationLogRepository.findByUserId(userId),
                "Error fetching logs by user ID: " + userId);
    }

    /**
     * Get logs by user ID with pagination
     * 
     * @param userId   The user ID
     * @param pageable Pagination information
     * @return Page of authentication logs for the user
     */
    public Page<AuthenticationLog> getLogsByUserIdPaginated(String userId, Pageable pageable) {
        return executeQuery(() -> authenticationLogRepository.findByUserId(userId, pageable),
                "Error fetching paginated logs by user ID: " + userId);
    }

    /**
     * Get logs by email
     * 
     * @param email The email address
     * @return List of authentication logs for the email
     */
    public List<AuthenticationLog> getLogsByEmail(String email) {
        return executeQuery(() -> authenticationLogRepository.findByEmail(email),
                "Error fetching logs by email: " + email);
    }

    /**
     * Get logs by email with pagination
     * 
     * @param email    The email address
     * @param pageable Pagination information
     * @return Page of authentication logs for the email
     */
    public Page<AuthenticationLog> getLogsByEmailPaginated(String email, Pageable pageable) {
        return executeQuery(() -> authenticationLogRepository.findByEmail(email, pageable),
                "Error fetching paginated logs by email: " + email);
    }

    /**
     * Get logs by domain
     * 
     * @param domain The domain name
     * @return List of authentication logs for the domain
     */
    public List<AuthenticationLog> getLogsByDomain(String domain) {
        return executeQuery(() -> authenticationLogRepository.findByPrimaryDomain(domain),
                "Error fetching logs by domain: " + domain);
    }

    /**
     * Get logs by domain with pagination
     * 
     * @param domain   The domain name
     * @param pageable Pagination information
     * @return Page of authentication logs for the domain
     */
    public Page<AuthenticationLog> getLogsByDomainPaginated(String domain, Pageable pageable) {
        return executeQuery(() -> authenticationLogRepository.findByPrimaryDomain(domain, pageable),
                "Error fetching paginated logs by domain: " + domain);
    }

    /**
     * Get logs by success status
     * 
     * @param successful Whether the authentication was successful
     * @return List of authentication logs with the specified success status
     */
    public List<AuthenticationLog> getLogsBySuccessStatus(boolean successful) {
        return executeQuery(() -> authenticationLogRepository.findBySuccessful(successful),
                "Error fetching logs by success status: " + successful);
    }

    /**
     * Get logs by success status with pagination
     * 
     * @param successful Whether the authentication was successful
     * @param pageable   Pagination information
     * @return Page of authentication logs with the specified success status
     */
    public Page<AuthenticationLog> getLogsBySuccessStatusPaginated(boolean successful, Pageable pageable) {
        return executeQuery(() -> authenticationLogRepository.findBySuccessful(successful, pageable),
                "Error fetching paginated logs by success status: " + successful);
    }

    /**
     * Get logs by IP address
     * 
     * @param ipAddress The IP address
     * @return List of authentication logs from the specified IP address
     */
    public List<AuthenticationLog> getLogsByIpAddress(String ipAddress) {
        return executeQuery(() -> authenticationLogRepository.findByIpAddress(ipAddress),
                "Error fetching logs by IP address: " + ipAddress);
    }

    /**
     * Get logs by IP address with pagination
     * 
     * @param ipAddress The IP address
     * @param pageable  Pagination information
     * @return Page of authentication logs from the specified IP address
     */
    public Page<AuthenticationLog> getLogsByIpAddressPaginated(String ipAddress, Pageable pageable) {
        return executeQuery(() -> authenticationLogRepository.findByIpAddress(ipAddress, pageable),
                "Error fetching paginated logs by IP address: " + ipAddress);
    }

    /**
     * Get logs within a time range
     * 
     * @param start The start time
     * @param end   The end time
     * @return List of authentication logs within the time range
     */
    public List<AuthenticationLog> getLogsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return executeQuery(() -> authenticationLogRepository.findByTimestampBetween(start, end),
                "Error fetching logs by time range");
    }

    /**
     * Get logs within a time range with pagination
     * 
     * @param start    The start time
     * @param end      The end time
     * @param pageable Pagination information
     * @return Page of authentication logs within the time range
     */
    public Page<AuthenticationLog> getLogsByTimeRangePaginated(LocalDateTime start, LocalDateTime end,
            Pageable pageable) {
        return executeQuery(() -> authenticationLogRepository.findByTimestampBetween(start, end, pageable),
                "Error fetching paginated logs by time range");
    }

    /**
     * Delete logs older than a specified date
     * 
     * @param cutoffDate The cutoff date
     */
    @Transactional
    public void deleteLogsOlderThan(LocalDateTime cutoffDate) {
        logger.info(LOG_DELETE_OLD, cutoffDate);
        try {
            // More efficient to use bulk delete than loading all logs into memory
            List<AuthenticationLog> oldLogs = authenticationLogRepository.findByTimestampBetween(
                    LocalDateTime.MIN, cutoffDate);
            authenticationLogRepository.deleteAll(oldLogs);
            logger.info("Deleted {} logs older than {}", oldLogs.size(), cutoffDate);
        } catch (Exception e) {
            logger.error("Error deleting logs older than " + cutoffDate, e);
            throw new RuntimeException("Failed to delete old logs", e);
        }
    }

    /**
     * Get logs with combined filtering and pagination
     * 
     * @param email      The email address (optional)
     * @param domain     The domain name (optional)
     * @param successful Whether the authentication was successful (optional)
     * @param startDate  The start date (optional)
     * @param endDate    The end date (optional)
     * @param pageable   Pagination information
     * @return Page of filtered authentication logs
     */
    public Page<AuthenticationLog> getFilteredLogs(
            String email,
            String domain,
            Boolean successful,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        logger.info(LOG_FILTERED_QUERY, email, domain, successful, startDate, endDate);

        try {
            // Handle all possible combinations of filters
            if (email != null && domain != null && successful != null && startDate != null && endDate != null) {
                return authenticationLogRepository.findByEmailAndPrimaryDomainAndSuccessfulAndTimestampBetween(
                        email, domain, successful, startDate, endDate, pageable);
            } else if (email != null && domain != null && successful != null) {
                return authenticationLogRepository.findByEmailAndPrimaryDomainAndSuccessful(
                        email, domain, successful, pageable);
            } else if (email != null && successful != null && startDate != null && endDate != null) {
                return authenticationLogRepository.findByEmailAndSuccessfulAndTimestampBetween(
                        email, successful, startDate, endDate, pageable);
            } else if (domain != null && successful != null && startDate != null && endDate != null) {
                return authenticationLogRepository.findByPrimaryDomainAndSuccessfulAndTimestampBetween(
                        domain, successful, startDate, endDate, pageable);
            } else if (email != null && domain != null) {
                return authenticationLogRepository.findByEmailAndPrimaryDomain(email, domain, pageable);
            } else if (email != null && successful != null) {
                return authenticationLogRepository.findByEmailAndSuccessful(email, successful, pageable);
            } else if (domain != null && successful != null) {
                return authenticationLogRepository.findByPrimaryDomainAndSuccessful(domain, successful, pageable);
            } else if (startDate != null && endDate != null) {
                return authenticationLogRepository.findByTimestampBetween(startDate, endDate, pageable);
            } else if (email != null) {
                return authenticationLogRepository.findByEmail(email, pageable);
            } else if (domain != null) {
                return authenticationLogRepository.findByPrimaryDomain(domain, pageable);
            } else if (successful != null) {
                return authenticationLogRepository.findBySuccessful(successful, pageable);
            } else {
                return authenticationLogRepository.findAll(pageable);
            }
        } catch (Exception e) {
            logger.error("Error executing filtered logs query", e);
            throw new RuntimeException("Failed to retrieve filtered logs", e);
        }
    }

    /**
     * Get the last successful login time for a user by ID
     * 
     * @param userId The user ID
     * @return The timestamp of the last successful login, or null if none exists
     */
    public LocalDateTime getLastSuccessfulLoginByUserId(String userId) {
        try {
            AuthenticationLog lastLogin = authenticationLogRepository
                    .findTopByUserIdAndSuccessfulOrderByTimestampDesc(userId, true);
            return lastLogin != null ? lastLogin.getTimestamp() : null;
        } catch (Exception e) {
            logger.error("Error fetching last successful login for user ID: " + userId, e);
            throw new RuntimeException("Failed to retrieve last login for user", e);
        }
    }

    /**
     * Get the last successful login time for a user by email
     * 
     * @param email The user's email
     * @return The timestamp of the last successful login, or null if none exists
     */
    public LocalDateTime getLastSuccessfulLoginByEmail(String email) {
        try {
            AuthenticationLog lastLogin = authenticationLogRepository
                    .findTopByEmailAndSuccessfulOrderByTimestampDesc(email, true);
            return lastLogin != null ? lastLogin.getTimestamp() : null;
        } catch (Exception e) {
            logger.error("Error fetching last successful login for email: " + email, e);
            throw new RuntimeException("Failed to retrieve last login for email", e);
        }
    }
}
