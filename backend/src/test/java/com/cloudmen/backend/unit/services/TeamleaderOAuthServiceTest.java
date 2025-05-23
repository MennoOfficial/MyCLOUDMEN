package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.config.TeamleaderApiConfig;
import com.cloudmen.backend.domain.models.OAuthToken;
import com.cloudmen.backend.repositories.OAuthTokenRepository;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

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
    private static final String AUTH_CODE = "authorization-code";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "https://example.com/callback";
    private static final String TOKEN_URL = "https://app.teamleader.eu/oauth2/access_token";
    private static final String AUTH_URL = "https://app.teamleader.eu/oauth2/authorize";
    private static final String PROVIDER_NAME = "teamleader";

    @Mock
    private TeamleaderApiConfig apiConfig;

    @Mock
    private WebClient webClient;

    @Mock
    private OAuthTokenRepository tokenRepository;

    @Mock
    private ObjectMapper objectMapper;

    // WebClient chain mocks
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Captor
    private ArgumentCaptor<OAuthToken> tokenCaptor;

    @Captor
    private ArgumentCaptor<BodyInserter<?, ?>> bodyInserterCaptor;

    private TeamleaderOAuthService oAuthService;

    @BeforeEach
    void setUp() {
        // Create service without retry to avoid NPEs in tests
        oAuthService = new TeamleaderOAuthService(
                apiConfig, webClient, tokenRepository, objectMapper, null);

        // Set up common mock chain
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(BodyInserter.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("mock response"));

        // Setup API config default values
        when(apiConfig.getClientId()).thenReturn(CLIENT_ID);
        when(apiConfig.getClientSecret()).thenReturn(CLIENT_SECRET);
        when(apiConfig.getRedirectUri()).thenReturn(REDIRECT_URI);
        when(apiConfig.getTokenUrl()).thenReturn(TOKEN_URL);
        when(apiConfig.getAuthUrl()).thenReturn(AUTH_URL);
    }

    @Test
    @DisplayName("getAuthorizationUrl should return correct URL")
    void getAuthorizationUrl_shouldReturnCorrectUrl() {
        // Act
        String result = oAuthService.getAuthorizationUrl();

        // Assert
        assertNotNull(result);
        assertTrue(result.startsWith(AUTH_URL));
        assertTrue(result.contains("client_id=" + CLIENT_ID));
        assertTrue(result.contains("redirect_uri=" + REDIRECT_URI));
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
        token.setId("token-id");
        token.setAccessToken("valid-token");
        token.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Not expired
        token.setProvider("teamleader");

        when(tokenRepository.findByProvider("teamleader")).thenReturn(Optional.of(token));
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(token);

        // Act
        String result = oAuthService.getAccessToken();

        // Assert
        assertEquals("valid-token", result);

        // Verify
        verify(tokenRepository).findByProvider("teamleader");
        verify(tokenRepository).save(tokenCaptor.capture());
        OAuthToken savedToken = tokenCaptor.getValue();
        assertEquals("valid-token", savedToken.getAccessToken());
        // Verify token was saved (markAsUsed was called)
        verify(tokenRepository).save(any(OAuthToken.class));
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
        when(tokenRepository.save(any(OAuthToken.class))).thenReturn(token);

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

    @Test
    @DisplayName("exchangeAuthorizationCode should return true when token exchange is successful")
    void exchangeAuthorizationCode_shouldReturnTrue_whenTokenExchangeSuccessful() throws Exception {
        // Arrange - prepare token response
        String tokenResponseJson = "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"refresh_token\":\""
                + REFRESH_TOKEN + "\",\"expires_in\":" + EXPIRES_IN + ",\"token_type\":\"bearer\"}";

        // Create real JsonNode instead of mock
        ObjectNode tokenResponse = JsonNodeFactory.instance.objectNode();
        tokenResponse.put("access_token", ACCESS_TOKEN);
        tokenResponse.put("refresh_token", REFRESH_TOKEN);
        tokenResponse.put("expires_in", EXPIRES_IN);
        tokenResponse.put("token_type", "bearer");

        // Setup the necessary mocks
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(tokenResponseJson));
        when(objectMapper.readTree(tokenResponseJson)).thenReturn(tokenResponse);

        // Mock the token repository
        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.empty());
        when(tokenRepository.save(any(OAuthToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        boolean result = oAuthService.exchangeAuthorizationCode(AUTH_CODE);

        // Assert
        assertTrue(result);

        // Verify token was saved with the correct values
        verify(tokenRepository).save(tokenCaptor.capture());
        OAuthToken savedToken = tokenCaptor.getValue();
        assertEquals(ACCESS_TOKEN, savedToken.getAccessToken());
        assertEquals(REFRESH_TOKEN, savedToken.getRefreshToken());
        assertEquals(PROVIDER_NAME, savedToken.getProvider());

        // Verify WebClient was called with right parameters
        verify(requestBodySpec).body(any(BodyInserter.class));
        verify(requestBodyUriSpec).uri(TOKEN_URL);
        verify(requestBodySpec).contentType(MediaType.APPLICATION_FORM_URLENCODED);
    }

    @Test
    @DisplayName("exchangeAuthorizationCode should return false when token exchange fails")
    void exchangeAuthorizationCode_shouldReturnFalse_whenTokenExchangeFails() throws Exception {
        // Arrange - create error response
        String errorResponseJson = "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid authorization code\"}";

        // Create real JsonNode instead of mock
        ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
        errorResponse.put("error", "invalid_grant");
        errorResponse.put("error_description", "Invalid authorization code");

        // Setup the necessary mocks
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(errorResponseJson));
        when(objectMapper.readTree(errorResponseJson)).thenReturn(errorResponse);

        // Act
        boolean result = oAuthService.exchangeAuthorizationCode(AUTH_CODE);

        // Assert
        assertFalse(result);
        verify(tokenRepository, never()).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("exchangeAuthorizationCode should return false when exception occurs")
    void exchangeAuthorizationCode_shouldReturnFalse_whenExceptionOccurs() throws Exception {
        // Arrange - setup exception
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(new RuntimeException("API Error")));

        // Act
        boolean result = oAuthService.exchangeAuthorizationCode(AUTH_CODE);

        // Assert
        assertFalse(result);
        verify(tokenRepository, never()).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("getAccessToken should refresh token when token is expired")
    void getAccessToken_shouldRefreshToken_whenTokenIsExpired() throws Exception {
        // Arrange - setup expired token
        OAuthToken expiredToken = new OAuthToken();
        expiredToken.setId("token-id");
        expiredToken.setAccessToken("expired-token");
        expiredToken.setRefreshToken(REFRESH_TOKEN);
        expiredToken.setProvider(PROVIDER_NAME);
        expiredToken.setAccessTokenExpiresAt(LocalDateTime.now().minusHours(1)); // Expired

        // Create refresh token response
        String refreshResponseJson = "{\"access_token\":\"new-token\",\"expires_in\":" + EXPIRES_IN
                + ",\"token_type\":\"bearer\"}";

        // Create real JsonNode for response
        ObjectNode refreshResponse = JsonNodeFactory.instance.objectNode();
        refreshResponse.put("access_token", "new-token");
        refreshResponse.put("expires_in", EXPIRES_IN);
        refreshResponse.put("token_type", "bearer");

        // Setup mocks
        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.of(expiredToken));
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(refreshResponseJson));
        when(objectMapper.readTree(refreshResponseJson)).thenReturn(refreshResponse);

        // Setup token repository to return the updated token
        when(tokenRepository.save(any(OAuthToken.class))).thenAnswer(i -> {
            OAuthToken token = i.getArgument(0);
            if (token.getAccessToken().equals("new-token")) {
                // The token after refresh
                return token;
            } else {
                // The token after markAsUsed
                token.setAccessToken("new-token");
                return token;
            }
        });

        // Act
        String result = oAuthService.getAccessToken();

        // Assert
        assertEquals("new-token", result);

        // Verify refresh token request was made
        verify(requestBodySpec).body(any(BodyInserter.class));
        verify(requestBodyUriSpec).uri(TOKEN_URL);

        // Verify token was saved twice (once for refresh, once for markAsUsed)
        verify(tokenRepository, times(2)).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("getAccessToken should return null when refresh fails")
    void getAccessToken_shouldReturnNull_whenRefreshFails() throws Exception {
        // Arrange - setup expired token
        OAuthToken expiredToken = new OAuthToken();
        expiredToken.setId("token-id");
        expiredToken.setAccessToken("expired-token");
        expiredToken.setRefreshToken(REFRESH_TOKEN);
        expiredToken.setProvider(PROVIDER_NAME);
        expiredToken.setAccessTokenExpiresAt(LocalDateTime.now().minusHours(1)); // Expired

        // Create error response JSON
        String errorResponseJson = "{\"error\":\"invalid_grant\",\"error_description\":\"Invalid refresh token\"}";

        // Create real JsonNode for response
        ObjectNode errorResponse = JsonNodeFactory.instance.objectNode();
        errorResponse.put("error", "invalid_grant");
        errorResponse.put("error_description", "Invalid refresh token");

        // Setup mocks
        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.of(expiredToken));
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(errorResponseJson));
        when(objectMapper.readTree(errorResponseJson)).thenReturn(errorResponse);

        // Act
        String result = oAuthService.getAccessToken();

        // Assert
        assertNull(result);

        // Verify refresh was attempted
        verify(requestBodySpec).body(any(BodyInserter.class));

        // Verify token was not updated
        verify(tokenRepository, never()).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("getTokenInfo should return token information")
    void getTokenInfo_shouldReturnTokenInformation() {
        // Arrange
        OAuthToken token = new OAuthToken();
        token.setId("token-id");
        token.setAccessToken(ACCESS_TOKEN);
        token.setRefreshToken(REFRESH_TOKEN);
        token.setProvider(PROVIDER_NAME);

        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.of(token));

        // Act
        OAuthToken result = oAuthService.getTokenInfo();

        // Assert
        assertNotNull(result);
        assertEquals(ACCESS_TOKEN, result.getAccessToken());
        assertEquals(REFRESH_TOKEN, result.getRefreshToken());
        assertEquals(PROVIDER_NAME, result.getProvider());
        assertEquals("token-id", result.getId());
    }

    @Test
    @DisplayName("getTokenInfo should return null when no token exists")
    void getTokenInfo_shouldReturnNull_whenNoTokenExists() {
        // Arrange
        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.empty());

        // Act
        OAuthToken result = oAuthService.getTokenInfo();

        // Assert
        assertNull(result);
        verify(tokenRepository).findByProvider(PROVIDER_NAME);
    }

    @Test
    @DisplayName("revokeToken should return true and delete token when token exists")
    void revokeToken_shouldReturnTrueAndDeleteToken_whenTokenExists() {
        // Arrange
        OAuthToken token = new OAuthToken();
        token.setId("token-id");
        token.setProvider(PROVIDER_NAME);

        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.of(token));

        // Act
        boolean result = oAuthService.revokeToken();

        // Assert
        assertTrue(result);
        verify(tokenRepository).delete(token);
    }

    @Test
    @DisplayName("revokeToken should return false when no token exists")
    void revokeToken_shouldReturnFalse_whenNoTokenExists() {
        // Arrange
        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.empty());

        // Act
        boolean result = oAuthService.revokeToken();

        // Assert
        assertFalse(result);
        verify(tokenRepository, never()).delete(any(OAuthToken.class));
    }

    @Test
    @DisplayName("getAccessToken should refresh token but preserve original refresh token if not in response")
    void getAccessToken_shouldRefreshTokenButPreserveOriginalRefreshToken() throws Exception {
        // Arrange - setup expired token
        OAuthToken expiredToken = new OAuthToken();
        expiredToken.setId("token-id");
        expiredToken.setAccessToken("expired-token");
        expiredToken.setRefreshToken(REFRESH_TOKEN);
        expiredToken.setProvider(PROVIDER_NAME);
        expiredToken.setAccessTokenExpiresAt(LocalDateTime.now().minusHours(1)); // Expired

        // Create refresh token response WITHOUT a refresh_token field
        String refreshResponseJson = "{\"access_token\":\"new-token\",\"expires_in\":" + EXPIRES_IN
                + ",\"token_type\":\"bearer\"}";

        // Create real JsonNode for response
        ObjectNode refreshResponse = JsonNodeFactory.instance.objectNode();
        refreshResponse.put("access_token", "new-token");
        refreshResponse.put("expires_in", EXPIRES_IN);
        refreshResponse.put("token_type", "bearer");
        // Intentionally no refresh_token field

        // Setup mocks
        when(tokenRepository.findByProvider(PROVIDER_NAME)).thenReturn(Optional.of(expiredToken));
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(refreshResponseJson));
        when(objectMapper.readTree(refreshResponseJson)).thenReturn(refreshResponse);

        // Setup token repository to capture the saved token
        when(tokenRepository.save(any(OAuthToken.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        String result = oAuthService.getAccessToken();

        // Assert - should get new access token
        assertEquals("new-token", result);

        // Verify token was saved with NEW access token but ORIGINAL refresh token
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());
        OAuthToken savedToken = tokenCaptor.getAllValues().get(0); // First save is token refresh
        assertEquals("new-token", savedToken.getAccessToken());
        assertEquals(REFRESH_TOKEN, savedToken.getRefreshToken()); // Original refresh token preserved
    }
}