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

/**
 * Service for sending emails related to purchase requests.
 */
@Service
public class PurchaseEmailService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:mycloudmen@gmail.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public PurchaseEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send a purchase request email with a link to accept the request.
     *
     * @param to        The recipient's email address
     * @param requestId The unique ID of the request
     * @throws MessagingException If there's an error sending the email
     */
    public void sendPurchaseRequest(String to, String requestId) throws MessagingException {
        log.info("Sending purchase request email for request ID: {}", requestId);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Purchase Request");

        String htmlContent = "<p>Hello,</p>"
                + "<p>You have a new purchase request.</p>"
                + "<a href=\"" + baseUrl + "/purchase/accept?requestId=" + requestId + "\">Accept Purchase</a>";

        helper.setText(htmlContent, true);

        try {
            mailSender.send(message);
            log.info("Purchase request email sent successfully");
        } catch (MailException e) {
            log.error("Failed to send purchase request email: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Send a confirmation email after a purchase has been accepted.
     *
     * @param to The recipient's email address
     * @throws MessagingException If there's an error sending the email
     */
    public void sendConfirmationEmail(String to) throws MessagingException {
        log.info("Sending purchase confirmation email to: {}", to);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Purchase Confirmation");

        String htmlContent = "<p>Hello,</p>"
                + "<p>Your purchase request has been accepted and processed.</p>"
                + "<p>Thank you for your purchase!</p>";

        helper.setText(htmlContent, true);

        try {
            mailSender.send(message);
            log.info("Purchase confirmation email sent successfully");
        } catch (MailException e) {
            log.error("Failed to send confirmation email: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Send a Google Workspace license purchase request email with a link to accept
     * the request.
     *
     * @param to          The recipient's email address (admin)
     * @param requestId   The unique ID of the request
     * @param count       The number of licenses requested
     * @param licenseType The type of license
     * @param domain      The domain for which licenses are requested
     * @param userEmail   The email of the user who requested the licenses
     * @param customerId  The Google customer ID associated with the request
     * @throws MessagingException If there's an error sending the email
     */
    public void sendGoogleWorkspaceLicenseRequest(
            String to,
            String requestId,
            int count,
            String licenseType,
            String domain,
            String userEmail,
            String customerId) throws MessagingException {

        log.info("Sending Google Workspace license request email for request ID: {}, customer ID: {}", requestId,
                customerId);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Google Workspace License Purchase Request");

        String htmlContent = "<p>Hello,</p>"
                + "<p>You have a new Google Workspace license purchase request from " + userEmail + ".</p>"
                + "<p>Request details:</p>"
                + "<ul>"
                + "<li>Number of licenses: " + count + "</li>"
                + "<li>License type: " + licenseType + "</li>"
                + "<li>Domain: " + domain + "</li>"
                + "<li>Customer ID: " + customerId + "</li>"
                + "</ul>"
                + "<a href=\"" + baseUrl + "/api/purchase/google-workspace/accept?requestId=" + requestId
                + "&customerId=" + customerId + "\">Accept Purchase</a>";

        helper.setText(htmlContent, true);

        try {
            mailSender.send(message);
            log.info("Google Workspace license request email sent successfully");
        } catch (MailException e) {
            log.error("Failed to send Google Workspace license request email: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Send a confirmation email after Google Workspace licenses have been
     * purchased.
     *
     * @param to          The recipient's email address (requesting user)
     * @param count       The number of licenses purchased
     * @param licenseType The type of license
     * @param domain      The domain for which licenses were purchased
     * @throws MessagingException If there's an error sending the email
     */
    public void sendGoogleWorkspaceLicenseConfirmation(
            String to,
            int count,
            String licenseType,
            String domain) throws MessagingException {

        log.info("Sending Google Workspace license confirmation email to: {}", to);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Google Workspace License Purchase Confirmation");

        String htmlContent = "<p>Hello,</p>"
                + "<p>Your Google Workspace license purchase request has been accepted and processed.</p>"
                + "<p>Details:</p>"
                + "<ul>"
                + "<li>Number of licenses: " + count + "</li>"
                + "<li>License type: " + licenseType + "</li>"
                + "<li>Domain: " + domain + "</li>"
                + "</ul>"
                + "<p>Thank you for your purchase!</p>"
                + "<p>The licenses will be added to your account shortly.</p>";

        helper.setText(htmlContent, true);

        try {
            mailSender.send(message);
            log.info("Google Workspace license confirmation email sent successfully");
        } catch (MailException e) {
            log.error("Failed to send Google Workspace license confirmation email: {}", e.getMessage());
            throw e;
        }
    }
}