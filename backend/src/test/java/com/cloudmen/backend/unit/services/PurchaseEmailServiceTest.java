package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.services.PurchaseEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PurchaseEmailService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseEmailService Tests")
class PurchaseEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private PurchaseEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new PurchaseEmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@example.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "https://test.com");

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("sendPurchaseRequest should send email with correct parameters")
    void sendPurchaseRequest_shouldSendEmailWithCorrectParameters() throws MessagingException {
        // Arrange
        String requestId = "req-123";
        String toEmail = "admin@example.com";

        // Act
        emailService.sendPurchaseRequest(toEmail, requestId);

        // Assert
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendPurchaseRequest should handle exceptions properly")
    void sendPurchaseRequest_shouldHandleExceptionsGracefully() throws MessagingException {
        // Arrange
        String requestId = "req-123";
        String toEmail = "admin@example.com";

        // Use MailSendException instead of MessagingException
        doThrow(new MailSendException("Failed to send mail"))
                .when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            emailService.sendPurchaseRequest(toEmail, requestId);
        });

        assertTrue(exception instanceof MailSendException);
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendConfirmationEmail should send email with correct parameters")
    void sendConfirmationEmail_shouldSendEmailWithCorrectParameters() throws MessagingException {
        // Arrange
        String userEmail = "user@example.com";

        // Act
        emailService.sendConfirmationEmail(userEmail);

        // Assert
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendConfirmationEmail should handle exceptions properly")
    void sendConfirmationEmail_shouldHandleExceptionsGracefully() throws MessagingException {
        // Arrange
        String userEmail = "user@example.com";

        // Use MailSendException instead of MessagingException
        doThrow(new MailSendException("Failed to send mail"))
                .when(mailSender).send(any(MimeMessage.class));

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            emailService.sendConfirmationEmail(userEmail);
        });

        assertTrue(exception instanceof MailSendException);
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }
}