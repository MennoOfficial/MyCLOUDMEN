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

    @Value("${spring.mail.username}")
    private String username;

    private String fromEmail;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    @Value("${app.api-base-url:http://localhost:8080}")
    private String apiBaseUrl;

    public PurchaseEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("PurchaseEmailService initialized with username: {}", username);
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
                ".details-card { background: #f8f9fa; border-radius: 10px; border: 1.5px solid #54bfae; padding: 22px 18px 16px 18px; margin: 18px 0 10px 0; box-shadow: 0 1px 4px rgba(84,191,174,0.07); }"
                +
                ".details-row { display: flex; justify-content: space-between; margin-bottom: 16px; font-size: 15px; }"
                +
                ".details-row:last-of-type { margin-bottom: 22px; }" +
                ".details-label { color: #7f8c8d; font-weight: 500; width: 30%; flex-shrink: 0; }" +
                ".details-value { color: #2c3e50; font-weight: 600; text-align: right; width: 70%; word-break: break-word; }"
                +
                ".details-section { margin-bottom: 20px; border-bottom: 1px solid #e9ecef; padding-bottom: 5px; }" +
                ".details-section:last-of-type { border-bottom: none; }" +
                ".price-row { display: flex; justify-content: space-between; margin-top: 20px; padding-top: 16px; border-top: 1px solid #e9ecef; font-size: 17px; }"
                +
                ".price-label { color: #54bfae; font-weight: 600; }" +
                ".price-value { color: #54bfae; font-weight: 700; }" +
                ".request-id { font-size: 13px; color: #b0b0b0; text-align: center; margin: 20px 0 5px 0; }" +
                ".button { display: block; margin: 28px auto 0 auto; background: #54bfae; color: #fff !important; padding: 15px 0; width: 100%; max-width: 320px; border-radius: 8px; font-weight: 600; font-size: 17px; text-align: center; text-decoration: none !important; border: none; cursor: pointer; transition: background 0.2s, box-shadow 0.2s; box-shadow: 0 2px 8px rgba(84,191,174,0.10); }"
                +
                ".button:hover { background: #3cae99; color: #fff !important; box-shadow: 0 4px 16px rgba(84,191,174,0.18); }"
                +
                ".footer { text-align: center; color: #7f8c8d; font-size: 12px; margin-top: 32px; }" +
                "@media only screen and (max-width: 600px) { .container { padding: 8px 2vw; } .button { width: 100%; } }";
    }

    /**
     * Send a purchase request email with a link to accept the request.
     *
     * @param to        The recipient's email address
     * @param requestId The unique ID of the request
     * @throws MessagingException If there's an error sending the email
     */
    public void sendPurchaseRequest(String to, String requestId) throws MessagingException {
        log.info("Sending purchase request email for request ID: {} to recipient: {}", requestId, to);
        fromEmail = getFromEmail();
        log.debug("Email configuration: fromEmail={}, baseUrl={}, apiBaseUrl={}", fromEmail, baseUrl, apiBaseUrl);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try {
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Your Purchase Request");

            // Frontend page URL for accepting the purchase
            String confirmUrl = "/confirm-purchase?requestId=" + requestId + "&email=" + to;
            String fullConfirmUrl = baseUrl + confirmUrl;
            log.debug("Confirm URL: {}", fullConfirmUrl);

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0' />"
                    +
                    "<title>Your Purchase Request</title>" +
                    "<style>" + getCommonStyles() + "</style></head><body>" +
                    "<div class='container'>" +
                    "  <div class='header'>" +
                    "    <div class='title'>Purchase Request</div>" +
                    "  </div>" +
                    "  <div class='main-message'>Thank you for your purchase request.<br>Your request ID: <b>"
                    + requestId + "</b></div>" +
                    "  <a href='" + fullConfirmUrl + "' class='button'>Confirm Purchase</a>" +
                    "  <div class='footer'>If you didn't make this request, you can ignore this email.<br>&copy; "
                    + java.time.Year.now().getValue() + " MyCLOUDMEN</div>" +
                    "</div></body></html>";

            helper.setText(htmlContent, true);

            // Add null check before accessing getFrom() - a common issue in tests
            try {
                if (helper.getMimeMessage().getFrom() != null && helper.getMimeMessage().getAllRecipients() != null) {
                    log.debug("About to send email with subject: '{}' from: '{}' to: '{}'",
                            helper.getMimeMessage().getSubject(),
                            helper.getMimeMessage().getFrom()[0],
                            helper.getMimeMessage().getAllRecipients()[0]);
                } else {
                    log.debug("About to send email with subject: '{}' (from/to info not available in test environment)",
                            helper.getMimeMessage().getSubject());
                }
            } catch (Exception e) {
                log.debug("Could not log email details: {}", e.getMessage());
            }

            mailSender.send(message);
            log.info("Purchase request email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send purchase request email: {}", e.getMessage(), e);
            // Log mail configuration for debugging
            log.debug("Email configuration - from: {}, to: {}, baseUrl: {}, apiBaseUrl: {}",
                    fromEmail, to, baseUrl, apiBaseUrl);
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending purchase request email: {}", e.getMessage(), e);
            throw new MessagingException("Unexpected error: " + e.getMessage(), e);
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
        fromEmail = getFromEmail();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try {
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Purchase Confirmation");

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0' />"
                    +
                    "<title>Purchase Confirmation</title>" +
                    "<style>" + getCommonStyles() + "</style></head><body>" +
                    "<div class='container'>" +
                    "  <div class='header'>" +
                    "    <div class='title'>Purchase Confirmed</div>" +
                    "  </div>" +
                    "  <div class='main-message'>Your purchase request has been accepted and processed successfully.</div>"
                    +
                    "  <div class='footer'>Thank you for your purchase!<br>&copy; " + java.time.Year.now().getValue()
                    + " MyCLOUDMEN</div>" +
                    "</div></body></html>";

            helper.setText(htmlContent, true);

            // Add null check before accessing getFrom() - a common issue in tests
            try {
                if (helper.getMimeMessage().getFrom() != null && helper.getMimeMessage().getAllRecipients() != null) {
                    log.debug("About to send email with subject: '{}' from: '{}' to: '{}'",
                            helper.getMimeMessage().getSubject(),
                            helper.getMimeMessage().getFrom()[0],
                            helper.getMimeMessage().getAllRecipients()[0]);
                } else {
                    log.debug("About to send email with subject: '{}' (from/to info not available in test environment)",
                            helper.getMimeMessage().getSubject());
                }
            } catch (Exception e) {
                log.debug("Could not log email details: {}", e.getMessage());
            }

            mailSender.send(message);
            log.info("Purchase confirmation email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send confirmation email: {}", e.getMessage(), e);
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending confirmation email: {}", e.getMessage(), e);
            throw new MessagingException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Send a Google Workspace license purchase request email with a link to accept
     * the request.
     *
     * @param to          The recipient's email address (requesting user)
     * @param requestId   The unique ID of the request
     * @param count       The number of licenses requested
     * @param licenseType The type of license
     * @param domain      The domain for which licenses are requested
     * @param userEmail   The email of the user who made the request
     * @param customerId  The Google Workspace customer ID
     * @param cost        The cost of the license purchase
     * @param companyName The name of the company requesting the license
     * @throws MessagingException If there's an error sending the email
     */
    public void sendGoogleWorkspaceLicenseRequest(
            String to,
            String requestId,
            int count,
            String licenseType,
            String domain,
            String userEmail,
            String customerId,
            Double cost,
            String companyName) throws MessagingException {

        log.info("Sending Google Workspace license request email for requestId: {} to: {}", requestId, to);
        fromEmail = getFromEmail();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try {
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Google Workspace License Request");

            // Frontend page URL for approving the license request
            String approveUrl = "/approve-license?requestId=" + requestId +
                    "&email=" + userEmail +
                    "&count=" + count +
                    "&licenseType=" + licenseType +
                    "&domain=" + domain;
            String fullApproveUrl = baseUrl + approveUrl;

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0' />"
                    +
                    "<title>Google Workspace License Request</title>" +
                    "<style>" + getCommonStyles() +
                    ".details-card { background: #f8f9fa; border-radius: 10px; border: 1.5px solid #54bfae; padding: 22px 18px 16px 18px; margin: 18px 0 10px 0; box-shadow: 0 1px 4px rgba(84,191,174,0.07); }"
                    +
                    ".details-row { display: flex; justify-content: space-between; margin-bottom: 16px; font-size: 15px; }"
                    +
                    ".details-row:last-of-type { margin-bottom: 22px; }" +
                    ".details-label { color: #7f8c8d; font-weight: 500; width: 30%; flex-shrink: 0; }" +
                    ".details-value { color: #2c3e50; font-weight: 600; text-align: right; width: 70%; word-break: break-word; }"
                    +
                    ".details-section { margin-bottom: 20px; border-bottom: 1px solid #e9ecef; padding-bottom: 5px; }" +
                    ".details-section:last-of-type { border-bottom: none; }" +
                    ".price-row { display: flex; justify-content: space-between; margin-top: 20px; padding-top: 16px; border-top: 1px solid #e9ecef; font-size: 17px; }"
                    +
                    ".price-label { color: #54bfae; font-weight: 600; }" +
                    ".price-value { color: #54bfae; font-weight: 700; }" +
                    ".request-id { font-size: 13px; color: #b0b0b0; text-align: center; margin: 20px 0 5px 0; }" +
                    ".button { display: block; margin: 22px auto 0 auto; background: #54bfae; color: #fff !important; padding: 15px 0; width: 100%; max-width: 320px; border-radius: 8px; font-weight: 600; font-size: 17px; text-align: center; text-decoration: none !important; border: none; cursor: pointer; transition: background 0.2s, box-shadow 0.2s; box-shadow: 0 2px 8px rgba(84,191,174,0.10); }"
                    +
                    ".button:hover { background: #3cae99; color: #fff !important; box-shadow: 0 4px 16px rgba(84,191,174,0.18); }"
                    +
                    "</style></head><body>" +
                    "<div class='container'>" +
                    "  <div class='header'>" +
                    "    <div class='title'>License Approval</div>" +
                    "  </div>" +
                    "  <div class='details-card'>" +
                    "    <div class='details-section'>" +
                    (companyName != null
                            ? "<div class='details-row'><span class='details-label'>Domain</span><span class='details-value'>"
                                    + companyName + "</span></div>"
                            : "")
                    +
                    "      <div class='details-row'><span class='details-label'>Product</span><span class='details-value'>"
                    + licenseType + "</span></div>" +
                    "      <div class='details-row'><span class='details-label'>Quantity</span><span class='details-value'>"
                    + count + "</span></div>" +
                    "    </div>" +
                    (cost != null
                            ? "<div class='price-row'><span class='price-label'>Total Price</span><span class='price-value'>$"
                                    + String.format("%.2f", cost) + "</span></div>"
                            : "")
                    +
                    "    <div class='request-id'>Request ID: <b>" + requestId + "</b></div>" +
                    "    <a href='" + fullApproveUrl + "' class='button'>Approve License Request</a>" +
                    "  </div>" +
                    "  <div class='footer'>If you didn't make this request, you can ignore this email.<br>&copy; "
                    + java.time.Year.now().getValue() + " MyCLOUDMEN</div>" +
                    "</div></body></html>";

            helper.setText(htmlContent, true);

            // Add null check before accessing getFrom() - a common issue in tests
            try {
                if (helper.getMimeMessage().getFrom() != null && helper.getMimeMessage().getAllRecipients() != null) {
                    log.debug("About to send email with subject: '{}' from: '{}' to: '{}'",
                            helper.getMimeMessage().getSubject(),
                            helper.getMimeMessage().getFrom()[0],
                            helper.getMimeMessage().getAllRecipients()[0]);
                } else {
                    log.debug("About to send email with subject: '{}' (from/to info not available in test environment)",
                            helper.getMimeMessage().getSubject());
                }
            } catch (Exception e) {
                log.debug("Could not log email details: {}", e.getMessage());
            }

            mailSender.send(message);
            log.info("Google Workspace license request email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send Google Workspace license request email: {}", e.getMessage(), e);
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending Google Workspace license request email: {}", e.getMessage(), e);
            throw new MessagingException("Unexpected error: " + e.getMessage(), e);
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
     * @param cost        The cost of the license purchase
     * @throws MessagingException If there's an error sending the email
     */
    public void sendGoogleWorkspaceLicenseConfirmation(
            String to,
            int count,
            String licenseType,
            String domain,
            Double cost) throws MessagingException {

        log.info("Sending Google Workspace license confirmation email to: {}", to);
        fromEmail = getFromEmail();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        try {
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Google Workspace License Purchase Confirmation");

            String htmlContent = "<!DOCTYPE html>" +
                    "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0' />"
                    +
                    "<title>Google Workspace License Confirmation</title>" +
                    "<style>" + getCommonStyles() +
                    ".details-card { background: #f8f9fa; border-radius: 10px; border: 1.5px solid #54bfae; padding: 22px 18px 16px 18px; margin: 18px 0 10px 0; box-shadow: 0 1px 4px rgba(84,191,174,0.07); }"
                    +
                    ".details-row { display: flex; justify-content: space-between; margin-bottom: 16px; font-size: 15px; }"
                    +
                    ".details-row:last-of-type { margin-bottom: 22px; }" +
                    ".details-label { color: #7f8c8d; font-weight: 500; width: 30%; flex-shrink: 0; }" +
                    ".details-value { color: #2c3e50; font-weight: 600; text-align: right; width: 70%; word-break: break-word; }"
                    +
                    ".details-section { margin-bottom: 20px; border-bottom: 1px solid #e9ecef; padding-bottom: 5px; }" +
                    ".details-section:last-of-type { border-bottom: none; }" +
                    ".price-row { display: flex; justify-content: space-between; margin-top: 20px; padding-top: 16px; border-top: 1px solid #e9ecef; font-size: 17px; }"
                    +
                    ".price-label { color: #54bfae; font-weight: 600; }" +
                    ".price-value { color: #54bfae; font-weight: 700; }" +
                    ".confirmation-badge { background-color: #54bfae; color: white; font-weight: bold; padding: 6px 12px; border-radius: 20px; display: inline-block; margin-bottom: 15px; }"
                    +
                    "</style></head><body>" +
                    "<div class='container'>" +
                    "  <div class='header'>" +
                    "    <div class='title'>Purchase Confirmed</div>" +
                    "  </div>" +
                    "  <div style='text-align: center; margin-bottom: 10px;'><span class='confirmation-badge'>Approved</span></div>"
                    +
                    "  <div class='details-card'>" +
                    "    <div class='details-section'>" +
                    "      <div class='details-row'><span class='details-label'>Product</span><span class='details-value'>"
                    + licenseType + "</span></div>" +
                    "      <div class='details-row'><span class='details-label'>Quantity</span><span class='details-value'>"
                    + count + "</span></div>" +
                    "      <div class='details-row'><span class='details-label'>Domain</span><span class='details-value'>"
                    + domain + "</span></div>" +
                    "    </div>" +
                    (cost != null
                            ? "<div class='price-row'><span class='price-label'>Total Price</span><span class='price-value'>$"
                                    + String.format("%.2f", cost) + "</span></div>"
                            : "")
                    +
                    "  </div>" +
                    "  <div class='main-message' style='margin-top: 20px;'>Your Google Workspace license purchase request has been approved and processed successfully.</div>"
                    +
                    "  <div class='footer'>Thank you for your purchase!<br>&copy; " + java.time.Year.now().getValue()
                    + " MyCLOUDMEN</div>" +
                    "</div></body></html>";

            helper.setText(htmlContent, true);

            // Just log that we're sending an email, no need to try to access potentially
            // null fields
            log.debug(
                    "Sending Google Workspace license confirmation email with subject: 'Google Workspace License Purchase Confirmation'");

            mailSender.send(message);
            log.info("Google Workspace license confirmation email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send Google Workspace license confirmation email: {}", e.getMessage(), e);
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending Google Workspace license confirmation email: {}", e.getMessage(), e);
            throw new MessagingException("Unexpected error: " + e.getMessage(), e);
        }
    }
}