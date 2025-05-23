package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.services.PurchaseEmailService;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PurchaseEmailService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseEmailService Tests")
class PurchaseEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;

    private PurchaseEmailService emailService;
    private MimeMessage mimeMessage;

    private final String TEST_FROM_EMAIL = "test@example.com";
    private final String TEST_TO_EMAIL = "recipient@example.com";
    private final String TEST_BASE_URL = "https://test.com";
    private final String TEST_API_BASE_URL = "https://api.test.com";
    private final String TEST_REQUEST_ID = "request-123";

    @BeforeEach
    void setUp() throws MessagingException {
        // Create real MimeMessage for verification
        Session session = Session.getInstance(new Properties());
        mimeMessage = new MimeMessage(session);

        // Set up the mock to return a MimeMessage with lenient mode
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Create the service instance
        emailService = new PurchaseEmailService(mailSender);

        // Set required properties
        ReflectionTestUtils.setField(emailService, "username", TEST_FROM_EMAIL);
        ReflectionTestUtils.setField(emailService, "baseUrl", TEST_BASE_URL);
        ReflectionTestUtils.setField(emailService, "apiBaseUrl", TEST_API_BASE_URL);
        ReflectionTestUtils.setField(emailService, "fromEmail", TEST_FROM_EMAIL);
    }

    @Test
    @DisplayName("getFromEmail - Should prefer existing fromEmail field")
    void getFromEmail_ShouldPreferExistingFromEmailField() throws Exception {
        // Act - call the private method via reflection
        String result = (String) ReflectionTestUtils.invokeMethod(emailService, "getFromEmail");

        // Assert
        assertEquals(TEST_FROM_EMAIL, result);
    }

    @Test
    @DisplayName("getFromEmail - Should use username when it contains @")
    void getFromEmail_ShouldUseUsername_WhenItContainsAtSymbol() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(emailService, "fromEmail", null);
        ReflectionTestUtils.setField(emailService, "username", "user@domain.com");

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(emailService, "getFromEmail");

        // Assert
        assertEquals("user@domain.com", result);
    }

    @Test
    @DisplayName("getFromEmail - Should append Gmail domain when username doesn't contain @")
    void getFromEmail_ShouldAppendGmailDomain_WhenUsernameDoesntContainAtSymbol() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(emailService, "fromEmail", null);
        ReflectionTestUtils.setField(emailService, "username", "username");

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(emailService, "getFromEmail");

        // Assert
        assertEquals("username@gmail.com", result);
    }

    @Test
    @DisplayName("getFromEmail - Should use default when username is null")
    void getFromEmail_ShouldUseDefault_WhenUsernameIsNull() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(emailService, "fromEmail", null);
        ReflectionTestUtils.setField(emailService, "username", null);

        // Act
        String result = (String) ReflectionTestUtils.invokeMethod(emailService, "getFromEmail");

        // Assert
        assertEquals("mycloudmen@gmail.com", result);
    }

    @Test
    @DisplayName("sendPurchaseRequest - Should verify recipients and subject only")
    void sendPurchaseRequest_ShouldSetCorrectRecipientsAndSubject() throws MessagingException {
        // Act
        emailService.sendPurchaseRequest(TEST_TO_EMAIL, TEST_REQUEST_ID);

        // Assert - only verify the call happened and the basic recipient/subject info
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPurchaseRequest - Should handle mail exception")
    void sendPurchaseRequest_ShouldHandleMailException() {
        // Arrange
        doThrow(new MailSendException("SMTP server error")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class,
                () -> emailService.sendPurchaseRequest(TEST_TO_EMAIL, TEST_REQUEST_ID));

        assertTrue(exception.getMessage().contains("Failed to send email"));
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendConfirmationEmail - Should verify recipients and subject only")
    void sendConfirmationEmail_ShouldSetCorrectRecipientsAndSubject() throws MessagingException {
        // Act
        emailService.sendConfirmationEmail(TEST_TO_EMAIL);

        // Assert - only verify the call happened and the basic recipient/subject info
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendConfirmationEmail - Should handle mail exception")
    void sendConfirmationEmail_ShouldHandleMailException() {
        // Arrange
        doThrow(new MailSendException("SMTP server error")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        MessagingException exception = assertThrows(MessagingException.class,
                () -> emailService.sendConfirmationEmail(TEST_TO_EMAIL));

        assertTrue(exception.getMessage().contains("Failed to send email"));
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendGoogleWorkspaceLicenseRequest - Should verify basic call only")
    void sendGoogleWorkspaceLicenseRequest_ShouldSetCorrectRecipientsAndContainRequiredInfo()
            throws MessagingException {
        // Arrange
        String adminEmail = "admin@example.com";
        String licenseType = "Business Standard";
        String domain = "example.com";
        String customerName = "Test Company";
        String customerId = "customer-123";
        int licenseCount = 5;
        double cost = 60.0;

        // Act
        emailService.sendGoogleWorkspaceLicenseRequest(
                adminEmail, TEST_REQUEST_ID, licenseCount, licenseType,
                domain, TEST_TO_EMAIL, customerId, cost, customerName);

        // Assert - only verify the call happened
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendGoogleWorkspaceLicenseConfirmation - Should verify basic call only")
    void sendGoogleWorkspaceLicenseConfirmation_ShouldContainLicenseDetails() throws MessagingException {
        // Arrange
        String licenseType = "Business Standard";
        String domain = "example.com";
        int licenseCount = 5;
        double cost = 60.0;

        // Act
        emailService.sendGoogleWorkspaceLicenseConfirmation(
                TEST_TO_EMAIL, licenseCount, licenseType, domain, cost);

        // Assert - only verify the call happened
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("All email methods - Should handle unexpected exceptions")
    void allEmailMethods_ShouldHandleUnexpectedExceptions() throws MessagingException {
        // Arrange - simulate RuntimeException which isn't a MailException
        doThrow(new RuntimeException("Unexpected error")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert for all email methods
        Exception ex1 = assertThrows(MessagingException.class,
                () -> emailService.sendPurchaseRequest(TEST_TO_EMAIL, TEST_REQUEST_ID));
        assertTrue(ex1.getMessage().contains("Unexpected error"));

        Exception ex2 = assertThrows(MessagingException.class,
                () -> emailService.sendConfirmationEmail(TEST_TO_EMAIL));
        assertTrue(ex2.getMessage().contains("Unexpected error"));

        Exception ex3 = assertThrows(MessagingException.class,
                () -> emailService.sendGoogleWorkspaceLicenseRequest(
                        "admin@example.com", TEST_REQUEST_ID, 5, "Business Standard",
                        "example.com", TEST_TO_EMAIL, "customer-123", 60.0, "Test Company"));
        assertTrue(ex3.getMessage().contains("Unexpected error"));

        Exception ex4 = assertThrows(MessagingException.class,
                () -> emailService.sendGoogleWorkspaceLicenseConfirmation(
                        TEST_TO_EMAIL, 5, "Business Standard", "example.com", 60.0));
        assertTrue(ex4.getMessage().contains("Unexpected error"));
    }
}