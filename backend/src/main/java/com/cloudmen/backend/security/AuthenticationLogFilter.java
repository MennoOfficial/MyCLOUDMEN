package com.cloudmen.backend.security;

import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.util.RequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Filter to log authentication attempts
 * This filter intercepts authentication requests and logs them
 */
@Component
@Order(1) // High priority to ensure it runs early in the filter chain
public class AuthenticationLogFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationLogFilter.class);
    private final AuthenticationLogService authenticationLogService;

    public AuthenticationLogFilter(AuthenticationLogService authenticationLogService) {
        this.authenticationLogService = authenticationLogService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Only log authentication attempts
        if (isAuthenticationRequest(request)) {
            String ipAddress = RequestUtils.getClientIpAddress(request);
            String userAgent = RequestUtils.getUserAgent(request);

            // Wrap the request to allow multiple reads of the input stream
            ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);

            // Wrap the response to capture the status
            AuthenticationResponseWrapper responseWrapper = new AuthenticationResponseWrapper(response);

            try {
                // Continue the filter chain with our wrapped response
                filterChain.doFilter(requestWrapper, responseWrapper);

                // After the request is processed, check if it was successful
                if (isSuccessfulAuthentication(responseWrapper)) {
                    // Extract email from the request
                    String email = extractEmailFromRequest(requestWrapper);
                    if (email != null) {
                        logger.info("Logging successful authentication in filter for: {}", email);
                        authenticationLogService.logSuccessfulAuthentication(
                                email, ipAddress, userAgent);
                    }
                } else {
                    // Log failed authentication
                    String email = extractEmailFromRequest(requestWrapper);
                    String failureReason = "Authentication failed with status: " + responseWrapper.getStatus();
                    logger.info("Logging failed authentication in filter for: {}", email);
                    authenticationLogService.logFailedAuthentication(
                            email, ipAddress, userAgent, failureReason);
                }
            } catch (Exception e) {
                // Log exception during authentication
                String email = extractEmailFromRequest(requestWrapper);
                logger.error("Exception in authentication filter", e);
                authenticationLogService.logFailedAuthentication(
                        email, ipAddress, userAgent, "Exception: " + e.getMessage());
                throw e;
            }
        } else {
            // Not an authentication request, continue the filter chain
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Check if the request is an authentication request
     * 
     * @param request The HTTP request
     * @return True if it's an authentication request, false otherwise
     */
    private boolean isAuthenticationRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        logger.info("Checking request: " + method + " " + path);

        // Skip auth-logs endpoints to avoid double logging
        if (path.contains("/api/auth-logs")) {
            logger.info("Skipping auth-logs endpoint: " + path);
            return false;
        }

        // Skip direct calls to our auth0 logging endpoints to avoid double logging
        if (path.contains("/api/auth0/log-authentication") || path.contains("/auth0/log-authentication")) {
            logger.info("Skipping explicit auth logging endpoint: " + path);
            return false;
        }

        // Only log specific authentication endpoints
        boolean isAuthEndpoint =
                // OAuth token endpoint
                (path.contains("/oauth/token")) ||
                // Auth0 callback
                        (path.contains("/callback") && path.contains("/auth")) ||
                        // Login endpoint
                        (path.contains("/login") && !path.contains("/api"));

        if (isAuthEndpoint) {
            logger.info("Request identified as authentication request: " + method + " " + path);
            return true;
        }

        return false;
    }

    /**
     * Check if the authentication was successful based on the response
     * 
     * @param response The HTTP response
     * @return True if authentication was successful, false otherwise
     */
    private boolean isSuccessfulAuthentication(HttpServletResponse response) {
        int status = response.getStatus();
        return status >= 200 && status < 300;
    }

    /**
     * Extract email from the authentication request
     * 
     * @param request The HTTP request
     * @return The email if found, null otherwise
     */
    private String extractEmailFromRequest(HttpServletRequest request) {
        // Try to get email from request parameters
        String email = request.getParameter("username");
        if (email == null) {
            email = request.getParameter("email");
        }

        // If not found in parameters, try to get from headers
        if (email == null) {
            email = request.getHeader("X-Auth-Email");
        }

        // Try to get from Auth0 specific headers or parameters
        if (email == null) {
            email = request.getHeader("Auth0-Email");
        }

        // Don't try to read the body - it may have been consumed already
        // Instead, rely on the Auth0Controller to log the authentication

        return email;
    }
}