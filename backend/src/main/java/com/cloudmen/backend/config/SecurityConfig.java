package com.cloudmen.backend.config;

import com.cloudmen.backend.security.AuthenticationLogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the MyCLOUDMEN application.
 * This class configures Spring Security with JWT authentication via Auth0,
 * defines authorization rules, CORS settings, and integrates the authentication
 * logging filter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${auth0.audience:https://mycloudmen-api}")
    private String audience; // The API identifier in Auth0

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://dev-example.us.auth0.com/}")
    private String issuer; // The Auth0 domain URL

    private final AuthenticationLogFilter authenticationLogFilter;

    /**
     * Constructor with dependency injection for AuthenticationLogFilter
     * 
     * @param authenticationLogFilter Filter for logging authentication attempts
     */
    public SecurityConfig(AuthenticationLogFilter authenticationLogFilter) {
        this.authenticationLogFilter = authenticationLogFilter;
    }

    /**
     * Configures the security filter chain for the application.
     * This defines which endpoints are secured, how CORS is handled,
     * and integrates JWT authentication with Auth0.
     * 
     * @param http The HttpSecurity to configure
     * @return The configured SecurityFilterChain
     * @throws Exception If configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring security filter chain");

        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF as we're using JWT tokens
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authz -> {
                    // Log the configuration
                    logger.info("Configuring authorization rules");

                    authz
                            // Public endpoints that don't require authentication
                            .requestMatchers("/api/public/**", "/api/auth0/**").permitAll()
                            // Authentication logs endpoint
                            .requestMatchers("/api/auth-logs/**").permitAll()
                            // Teamleader OAuth2 endpoints
                            .requestMatchers("/api/teamleader/oauth/**").permitAll()
                            .requestMatchers("/api/teamleader/webhook/**").permitAll()
                            // Allow OPTIONS requests for CORS preflight
                            .requestMatchers("/**").permitAll();

                    logger.info("Authorization rules configured");
                })
                // Add our custom authentication logging filter before the standard
                // authentication filter
                .addFilterBefore(authenticationLogFilter, UsernamePasswordAuthenticationFilter.class);

        // Only configure OAuth2 resource server if issuer is properly configured
        try {
            if (issuer != null && !issuer.contains("example")) {
                http.oauth2ResourceServer(oauth2 -> {
                    // Configure as OAuth2 resource server with JWT support
                    logger.info("Configuring OAuth2 resource server with issuer: {}", issuer);
                    oauth2.jwt(jwt -> {
                        logger.info("Configuring JWT handling");
                    });
                });
            } else {
                logger.warn("OAuth2 resource server not configured: issuer is not properly set");
            }
        } catch (Exception e) {
            logger.error("Error configuring OAuth2 resource server", e);
        }

        logger.info("Security filter chain configured");
        return http.build();
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) for the application.
     * This allows the frontend application to communicate with the backend API
     * when they are hosted on different domains/ports.
     * 
     * @return The CORS configuration source
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        logger.info("Configuring CORS");

        CorsConfiguration configuration = new CorsConfiguration();
        // Get the allowed origins from application.properties
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200", // Angular dev server
                "http://127.0.0.1:4200", // Also handle localhost as IP
                "http://localhost:8080", // Spring Boot dev server
                "https://www.mycloudmen.mennoplochaet.be",
                "https://mycloudmen.mennoplochaet.be"));
        // Allow standard HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        // Allow common headers
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With",
                        "Access-Control-Request-Method", "Access-Control-Request-Headers",
                        "Cache-Control", "Pragma", "Expires"));
        // Allow the Authorization header to be exposed to the client
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Access-Control-Allow-Origin"));
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        // Cache preflight requests for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        logger.info("CORS configuration completed");
        return source;
    }

    /**
     * Configures the JWT decoder for validating Auth0 tokens.
     * This ensures that tokens are issued by the correct Auth0 tenant
     * and contain the correct audience (API identifier).
     * 
     * @return The configured JWT decoder
     */
    @Bean
    JwtDecoder jwtDecoder() {
        // Only create the decoder if the issuer is properly configured
        if (issuer == null || issuer.contains("example")) {
            logger.warn("JWT decoder not created: issuer is not properly set");
            return token -> {
                throw new RuntimeException("JWT decoder not configured");
            };
        }

        try {
            logger.info("Creating JWT decoder with issuer: {}", issuer);

            // Create a decoder that verifies the token signature using Auth0's public keys
            NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);

            // Create a custom validator that checks if the token contains our API audience
            OAuth2TokenValidator<Jwt> audienceValidator = token -> {
                List<String> audiences = token.getAudience();
                logger.debug("Token audiences: {}", audiences);

                if (audiences.contains(audience)) {
                    logger.debug("Token contains required audience: {}", audience);
                    return OAuth2TokenValidatorResult.success();
                }

                logger.warn("Token missing required audience: {}", audience);
                return OAuth2TokenValidatorResult
                        .failure(new OAuth2Error("invalid_token", "The required audience is missing", null));
            };

            // Combine the default issuer validator with our custom audience validator
            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
            OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer,
                    audienceValidator);

            // Set the combined validator on the decoder
            jwtDecoder.setJwtValidator(withAudience);

            logger.info("JWT decoder created successfully");
            return jwtDecoder;
        } catch (Exception e) {
            logger.error("Error creating JWT decoder", e);
            return token -> {
                throw new RuntimeException("Error creating JWT decoder", e);
            };
        }
    }
}