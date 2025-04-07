package com.cloudmen.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient to connect to TeamLeader API.
 * Sets up a WebClient bean with the appropriate base URL and memory settings.
 */
@Configuration
public class WebClientConfig {

    @Value("${teamleader.api.base-url:https://api.focus.teamleader.eu}")
    private String teamleaderApiBaseUrl;

    /**
     * Creates a WebClient bean configured for TeamLeader API.
     * 
     * @return A configured WebClient instance
     */
    @Bean
    public WebClient webClient() {
        // Configure memory limit for larger responses
        final int size = 16 * 1024 * 1024; // 16MB buffer size
        final ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(size))
                .build();

        return WebClient.builder()
                .baseUrl(teamleaderApiBaseUrl)
                .exchangeStrategies(strategies)
                .build();
    }
}