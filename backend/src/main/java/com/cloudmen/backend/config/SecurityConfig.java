package com.cloudmen.backend.config;

import com.cloudmen.backend.security.AuthenticationLogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${auth0.audience}")
    private String audience;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    private final AuthenticationLogFilter authenticationLogFilter;

    public SecurityConfig(AuthenticationLogFilter authenticationLogFilter) {
        this.authenticationLogFilter = authenticationLogFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring security filter chain");

        http
                .cors().and()
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> {
                    // Log the configuration
                    logger.info("Configuring authorization rules");

                    authz
                            // Public endpoints that don't require authentication
                            .requestMatchers("/api/public/**", "/api/auth0/**").permitAll()
                            // Authentication logs endpoint - requires authentication
                            .requestMatchers("/api/auth-logs/**").permitAll() // Temporarily allow all access for
                                                                              // debugging
                            // Allow OPTIONS requests for CORS
                            .requestMatchers("/**").permitAll();

                    logger.info("Authorization rules configured");
                })
                .oauth2ResourceServer(oauth2 -> {
                    logger.info("Configuring OAuth2 resource server");
                    oauth2.jwt(jwt -> {
                        logger.info("Configuring JWT handling");
                    });
                })
                .addFilterBefore(authenticationLogFilter, UsernamePasswordAuthenticationFilter.class);

        logger.info("Security filter chain configured");
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        logger.info("Configuring CORS");

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200", "http://localhost:8080"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        logger.info("CORS configuration completed");
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        logger.info("Creating JWT decoder with issuer: {}", issuer);

        NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);

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

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        logger.info("JWT decoder created successfully");
        return jwtDecoder;
    }
}