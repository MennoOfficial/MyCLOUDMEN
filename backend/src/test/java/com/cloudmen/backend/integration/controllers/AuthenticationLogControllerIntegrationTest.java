package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.AuthenticationLogController;
import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for AuthenticationLogController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationLogController Integration Tests")
public class AuthenticationLogControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private AuthenticationLogService authenticationLogService;

    // Create a custom module for Page deserialization
    public static class PageJacksonModule extends SimpleModule {
        public PageJacksonModule() {
            super("PageModule");
            addDeserializer(Page.class, new JsonDeserializer<Page<?>>() {
                @Override
                public Page<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    ObjectMapper mapper = (ObjectMapper) p.getCodec();
                    JsonNode node = mapper.readTree(p);
                    JsonNode contentNode = node.get("content");
                    List<?> content = contentNode != null
                            ? mapper.convertValue(contentNode, new TypeReference<List<AuthenticationLog>>() {
                            })
                            : new ArrayList<>();
                    int totalElements = node.has("totalElements") ? node.get("totalElements").asInt() : content.size();
                    int pageNumber = node.has("number") ? node.get("number").asInt() : 0;
                    int pageSize = node.has("size") ? node.get("size").asInt() : content.size();

                    return new PageImpl<>(content, PageRequest.of(pageNumber, pageSize), totalElements);
                }
            });
        }
    }

    // Use a real ObjectMapper with JavaTimeModule for date handling
    // And our custom Page deserializer
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new PageJacksonModule());

    // Create controller directly
    private AuthenticationLogController authenticationLogController;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test
        authenticationLogController = new AuthenticationLogController(authenticationLogService);

        // Create standalone MockMvc
        mockMvc = MockMvcBuilders
                .standaloneSetup(authenticationLogController)
                .build();
    }

    @Test
    @DisplayName("GET /api/auth-logs - Returns all logs without filters")
    void getLogs_ReturnsAllLogs_WithoutFilters() throws Exception {
        // Arrange
        AuthenticationLog log1 = createSuccessfulAuthLog("user1@example.com", "user1");
        AuthenticationLog log2 = createFailedAuthLog("user2@example.com", "Invalid password");

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsPaginated(any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());

        // Verify
        verify(authenticationLogService).getLogsPaginated(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs - Returns filtered logs with multiple filters")
    void getLogs_ReturnsFilteredLogs_WithFilters() throws Exception {
        // Arrange
        String email = "user@example.com";
        String domain = "example.com";
        boolean successful = true;
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();

        AuthenticationLog log = createSuccessfulAuthLog(email, "userId");
        List<AuthenticationLog> logs = Arrays.asList(log);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 1);

        when(authenticationLogService.getFilteredLogs(
                eq(email), eq(domain), eq(successful),
                eq(startDate), eq(endDate), any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs")
                .param("email", email)
                .param("domain", domain)
                .param("successful", "true")
                .param("startDate", startDate.toString())
                .param("endDate", endDate.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(1, responseLogList.size());

        // Verify
        verify(authenticationLogService).getFilteredLogs(
                eq(email), eq(domain), eq(successful),
                eq(startDate), eq(endDate), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs/user/{userId} - Returns logs for specific user")
    void getLogsByUserId_ReturnsUserLogs() throws Exception {
        // Arrange
        String userId = "user123";

        AuthenticationLog log1 = createSuccessfulAuthLog("user@example.com", userId);
        AuthenticationLog log2 = createSuccessfulAuthLog("user@example.com", userId);

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsByUserIdPaginated(eq(userId), any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs/user/{userId}", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());
        assertEquals(userId, responseLogList.get(0).getUserId());

        // Verify
        verify(authenticationLogService).getLogsByUserIdPaginated(eq(userId), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs/email/{email} - Returns logs for specific email")
    void getLogsByEmail_ReturnsEmailLogs() throws Exception {
        // Arrange
        String email = "user@example.com";

        AuthenticationLog log1 = createSuccessfulAuthLog(email, "user1");
        AuthenticationLog log2 = createFailedAuthLog(email, "Invalid password");

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsByEmailPaginated(eq(email), any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs/email/{email}", email)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());
        assertEquals(email, responseLogList.get(0).getEmail());

        // Verify
        verify(authenticationLogService).getLogsByEmailPaginated(eq(email), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs/domain/{domain} - Returns logs for specific domain")
    void getLogsByDomain_ReturnsDomainLogs() throws Exception {
        // Arrange
        String domain = "example.com";

        AuthenticationLog log1 = createSuccessfulAuthLog("user1@example.com", "user1");
        log1.setPrimaryDomain(domain);
        AuthenticationLog log2 = createSuccessfulAuthLog("user2@example.com", "user2");
        log2.setPrimaryDomain(domain);

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsByDomainPaginated(eq(domain), any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs/domain/{domain}", domain)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());
        assertEquals(domain, responseLogList.get(0).getPrimaryDomain());

        // Verify
        verify(authenticationLogService).getLogsByDomainPaginated(eq(domain), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs/status - Returns logs by success status")
    void getLogsBySuccessStatus_ReturnsStatusLogs() throws Exception {
        // Arrange
        boolean successful = true;

        AuthenticationLog log1 = createSuccessfulAuthLog("user1@example.com", "user1");
        AuthenticationLog log2 = createSuccessfulAuthLog("user2@example.com", "user2");

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsBySuccessStatusPaginated(eq(successful), any(Pageable.class)))
                .thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs/status")
                .param("successful", "true")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());
        assertTrue(responseLogList.get(0).isSuccessful());

        // Verify
        verify(authenticationLogService).getLogsBySuccessStatusPaginated(eq(successful), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs/ip/{ipAddress} - Returns logs for specific IP address")
    void getLogsByIpAddress_ReturnsIpLogs() throws Exception {
        // Arrange
        String ipAddress = "192.168.1.1";

        AuthenticationLog log1 = createSuccessfulAuthLog("user1@example.com", "user1");
        log1.setIpAddress(ipAddress);
        AuthenticationLog log2 = createFailedAuthLog("user2@example.com", "Invalid password");
        log2.setIpAddress(ipAddress);

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsByIpAddressPaginated(eq(ipAddress), any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs/ip/{ipAddress}", ipAddress)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());
        assertEquals(ipAddress, responseLogList.get(0).getIpAddress());

        // Verify
        verify(authenticationLogService).getLogsByIpAddressPaginated(eq(ipAddress), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/auth-logs/timerange - Returns logs for specific time range")
    void getLogsByTimeRange_ReturnsTimeRangeLogs() throws Exception {
        // Arrange
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        AuthenticationLog log1 = createSuccessfulAuthLog("user1@example.com", "user1");
        AuthenticationLog log2 = createFailedAuthLog("user2@example.com", "Invalid password");

        List<AuthenticationLog> logs = Arrays.asList(log1, log2);
        Page<AuthenticationLog> page = new PageImpl<>(logs, PageRequest.of(0, 10), 2);

        when(authenticationLogService.getLogsByTimeRangePaginated(eq(start), eq(end), any(Pageable.class)))
                .thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/auth-logs/timerange")
                .param("start", start.toString())
                .param("end", end.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert - use manual extraction of content to avoid Page deserialization
        // issues
        String responseBody = result.getResponse().getContentAsString();
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode contentNode = rootNode.get("content");
        List<AuthenticationLog> responseLogList = objectMapper.convertValue(contentNode,
                new TypeReference<List<AuthenticationLog>>() {
                });

        assertEquals(2, responseLogList.size());

        // Verify
        verify(authenticationLogService).getLogsByTimeRangePaginated(eq(start), eq(end), any(Pageable.class));
    }

    // Helper method to create successful authentication log
    private AuthenticationLog createSuccessfulAuthLog(String email, String userId) {
        AuthenticationLog log = new AuthenticationLog();
        log.setId(java.util.UUID.randomUUID().toString());
        log.setEmail(email);
        log.setUserId(userId);
        log.setPrimaryDomain(email.substring(email.indexOf('@') + 1));
        log.setIpAddress("127.0.0.1");
        log.setUserAgent("Mozilla/5.0");
        log.setSuccessful(true);
        log.setTimestamp(LocalDateTime.now());
        return log;
    }

    // Helper method to create failed authentication log
    private AuthenticationLog createFailedAuthLog(String email, String failureReason) {
        AuthenticationLog log = new AuthenticationLog();
        log.setId(java.util.UUID.randomUUID().toString());
        log.setEmail(email);
        log.setIpAddress("127.0.0.1");
        log.setUserAgent("Mozilla/5.0");
        log.setSuccessful(false);
        log.setFailureReason(failureReason);
        log.setTimestamp(LocalDateTime.now());
        return log;
    }
}