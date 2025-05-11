package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.services.PurchaseEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Simple unit tests for PurchaseEmailService that focus on email sending
 * behavior
 * rather than complex mocking.
 */
@DisplayName("PurchaseEmailService Tests")
class PurchaseEmailServiceTest {

    private JavaMailSender mailSender;
    private PurchaseEmailService emailService;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        // Create mocks manually instead of using annotations
        mailSender = mock(JavaMailSender.class);
        mimeMessage = mock(MimeMessage.class);

        // Set up the mock to return a MimeMessage
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Create the service instance
        emailService = new PurchaseEmailService(mailSender);

        // Set required properties
        ReflectionTestUtils.setField(emailService, "username", "test@example.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://test.com");
        ReflectionTestUtils.setField(emailService, "apiBaseUrl", "https://api.test.com");
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@example.com");
    }

    @Test
    @DisplayName("sendPurchaseRequest - Happy Path")
    void sendPurchaseRequest_HappyPath() throws MessagingException {
        // Act
        emailService.sendPurchaseRequest("test@example.com", "request-123");

        // Verify that the email was sent
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPurchaseRequest - Exception Handling")
    void sendPurchaseRequest_ExceptionHandling() throws MessagingException {
        // Arrange - make the send method throw an exception
        doThrow(new RuntimeException("Mail error")).when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        assertThrows(MessagingException.class,
                () -> emailService.sendPurchaseRequest("test@example.com", "request-123"));
    }

    @Test
    @DisplayName("sendConfirmationEmail - Happy Path")
    void sendConfirmationEmail_HappyPath() throws MessagingException {
        // Act
        emailService.sendConfirmationEmail("test@example.com");

        // Verify that the email was sent
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendGoogleWorkspaceLicenseRequest - Happy Path")
    void sendGoogleWorkspaceLicenseRequest_HappyPath() throws MessagingException {
        // Act
        emailService.sendGoogleWorkspaceLicenseRequest(
                "admin@example.com", "request-123", 5, "Business Standard",
                "example.com", "user@example.com", "customer-id", 60.0, "Test Company");

        // Verify that the email was sent
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendGoogleWorkspaceLicenseConfirmation - Happy Path")
    void sendGoogleWorkspaceLicenseConfirmation_HappyPath() throws MessagingException {
        // Act
        emailService.sendGoogleWorkspaceLicenseConfirmation(
                "user@example.com", 5, "Business Standard", "example.com", 60.0);

        // Verify that the email was sent
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }
}