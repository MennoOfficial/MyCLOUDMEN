package com.cloudmen.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Configuration for WebClient to connect to TeamLeader API.
 */
@Configuration
public class WebClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);

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

        // Configure HttpClient with timeouts
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
                .baseUrl(teamleaderApiBaseUrl)
                .exchangeStrategies(strategies)
                .filter(logRequest())
                .filter(logResponse())
                .filter(handleErrors())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Log request details for debugging
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            logger.debug("WebClient Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach(
                    (name, values) -> values.forEach(value -> logger.trace("WebClient Header: {}={}", name, value)));
            return Mono.just(clientRequest);
        });
    }

    /**
     * Log response details for debugging
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            logger.debug("WebClient Response: Status {}", clientResponse.statusCode());
            return Mono.just(clientResponse);
        });
    }

    /**
     * Handle error responses
     */
    private ExchangeFilterFunction handleErrors() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                return handleErrorResponse(response);
            }
            return Mono.just(response);
        });
    }

    /**
     * Process error responses with detailed logging
     */
    private Mono<ClientResponse> handleErrorResponse(ClientResponse response) {
        org.springframework.http.HttpStatusCode statusCode = response.statusCode();

        if (statusCode.is5xxServerError()) {
            logger.error("WebClient received server error: {}", statusCode.value());
        } else if (statusCode.is4xxClientError()) {
            logger.warn("WebClient received client error: {}", statusCode.value());
        }

        return response.bodyToMono(String.class)
                .defaultIfEmpty("No response body")
                .flatMap(body -> {
                    logger.debug("Error response body: {}", body);
                    return Mono.just(response);
                });
    }

    /**
     * Create a retry specification for WebClient operations
     * Use with .retryWhen(webClientRetrySpec())
     * 
     * @return Retry specification with backoff
     */
    @Bean
    public Retry webClientRetrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(1))
                .filter(throwable -> {
                    // Only retry on server errors or network issues
                    logger.debug("Evaluating for retry: {}", throwable.getMessage());
                    return !(throwable instanceof org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest);
                })
                .doBeforeRetry(retrySignal -> logger.warn("Retrying request after failure. Attempt: {}",
                        retrySignal.totalRetries() + 1));
    }
}