package com.cloudmen.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import java.time.Duration;

/**
 * Configuration for WebClient to connect to TeamLeader API.
 * Sets up a WebClient bean with the appropriate base URL, memory settings, and
 * connection configuration.
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

        // Configure connection provider with custom settings
        ConnectionProvider provider = ConnectionProvider.builder("teamleader")
                .maxConnections(500)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(60))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        // Configure HttpClient with timeouts and DNS settings
        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(30))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(30))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(30)));

        return WebClient.builder()
                .baseUrl(teamleaderApiBaseUrl)
                .exchangeStrategies(strategies)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}