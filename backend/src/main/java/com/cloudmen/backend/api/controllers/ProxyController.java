package com.cloudmen.backend.api.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/api/proxy")
@CrossOrigin(origins = "*")
public class ProxyController {

    private final RestTemplate restTemplate;

    public ProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String imageUrl) {
        try {
            // Validate URL
            URI uri = new URI(imageUrl);

            // Get image bytes from remote server
            ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);

            // Return the image data with appropriate content type
            return ResponseEntity.ok()
                    .contentType(response.getHeaders().getContentType())
                    .body(response.getBody());
        } catch (URISyntaxException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}