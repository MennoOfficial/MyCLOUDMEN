package com.cloudmen.backend.api.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/api/proxy")
@CrossOrigin(origins = "*")
public class ProxyController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);
    private final WebClient webClient;

    public ProxyController(WebClient webClient) {
        this.webClient = webClient;
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String imageUrl) {
        try {
            // Validate URL
            URI uri = new URI(imageUrl);

            // Get image bytes from remote server using WebClient
            byte[] imageData = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .doOnError(e -> {
                        if (e instanceof WebClientResponseException) {
                            WebClientResponseException ex = (WebClientResponseException) e;
                            logger.error("Error fetching image from URL {}: HTTP {}", imageUrl, ex.getStatusCode());
                        }
                    })
                    .block();

            if (imageData != null) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(imageData);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (URISyntaxException e) {
            logger.error("Invalid URL format: {}", imageUrl);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            logger.error("Error proxying image from URL {}: {}", imageUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}