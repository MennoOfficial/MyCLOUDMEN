package com.cloudmen.backend.integration.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Test configuration for integration tests.
 * Provides mock beans needed for testing.
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    /**
     * Provide a WebClient.Builder for testing
     */
    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Provide a JavaMailSender for testing to avoid actual email sending
     */
    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return new JavaMailSenderImpl();
    }
}