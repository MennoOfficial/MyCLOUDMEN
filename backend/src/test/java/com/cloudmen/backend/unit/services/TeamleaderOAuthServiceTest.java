package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
import com.cloudmen.backend.domain.models.OAuthToken;
import com.cloudmen.backend.repositories.OAuthTokenRepository;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TeamleaderOAuthService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TeamleaderOAuthService Tests")
class TeamleaderOAuthServiceTest {

    private static final String ACCESS_TOKEN = "test-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final int EXPIRES_IN = 7200;

    @Mock
    private TeamleaderApiConfig apiConfig;

    @Mock
    private WebClient webClient;

    @Mock
    private OAuthTokenRepository tokenRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Retry webClientRetrySpec;

    // WebClient chain mocks
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TeamleaderOAuthService oAuthService;

    @BeforeEach
    void setUp() {
        oAuthService = new TeamleaderOAuthService(
                apiConfig, webClient, tokenRepository, objectMapper, webClientRetrySpec);

        // Set up common mock chain
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(BodyInserter.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("mock response"));
    }

    @Test
    @DisplayName("getAuthorizationUrl should return correct URL")
    void getAuthorizationUrl_shouldReturnCorrectUrl() {
        // Arrange
        String authUrl = "https://app.teamleader.eu/oauth2/authorize";
        String clientId = "test-client-id";
        String redirectUri = "https://example.com/callback";

        when(apiConfig.getAuthUrl()).thenReturn(authUrl);
        when(apiConfig.getClientId()).thenReturn(clientId);
        when(apiConfig.getRedirectUri()).thenReturn(redirectUri);

        // Act
        String result = oAuthService.getAuthorizationUrl();

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith(authUrl));
        assertTrue(result.contains("client_id=" + clientId));
        assertTrue(result.contains("redirect_uri=" + redirectUri));
        assertTrue(result.contains("response_type=code"));
        assertTrue(result.contains("state="));

        // Verify
        verify(apiConfig).getAuthUrl();
        verify(apiConfig).getClientId();
        verify(apiConfig).getRedirectUri();
    }

    @Test
    @DisplayName("getAccessToken should return valid token")
    void getAccessToken_shouldReturnValidToken() {
        // Arrange
        OAuthToken token = new OAuthToken();
        token.setAccessToken("valid-token");
        token.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Not expired
        token.setProvider("teamleader");

        when(tokenRepository.findByProvider("teamleader")).thenReturn(Optional.of(token));

        // Act
        String result = oAuthService.getAccessToken();

        // Assert
        assertEquals("valid-token", result);
        verify(tokenRepository).findByProvider("teamleader");
        verify(tokenRepository).save(token); // Verifying that markAsUsed saved the token
    }

    @Test
    @DisplayName("getAccessToken should return null when no token exists")
    void getAccessToken_shouldReturnNull_whenNoTokenExists() {
        // Arrange
        when(tokenRepository.findByProvider("teamleader")).thenReturn(Optional.empty());

        // Act
        String result = oAuthService.getAccessToken();

        // Assert
        assertNull(result);
        verify(tokenRepository).findByProvider("teamleader");
        verify(tokenRepository, never()).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("hasValidToken should return true when valid token exists")
    void hasValidToken_shouldReturnTrue_whenValidTokenExists() {
        // Arrange
        OAuthToken token = new OAuthToken();
        token.setAccessToken("valid-token");
        token.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Not expired
        token.setProvider("teamleader");

        when(tokenRepository.findByProvider("teamleader")).thenReturn(Optional.of(token));

        // Act
        boolean result = oAuthService.hasValidToken();

        // Assert
        assertTrue(result);
        verify(tokenRepository).findByProvider("teamleader");
    }

    @Test
    @DisplayName("hasValidToken should return false when no token exists")
    void hasValidToken_shouldReturnFalse_whenNoTokenExists() {
        // Arrange
        when(tokenRepository.findByProvider("teamleader")).thenReturn(Optional.empty());

        // Act
        boolean result = oAuthService.hasValidToken();

        // Assert
        assertFalse(result);
        verify(tokenRepository).findByProvider("teamleader");
    }

    /**
     * Create a mock token response with proper stubbing
     * 
     * @return A mocked JsonNode with the token response data
     */
    private JsonNode createMockTokenResponse() {
        // Create mock with lenient settings
        JsonNode tokenResponse = Mockito.mock(JsonNode.class, Mockito.withSettings().lenient());

        // Create child nodes with lenient settings
        JsonNode accessTokenNode = createTextNode(ACCESS_TOKEN);
        JsonNode refreshTokenNode = createTextNode(REFRESH_TOKEN);
        JsonNode expiresInNode = createNumberNode(EXPIRES_IN);
        JsonNode tokenTypeNode = createTextNode("bearer");

        // Add common has() method stubs
        when(tokenResponse.has("access_token")).thenReturn(true);
        when(tokenResponse.has("refresh_token")).thenReturn(true);
        when(tokenResponse.has("expires_in")).thenReturn(true);
        when(tokenResponse.has("token_type")).thenReturn(true);
        when(tokenResponse.has("error")).thenReturn(false);

        // Add the get() stubs
        when(tokenResponse.get("access_token")).thenReturn(accessTokenNode);
        when(tokenResponse.get("refresh_token")).thenReturn(refreshTokenNode);
        when(tokenResponse.get("expires_in")).thenReturn(expiresInNode);
        when(tokenResponse.get("token_type")).thenReturn(tokenTypeNode);

        return tokenResponse;
    }

    private JsonNode createTextNode(String value) {
        JsonNode textNode = Mockito.mock(JsonNode.class, Mockito.withSettings().lenient());
        when(textNode.asText()).thenReturn(value);
        return textNode;
    }

    private JsonNode createNumberNode(int value) {
        JsonNode numberNode = Mockito.mock(JsonNode.class, Mockito.withSettings().lenient());
        when(numberNode.asInt()).thenReturn(value);
        return numberNode;
    }
}