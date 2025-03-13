package com.cloudmen.backend.services;

import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.AuthenticationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AuthenticationLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationLogService.class);
    private final AuthenticationLogRepository authenticationLogRepository;
    private final UserService userService;

    public AuthenticationLogService(AuthenticationLogRepository authenticationLogRepository, UserService userService) {
        this.authenticationLogRepository = authenticationLogRepository;
        this.userService = userService;
        logger.info("AuthenticationLogService initialized");
    }

    /**
     * Log a successful authentication
     * 
     * @param email     The user's email
     * @param ipAddress The IP address of the request
     * @param userAgent The user agent string
     * @return The created AuthenticationLog
     */
    public AuthenticationLog logSuccessfulAuthentication(String email, String ipAddress, String userAgent) {
        logger.info("Logging successful authentication for email: {}, IP: {}", email, ipAddress);

        try {
            Optional<User> userOpt = userService.getUserByEmail(email);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.info("User found: {}, ID: {}", user.getEmail(), user.getId());

                AuthenticationLog log = new AuthenticationLog(
                        email,
                        user.getId(),
                        user.getPrimaryDomain(),
                        user.getCustomerGoogleId(),
                        ipAddress,
                        userAgent);

                AuthenticationLog savedLog = authenticationLogRepository.save(log);
                logger.info("Successfully saved authentication log with ID: {}", savedLog.getId());
                return savedLog;
            } else {
                // This is a special case where we have a successful auth but no user record yet
                // This could happen during the first login before user creation
                logger.info("No user found for email: {}. Creating basic log entry.", email);

                AuthenticationLog log = new AuthenticationLog();
                log.setEmail(email);
                log.setIpAddress(ipAddress);
                log.setUserAgent(userAgent);
                log.setSuccessful(true);
                log.setTimestamp(LocalDateTime.now());

                AuthenticationLog savedLog = authenticationLogRepository.save(log);
                logger.info("Successfully saved basic authentication log with ID: {}", savedLog.getId());
                return savedLog;
            }
        } catch (Exception e) {
            logger.error("Error logging successful authentication", e);
            throw e;
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
    public AuthenticationLog logFailedAuthentication(String email, String ipAddress, String userAgent,
            String failureReason) {
        logger.info("Logging failed authentication for email: {}, IP: {}, Reason: {}",
                email, ipAddress, failureReason);

        try {
            AuthenticationLog log = new AuthenticationLog(email, ipAddress, userAgent, failureReason);
            AuthenticationLog savedLog = authenticationLogRepository.save(log);
            logger.info("Successfully saved failed authentication log with ID: {}", savedLog.getId());
            return savedLog;
        } catch (Exception e) {
            logger.error("Error logging failed authentication", e);
            throw e;
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
     * Get logs by user ID
     * 
     * @param userId The user ID
     * @return List of authentication logs for the user
     */
    public List<AuthenticationLog> getLogsByUserId(String userId) {
        return authenticationLogRepository.findByUserId(userId);
    }

    /**
     * Get logs by user ID with pagination
     * 
     * @param userId   The user ID
     * @param pageable Pagination information
     * @return Page of authentication logs for the user
     */
    public Page<AuthenticationLog> getLogsByUserIdPaginated(String userId, Pageable pageable) {
        return authenticationLogRepository.findByUserId(userId, pageable);
    }

    /**
     * Get logs by email
     * 
     * @param email The email address
     * @return List of authentication logs for the email
     */
    public List<AuthenticationLog> getLogsByEmail(String email) {
        return authenticationLogRepository.findByEmail(email);
    }

    /**
     * Get logs by email with pagination
     * 
     * @param email    The email address
     * @param pageable Pagination information
     * @return Page of authentication logs for the email
     */
    public Page<AuthenticationLog> getLogsByEmailPaginated(String email, Pageable pageable) {
        return authenticationLogRepository.findByEmail(email, pageable);
    }

    /**
     * Get logs by domain
     * 
     * @param domain The domain name
     * @return List of authentication logs for the domain
     */
    public List<AuthenticationLog> getLogsByDomain(String domain) {
        return authenticationLogRepository.findByPrimaryDomain(domain);
    }

    /**
     * Get logs by domain with pagination
     * 
     * @param domain   The domain name
     * @param pageable Pagination information
     * @return Page of authentication logs for the domain
     */
    public Page<AuthenticationLog> getLogsByDomainPaginated(String domain, Pageable pageable) {
        return authenticationLogRepository.findByPrimaryDomain(domain, pageable);
    }

    /**
     * Get logs by success status
     * 
     * @param successful Whether the authentication was successful
     * @return List of authentication logs with the specified success status
     */
    public List<AuthenticationLog> getLogsBySuccessStatus(boolean successful) {
        return authenticationLogRepository.findBySuccessful(successful);
    }

    /**
     * Get logs by success status with pagination
     * 
     * @param successful Whether the authentication was successful
     * @param pageable   Pagination information
     * @return Page of authentication logs with the specified success status
     */
    public Page<AuthenticationLog> getLogsBySuccessStatusPaginated(boolean successful, Pageable pageable) {
        return authenticationLogRepository.findBySuccessful(successful, pageable);
    }

    /**
     * Get logs by IP address
     * 
     * @param ipAddress The IP address
     * @return List of authentication logs from the specified IP address
     */
    public List<AuthenticationLog> getLogsByIpAddress(String ipAddress) {
        return authenticationLogRepository.findByIpAddress(ipAddress);
    }

    /**
     * Get logs by IP address with pagination
     * 
     * @param ipAddress The IP address
     * @param pageable  Pagination information
     * @return Page of authentication logs from the specified IP address
     */
    public Page<AuthenticationLog> getLogsByIpAddressPaginated(String ipAddress, Pageable pageable) {
        return authenticationLogRepository.findByIpAddress(ipAddress, pageable);
    }

    /**
     * Get logs within a time range
     * 
     * @param start The start time
     * @param end   The end time
     * @return List of authentication logs within the time range
     */
    public List<AuthenticationLog> getLogsByTimeRange(LocalDateTime start, LocalDateTime end) {
        return authenticationLogRepository.findByTimestampBetween(start, end);
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
        return authenticationLogRepository.findByTimestampBetween(start, end, pageable);
    }

    /**
     * Delete logs older than a specified date
     * 
     * @param cutoffDate The cutoff date
     */
    public void deleteLogsOlderThan(LocalDateTime cutoffDate) {
        List<AuthenticationLog> oldLogs = authenticationLogRepository.findByTimestampBetween(
                LocalDateTime.MIN, cutoffDate);
        authenticationLogRepository.deleteAll(oldLogs);
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

        logger.info("Getting filtered logs - Email: {}, Domain: {}, Successful: {}, StartDate: {}, EndDate: {}",
                email, domain, successful, startDate, endDate);

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
    }
}
