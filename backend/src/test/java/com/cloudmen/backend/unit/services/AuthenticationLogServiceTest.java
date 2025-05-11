package com.cloudmen.backend.unit.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.AuthenticationLogRepository;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.UserService;

/**
 * Unit tests for AuthenticationLogService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationLogService Tests")
public class AuthenticationLogServiceTest {

        @Mock
        private AuthenticationLogRepository authenticationLogRepository;

        @Mock
        private UserService userService;

        @InjectMocks
        private AuthenticationLogService authenticationLogService;

        @Captor
        private ArgumentCaptor<AuthenticationLog> logCaptor;

        private User testUser;
        private AuthenticationLog testLog;

        @BeforeEach
        void setUp() {
                // Create test user
                testUser = new User();
                testUser.setId("user123");
                testUser.setEmail("test@example.com");
                testUser.setPrimaryDomain("example.com");
                testUser.setCustomerGoogleId("gid123");

                // Create test log
                testLog = new AuthenticationLog();
                testLog.setId("log123");
                testLog.setEmail("test@example.com");
                testLog.setUserId("user123");
                testLog.setPrimaryDomain("example.com");
                testLog.setGoogleUniqueId("gid123");
                testLog.setIpAddress("192.168.1.1");
                testLog.setUserAgent("Mozilla/5.0");
                testLog.setSuccessful(true);
                testLog.setTimestamp(LocalDateTime.now());
        }

        @Test
        @DisplayName("logSuccessfulAuthentication - With existing user")
        void logSuccessfulAuthentication_WithExistingUser() {
                // Arrange
                String email = "test@example.com";
                String ipAddress = "192.168.1.1";
                String userAgent = "Mozilla/5.0";

                when(userService.getUserByEmail(email)).thenReturn(Optional.of(testUser));
                when(authenticationLogRepository.save(any(AuthenticationLog.class))).thenReturn(testLog);

                // Act
                AuthenticationLog result = authenticationLogService.logSuccessfulAuthentication(email, ipAddress,
                                userAgent);

                // Assert
                assertNotNull(result);
                assertEquals("log123", result.getId());
                assertEquals(email, result.getEmail());
                assertEquals("user123", result.getUserId());
                assertEquals("example.com", result.getPrimaryDomain());
                assertEquals("gid123", result.getGoogleUniqueId());
                assertEquals(ipAddress, result.getIpAddress());
                assertEquals(userAgent, result.getUserAgent());
                assertTrue(result.isSuccessful());

                // Verify repository interactions
                ArgumentCaptor<AuthenticationLog> logCaptor = ArgumentCaptor.forClass(AuthenticationLog.class);
                verify(authenticationLogRepository).save(logCaptor.capture());
                AuthenticationLog capturedLog = logCaptor.getValue();

                assertEquals(email, capturedLog.getEmail());
                assertEquals(ipAddress, capturedLog.getIpAddress());
                assertEquals(userAgent, capturedLog.getUserAgent());
                assertTrue(capturedLog.isSuccessful());
        }

        @Test
        @DisplayName("logSuccessfulAuthentication - With non-existing user")
        void logSuccessfulAuthentication_WithNonExistingUser() {
                // Arrange
                String email = "nonexistent@example.com";
                String ipAddress = "192.168.1.1";
                String userAgent = "Mozilla/5.0";

                when(userService.getUserByEmail(email)).thenReturn(Optional.empty());

                AuthenticationLog savedLog = new AuthenticationLog();
                savedLog.setId("log456");
                savedLog.setEmail(email);
                savedLog.setIpAddress(ipAddress);
                savedLog.setUserAgent(userAgent);
                savedLog.setSuccessful(true);
                savedLog.setTimestamp(LocalDateTime.now());

                when(authenticationLogRepository.save(any(AuthenticationLog.class))).thenReturn(savedLog);

                // Act
                AuthenticationLog result = authenticationLogService.logSuccessfulAuthentication(email, ipAddress,
                                userAgent);

                // Assert
                assertNotNull(result);
                assertEquals("log456", result.getId());
                assertEquals(email, result.getEmail());
                assertNull(result.getUserId());
                assertNull(result.getPrimaryDomain());
                assertNull(result.getGoogleUniqueId());
                assertEquals(ipAddress, result.getIpAddress());
                assertEquals(userAgent, result.getUserAgent());
                assertTrue(result.isSuccessful());

                // Verify repository interactions
                ArgumentCaptor<AuthenticationLog> logCaptor = ArgumentCaptor.forClass(AuthenticationLog.class);
                verify(authenticationLogRepository).save(logCaptor.capture());
                AuthenticationLog capturedLog = logCaptor.getValue();

                assertEquals(email, capturedLog.getEmail());
                assertEquals(ipAddress, capturedLog.getIpAddress());
                assertEquals(userAgent, capturedLog.getUserAgent());
                assertTrue(capturedLog.isSuccessful());
        }

        @Test
        @DisplayName("logFailedAuthentication - With email provided")
        void logFailedAuthentication_WithEmailProvided() {
                // Arrange
                String email = "test@example.com";
                String ipAddress = "192.168.1.1";
                String userAgent = "Mozilla/5.0";
                String failureReason = "Invalid credentials";

                AuthenticationLog failedLog = new AuthenticationLog();
                failedLog.setId("logFailed123");
                failedLog.setEmail(email);
                failedLog.setIpAddress(ipAddress);
                failedLog.setUserAgent(userAgent);
                failedLog.setSuccessful(false);
                failedLog.setFailureReason(failureReason);
                failedLog.setTimestamp(LocalDateTime.now());

                when(authenticationLogRepository.save(any(AuthenticationLog.class))).thenReturn(failedLog);

                // Act
                AuthenticationLog result = authenticationLogService.logFailedAuthentication(
                                email, ipAddress, userAgent, failureReason);

                // Assert
                assertNotNull(result);
                assertEquals("logFailed123", result.getId());
                assertEquals(email, result.getEmail());
                assertEquals(ipAddress, result.getIpAddress());
                assertEquals(userAgent, result.getUserAgent());
                assertFalse(result.isSuccessful());
                assertEquals(failureReason, result.getFailureReason());

                // Verify repository interactions
                ArgumentCaptor<AuthenticationLog> logCaptor = ArgumentCaptor.forClass(AuthenticationLog.class);
                verify(authenticationLogRepository).save(logCaptor.capture());
                AuthenticationLog capturedLog = logCaptor.getValue();

                assertEquals(email, capturedLog.getEmail());
                assertEquals(ipAddress, capturedLog.getIpAddress());
                assertEquals(userAgent, capturedLog.getUserAgent());
                assertFalse(capturedLog.isSuccessful());
                assertEquals(failureReason, capturedLog.getFailureReason());
        }

        @Test
        @DisplayName("logFailedAuthentication - With null email")
        void logFailedAuthentication_WithNullEmail() {
                // Arrange
                String ipAddress = "192.168.1.1";
                String userAgent = "Mozilla/5.0";
                String failureReason = "Unknown user";

                AuthenticationLog failedLog = new AuthenticationLog();
                failedLog.setId("logFailed456");
                failedLog.setIpAddress(ipAddress);
                failedLog.setUserAgent(userAgent);
                failedLog.setSuccessful(false);
                failedLog.setFailureReason(failureReason);
                failedLog.setTimestamp(LocalDateTime.now());

                when(authenticationLogRepository.save(any(AuthenticationLog.class))).thenReturn(failedLog);

                // Act
                AuthenticationLog result = authenticationLogService.logFailedAuthentication(
                                null, ipAddress, userAgent, failureReason);

                // Assert
                assertNotNull(result);
                assertEquals("logFailed456", result.getId());
                assertNull(result.getEmail());
                assertEquals(ipAddress, result.getIpAddress());
                assertEquals(userAgent, result.getUserAgent());
                assertFalse(result.isSuccessful());
                assertEquals(failureReason, result.getFailureReason());

                // Verify repository interactions
                ArgumentCaptor<AuthenticationLog> logCaptor = ArgumentCaptor.forClass(AuthenticationLog.class);
                verify(authenticationLogRepository).save(logCaptor.capture());
                AuthenticationLog capturedLog = logCaptor.getValue();

                assertNull(capturedLog.getEmail());
                assertEquals(ipAddress, capturedLog.getIpAddress());
                assertEquals(userAgent, capturedLog.getUserAgent());
                assertFalse(capturedLog.isSuccessful());
                assertEquals(failureReason, capturedLog.getFailureReason());
        }

        @Test
        @DisplayName("getLogsBySuccessStatusPaginated - Returns paginated logs")
        void getLogsBySuccessStatusPaginated_ReturnsPaginatedLogs() {
                // Arrange
                boolean successful = true;
                Pageable pageable = PageRequest.of(0, 10);

                AuthenticationLog log1 = new AuthenticationLog();
                log1.setId("log1");
                log1.setSuccessful(true);

                AuthenticationLog log2 = new AuthenticationLog();
                log2.setId("log2");
                log2.setSuccessful(true);

                List<AuthenticationLog> logs = Arrays.asList(log1, log2);
                Page<AuthenticationLog> expectedPage = new PageImpl<>(logs, pageable, logs.size());

                when(authenticationLogRepository.findBySuccessful(successful, pageable)).thenReturn(expectedPage);

                // Act
                Page<AuthenticationLog> resultPage = authenticationLogService
                                .getLogsBySuccessStatusPaginated(successful, pageable);

                // Assert
                assertNotNull(resultPage);
                assertEquals(2, resultPage.getTotalElements());
                assertEquals(logs, resultPage.getContent());

                // Verify repository interactions
                verify(authenticationLogRepository).findBySuccessful(successful, pageable);
        }
}