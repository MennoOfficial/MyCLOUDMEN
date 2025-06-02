package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.domain.enums.RoleType;
import com.cloudmen.backend.domain.enums.StatusType;
import com.cloudmen.backend.domain.models.User;
import com.cloudmen.backend.services.UserEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private UserEmailService userEmailService;

    private User testUser;
    private MimeMessage mimeMessage;

    @BeforeEach
    public void setup() {
        // Set up test data
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
        testUser.setStatus(StatusType.ACTIVATED);
        testUser.setRoles(Arrays.asList(RoleType.COMPANY_USER));

        // Create a mock MimeMessage
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        // Configure service with test properties
        ReflectionTestUtils.setField(userEmailService, "username", "test@example.com");
        ReflectionTestUtils.setField(userEmailService, "baseUrl", "http://test.example.com");

        // Mock the mail sender to return our mock message
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    public void testSendUserApprovalEmail() throws MessagingException {
        // Mock the email sending
        doNothing().when(mailSender).send(any(MimeMessage.class));

        // Call the method being tested
        userEmailService.sendUserApprovalEmail(testUser);

        // Verify that send was called once
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    public void testSendUserApprovalEmailWithNullUser() throws MessagingException {
        // Test with null user
        userEmailService.sendUserApprovalEmail(null);

        // Verify that send was not called
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    public void testSendUserApprovalEmailWithNullEmail() throws MessagingException {
        // Set up a user with null email
        testUser.setEmail(null);

        // Call the method being tested
        userEmailService.sendUserApprovalEmail(testUser);

        // Verify that send was not called
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    public void testSendUserApprovalEmailWithEmptyEmail() throws MessagingException {
        // Set up a user with empty email
        testUser.setEmail("");

        // Call the method being tested
        userEmailService.sendUserApprovalEmail(testUser);

        // Verify that send was not called
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    public void testSendUserApprovalEmailWithMailException() {
        // Mock a mail exception when sending
        doThrow(new RuntimeException("Mail server error")).when(mailSender).send(any(MimeMessage.class));

        // Verify that a MessagingException is thrown
        assertThrows(MessagingException.class, () -> {
            userEmailService.sendUserApprovalEmail(testUser);
        });
    }
}