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

import com.cloudmen.backend.domain.models.AuthenticationLog;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.repositories.AuthenticationLogRepository;
import com.cloudmen.backend.services.AuthenticationLogService;
import com.cloudmen.backend.services.UserService;

@ExtendWith(MockitoExtension.class)
class AuthenticationLogServiceTest {

    @Mock
    private AuthenticationLogRepository authLogRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthenticationLogService authLogService;

    private User testUser;
    private AuthenticationLog testSuccessLog;
    private AuthenticationLog testFailedLog;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");
        testUser.setPrimaryDomain("example.com");
        testUser.setCustomerGoogleId("customer-123");

        // Setup test authentication logs
        testSuccessLog = new AuthenticationLog(
                "test@example.com",
                "user-123",
                "example.com",
                "customer-123",
                "192.168.1.1",
                "Mozilla/5.0");
        testSuccessLog.setId("log-123");
        testSuccessLog.setSuccessful(true);
        testSuccessLog.setTimestamp(LocalDateTime.now());

        testFailedLog = new AuthenticationLog(
                "test@example.com",
                "192.168.1.1",
                "Mozilla/5.0",
                "Invalid credentials");
        testFailedLog.setId("log-456");
        testFailedLog.setSuccessful(false);
        testFailedLog.setTimestamp(LocalDateTime.now());

        // Setup pageable for pagination tests
        pageable = PageRequest.of(0, 10);
    }

    @Test
    @DisplayName("logSuccessfulAuthentication should create log with user data when user exists")
    void logSuccessfulAuthentication_shouldCreateLogWithUserData_whenUserExists() {
        // Arrange
        when(userService.getUserByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(authLogRepository.save(any(AuthenticationLog.class))).thenReturn(testSuccessLog);

        // Act
        AuthenticationLog result = authLogService.logSuccessfulAuthentication(
                "test@example.com", "192.168.1.1", "Mozilla/5.0");

        // Assert
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("user-123", result.getUserId());
        assertEquals("example.com", result.getPrimaryDomain());
        assertTrue(result.isSuccessful());

        verify(userService).getUserByEmail("test@example.com");
        verify(authLogRepository).save(any(AuthenticationLog.class));
    }

    @Test
    @DisplayName("logSuccessfulAuthentication should create basic log when user doesn't exist")
    void logSuccessfulAuthentication_shouldCreateBasicLog_whenUserDoesntExist() {
        // Arrange
        when(userService.getUserByEmail(anyString())).thenReturn(Optional.empty());
        when(authLogRepository.save(any(AuthenticationLog.class))).thenReturn(testSuccessLog);

        // Act
        AuthenticationLog result = authLogService.logSuccessfulAuthentication(
                "test@example.com", "192.168.1.1", "Mozilla/5.0");

        // Assert
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertTrue(result.isSuccessful());

        verify(userService).getUserByEmail("test@example.com");
        verify(authLogRepository).save(any(AuthenticationLog.class));
    }

    @Test
    @DisplayName("logFailedAuthentication should create failed log")
    void logFailedAuthentication_shouldCreateFailedLog() {
        // Arrange
        when(authLogRepository.save(any(AuthenticationLog.class))).thenReturn(testFailedLog);

        // Act
        AuthenticationLog result = authLogService.logFailedAuthentication(
                "test@example.com", "192.168.1.1", "Mozilla/5.0", "Invalid credentials");

        // Assert
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Invalid credentials", result.getFailureReason());
        assertFalse(result.isSuccessful());

        verify(authLogRepository).save(any(AuthenticationLog.class));
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("getLogsPaginated should return paginated logs")
    void getLogsPaginated_shouldReturnPaginatedLogs() {
        // Arrange
        Page<AuthenticationLog> expectedPage = new PageImpl<>(
                Arrays.asList(testSuccessLog, testFailedLog), pageable, 2);
        when(authLogRepository.findAll(any(Pageable.class))).thenReturn(expectedPage);

        // Act
        Page<AuthenticationLog> result = authLogService.getLogsPaginated(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        verify(authLogRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getLogsByUserId should return logs for user")
    void getLogsByUserId_shouldReturnLogsForUser() {
        // Arrange
        List<AuthenticationLog> expectedLogs = Collections.singletonList(testSuccessLog);
        when(authLogRepository.findByUserId(anyString())).thenReturn(expectedLogs);

        // Act
        List<AuthenticationLog> result = authLogService.getLogsByUserId("user-123");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user-123", result.get(0).getUserId());

        verify(authLogRepository).findByUserId("user-123");
    }

    @Test
    @DisplayName("getLogsByEmail should return logs for email")
    void getLogsByEmail_shouldReturnLogsForEmail() {
        // Arrange
        List<AuthenticationLog> expectedLogs = Arrays.asList(testSuccessLog, testFailedLog);
        when(authLogRepository.findByEmail(anyString())).thenReturn(expectedLogs);

        // Act
        List<AuthenticationLog> result = authLogService.getLogsByEmail("test@example.com");

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("test@example.com", result.get(0).getEmail());

        verify(authLogRepository).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("getFilteredLogs should apply multiple filters")
    void getFilteredLogs_shouldApplyMultipleFilters() {
        // Arrange
        String email = "test@example.com";
        String domain = "example.com";
        Boolean successful = true;
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);
        LocalDateTime endDate = LocalDateTime.now();

        Page<AuthenticationLog> expectedPage = new PageImpl<>(
                Collections.singletonList(testSuccessLog), pageable, 1);

        when(authLogRepository.findByEmailAndPrimaryDomainAndSuccessfulAndTimestampBetween(
                eq(email), eq(domain), eq(successful), eq(startDate), eq(endDate), any(Pageable.class)))
                .thenReturn(expectedPage);

        // Act
        Page<AuthenticationLog> result = authLogService.getFilteredLogs(
                email, domain, successful, startDate, endDate, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        verify(authLogRepository).findByEmailAndPrimaryDomainAndSuccessfulAndTimestampBetween(
                email, domain, successful, startDate, endDate, pageable);
    }

    @Test
    @DisplayName("getLastSuccessfulLoginByUserId should return last login time")
    void getLastSuccessfulLoginByUserId_shouldReturnLastLoginTime() {
        // Arrange
        LocalDateTime loginTime = LocalDateTime.now().minusHours(2);
        AuthenticationLog lastLogin = new AuthenticationLog();
        lastLogin.setTimestamp(loginTime);

        when(authLogRepository.findTopByUserIdAndSuccessfulOrderByTimestampDesc(anyString(), eq(true)))
                .thenReturn(lastLogin);

        // Act
        LocalDateTime result = authLogService.getLastSuccessfulLoginByUserId("user-123");

        // Assert
        assertNotNull(result);
        assertEquals(loginTime, result);

        verify(authLogRepository).findTopByUserIdAndSuccessfulOrderByTimestampDesc("user-123", true);
    }

    @Test
    @DisplayName("deleteLogsOlderThan should remove old logs")
    void deleteLogsOlderThan_shouldRemoveOldLogs() {
        // Arrange
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<AuthenticationLog> oldLogs = Arrays.asList(testSuccessLog, testFailedLog);

        when(authLogRepository.findByTimestampBetween(eq(LocalDateTime.MIN), eq(cutoffDate)))
                .thenReturn(oldLogs);

        // Act
        authLogService.deleteLogsOlderThan(cutoffDate);

        // Assert
        verify(authLogRepository).findByTimestampBetween(LocalDateTime.MIN, cutoffDate);
        verify(authLogRepository).deleteAll(oldLogs);
    }
}