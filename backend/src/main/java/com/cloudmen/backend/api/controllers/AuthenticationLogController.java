package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.services.AuthenticationLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth-logs")
public class AuthenticationLogController {

    private final AuthenticationLogService authenticationLogService;

    public AuthenticationLogController(AuthenticationLogService authenticationLogService) {
        this.authenticationLogService = authenticationLogService;
    }

    /**
     * Get authentication logs with pagination and optional filtering
     * 
     * @param page       Page number (0-based)
     * @param size       Page size
     * @param email      Filter by email (optional)
     * @param domain     Filter by domain (optional)
     * @param successful Filter by success status (optional)
     * @param startDate  Filter by start date (optional)
     * @param endDate    Filter by end date (optional)
     * @return Paginated list of authentication logs
     */
    @GetMapping
    public ResponseEntity<Page<AuthenticationLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) Boolean successful,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());

        // If no filters are provided, return all logs
        if (email == null && domain == null && successful == null && startDate == null && endDate == null) {
            return ResponseEntity.ok(authenticationLogService.getLogsPaginated(pageRequest));
        }

        // Otherwise, use the filtered search
        return ResponseEntity.ok(authenticationLogService.getFilteredLogs(
                email, domain, successful, startDate, endDate, pageRequest));
    }

    /**
     * Get authentication logs for a specific user with pagination
     * 
     * @param userId User ID
     * @param page   Page number (0-based)
     * @param size   Page size
     * @return Paginated list of authentication logs for the user
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AuthenticationLog>> getLogsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(authenticationLogService.getLogsByUserIdPaginated(userId, pageRequest));
    }

    /**
     * Get authentication logs for a specific email with pagination
     * 
     * @param email Email address
     * @param page  Page number (0-based)
     * @param size  Page size
     * @return Paginated list of authentication logs for the email
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<Page<AuthenticationLog>> getLogsByEmail(
            @PathVariable String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(authenticationLogService.getLogsByEmailPaginated(email, pageRequest));
    }

    /**
     * Get authentication logs for a specific domain with pagination
     * 
     * @param domain Domain name
     * @param page   Page number (0-based)
     * @param size   Page size
     * @return Paginated list of authentication logs for the domain
     */
    @GetMapping("/domain/{domain}")
    public ResponseEntity<Page<AuthenticationLog>> getLogsByDomain(
            @PathVariable String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(authenticationLogService.getLogsByDomainPaginated(domain, pageRequest));
    }

    /**
     * Get authentication logs by success status with pagination
     * 
     * @param successful Whether the authentication was successful
     * @param page       Page number (0-based)
     * @param size       Page size
     * @return Paginated list of authentication logs with the specified success
     *         status
     */
    @GetMapping("/status")
    public ResponseEntity<Page<AuthenticationLog>> getLogsBySuccessStatus(
            @RequestParam boolean successful,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(authenticationLogService.getLogsBySuccessStatusPaginated(successful, pageRequest));
    }

    /**
     * Get authentication logs by IP address with pagination
     * 
     * @param ipAddress IP address
     * @param page      Page number (0-based)
     * @param size      Page size
     * @return Paginated list of authentication logs from the specified IP address
     */
    @GetMapping("/ip/{ipAddress}")
    public ResponseEntity<Page<AuthenticationLog>> getLogsByIpAddress(
            @PathVariable String ipAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(authenticationLogService.getLogsByIpAddressPaginated(ipAddress, pageRequest));
    }

    /**
     * Get authentication logs for a specific time range with pagination
     * 
     * @param start Start time
     * @param end   End time
     * @param page  Page number (0-based)
     * @param size  Page size
     * @return Paginated list of authentication logs within the time range
     */
    @GetMapping("/timerange")
    public ResponseEntity<Page<AuthenticationLog>> getLogsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(authenticationLogService.getLogsByTimeRangePaginated(start, end, pageRequest));
    }

    /**
     * Delete logs older than a specified date
     * 
     * @param cutoffDate The cutoff date
     * @return Response indicating success
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Void> deleteOldLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cutoffDate) {
        authenticationLogService.deleteLogsOlderThan(cutoffDate);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get the last successful login time for a user by ID
     * 
     * @param userId User ID
     * @return The timestamp of the last successful login
     */
    @GetMapping("/user/{userId}/last-login")
    public ResponseEntity<LocalDateTime> getLastLoginByUserId(@PathVariable String userId) {
        LocalDateTime lastLogin = authenticationLogService.getLastSuccessfulLoginByUserId(userId);
        return ResponseEntity.ok(lastLogin);
    }

    /**
     * Get the last successful login time for a user by email
     * 
     * @param email User's email
     * @return The timestamp of the last successful login
     */
    @GetMapping("/email/{email}/last-login")
    public ResponseEntity<LocalDateTime> getLastLoginByEmail(@PathVariable String email) {
        LocalDateTime lastLogin = authenticationLogService.getLastSuccessfulLoginByEmail(email);
        return ResponseEntity.ok(lastLogin);
    }
}