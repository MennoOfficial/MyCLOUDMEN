package com.cloudmen.backend.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.cloudmen.backend.domain.models.User;

import java.time.Year;

/**
 * Service for sending user-related emails.
 */
@Service
public class UserEmailService {

    private static final Logger log = LoggerFactory.getLogger(UserEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String username;

    private String fromEmail;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    @Value("${app.api-base-url:http://localhost:8080}")
    private String apiBaseUrl;

    public UserEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("UserEmailService initialized with username: {}", username);
    }

    /**
     * Get the from email address ensuring it's a valid email format
     */
    private String getFromEmail() {
        // If fromEmail was already set, use it
        if (fromEmail != null && !fromEmail.isEmpty()) {
            return fromEmail;
        }

        // If username already contains @ symbol, use it directly
        if (username != null && username.contains("@")) {
            return username;
        }
        // Otherwise, assume it's a Gmail address
        return username != null ? username + "@gmail.com" : "mycloudmen@gmail.com";
    }

    private String getCommonStyles() {
        return "body { font-family: 'Roboto', Arial, sans-serif; color: #2c3e50; background: #f8f9fa; margin: 0; padding: 0; }"
                +
                ".container { max-width: 480px; margin: 8px auto 32px auto; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); padding: 18px 24px 28px 24px; }"
                +
                ".header { text-align: center; margin-bottom: 10px; margin-top: 0; }" +
                ".title { font-size: 22px; font-weight: 600; margin: 0 0 8px 0; color: #2c3e50; }" +
                ".subtitle { font-size: 16px; font-weight: 500; margin: 0 0 16px 0; color: #54bfae; }" +
                ".main-message { font-size: 16px; line-height: 1.5; margin: 16px 0; color: #2c3e50; }" +
                ".button { display: block; margin: 28px auto 0 auto; background: #54bfae; color: #fff !important; padding: 15px 0; width: 100%; max-width: 320px; border-radius: 8px; font-weight: 600; font-size: 17px; text-align: center; text-decoration: none !important; border: none; cursor: pointer; transition: background 0.2s, box-shadow 0.2s; box-shadow: 0 2px 8px rgba(84,191,174,0.10); }"
                +
                ".button:hover { background: #3cae99; color: #fff !important; box-shadow: 0 4px 16px rgba(84,191,174,0.18); }"
                +
                ".footer { text-align: center; color: #7f8c8d; font-size: 12px; margin-top: 32px; }" +
                "@media only screen and (max-width: 600px) { .container { padding: 8px 2vw; } .button { width: 100%; } }";
    }

    /**
     * Send a user approval email when a user's account has been activated.
     *
     * @param user The user who has been approved
     * @throws MessagingException If there's an error sending the email
     */
    public void sendUserApprovalEmail(User user) throws MessagingException {
        if (user == null || user.getEmail() == null || user.getEmail().isEmpty()) {
            log.error("Cannot send approval email - user or email is null");
            return;
        }

        String to = user.getEmail();
        log.info("Sending user approval email to: {}", to);
        fromEmail = getFromEmail();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try {
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Your Account Has Been Approved");

            // Frontend page URL for login
            String loginUrl = baseUrl;

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0' />"
                    +
                    "<title>Account Approved</title>" +
                    "<style>" + getCommonStyles() + "</style></head><body>" +
                    "<div class='container'>" +
                    "  <div class='header'>" +
                    "    <div class='title'>Account Approved</div>" +
                    "    <div class='subtitle'>Welcome to MyCLOUDMEN</div>" +
                    "  </div>" +
                    "  <div class='main-message'>" +
                    "    <p>Hello " + (user.getName() != null ? user.getName() : "there") + ",</p>" +
                    "    <p>We're pleased to inform you that your account has been approved. You can now log in to access all the features of MyCLOUDMEN.</p>"
                    +
                    "    <p>Your account has been set up with standard user access for your company.</p>" +
                    "  </div>" +
                    "  <a href='" + loginUrl + "' class='button'>Log In Now</a>" +
                    "  <div class='footer'>If you have any questions, please contact your company administrator.<br>&copy; "
                    + Year.now().getValue() + " MyCLOUDMEN</div>" +
                    "</div></body></html>";

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("User approval email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send user approval email: {}", e.getMessage(), e);
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending user approval email: {}", e.getMessage(), e);
            throw new MessagingException("Unexpected error: " + e.getMessage(), e);
        }
    }
}