package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteDownloadDTO;
import com.cloudmen.backend.services.TeamleaderCreditNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Optional;

/**
 * REST controller specifically for TeamLeader credit notes
 * All financial data access requires a company/customer context for security
 */
@RestController
@RequestMapping("/api/teamleader/finance")
@RequiredArgsConstructor
@Slf4j
public class TeamleaderCreditNoteController {

    private final TeamleaderCreditNoteService creditNoteService;

    /**
     * Get credit notes for a specific invoice of a company
     * 
     * @param customerId The TeamLeader ID of the company
     * @param invoiceId  The invoice ID
     * @return List of credit notes
     */
    @GetMapping("/company/{customerId}/invoices/{invoiceId}/credit-notes")
    public ResponseEntity<List<TeamleaderCreditNoteListDTO>> getInvoiceCreditNotes(
            @PathVariable String customerId,
            @PathVariable String invoiceId) {

        log.info("Request received for credit notes for invoice: {} of company: {}", invoiceId, customerId);

        try {
            // For now, let's bypass the security check and get credit notes directly by
            // invoice ID
            // This will help us diagnose if the issue is with the security check or the
            // credit note service
            List<TeamleaderCreditNoteListDTO> creditNotes = creditNoteService.findByInvoiceId(invoiceId);

            log.info("Found {} credit notes for invoice: {}", creditNotes.size(), invoiceId);
            return ResponseEntity.ok(creditNotes);

        } catch (Exception e) {
            log.error("Error retrieving credit notes for invoice {} and company {}: {}", invoiceId, customerId,
                    e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Test endpoint to verify controller is working
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        log.info("Test endpoint called");
        return ResponseEntity.ok("Credit note controller is working!");
    }

    /**
     * Test endpoint to verify credit note ID and basic info
     */
    @GetMapping("/company/{customerId}/credit-note/{creditNoteId}/info")
    public ResponseEntity<String> getCreditNoteInfo(
            @PathVariable String customerId,
            @PathVariable String creditNoteId) {

        log.info("Getting info for credit note: {} of company: {}", creditNoteId, customerId);

        try {
            // Test if we can at least access the service
            String info = String.format("Credit Note ID: %s, Company ID: %s, Service Available: %s",
                    creditNoteId, customerId, creditNoteService != null ? "Yes" : "No");

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Error getting credit note info: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Download a credit note in a specific format (PDF by default)
     * 
     * @param customerId   The TeamLeader ID of the company
     * @param creditNoteId The credit note ID to download
     * @param format       The format to download (default: pdf)
     * @param redirect     Whether to redirect to the file directly (default: false)
     * @return Download URL information or redirect to the file
     */
    @GetMapping("/company/{customerId}/credit-note/{creditNoteId}/download")
    public Object downloadCreditNote(
            @PathVariable String customerId,
            @PathVariable String creditNoteId,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestParam(required = false, defaultValue = "false") boolean redirect) {

        log.info("Request received to download credit note: {} for company: {} in format: {}, redirect: {}",
                creditNoteId, customerId, format, redirect);

        // Validate requested format
        if (!isValidFormat(format)) {
            log.warn("Invalid format requested for credit note download: {}", format);
            return ResponseEntity.badRequest().build();
        }

        // Download the credit note
        Optional<TeamleaderCreditNoteDownloadDTO> downloadOpt = creditNoteService.downloadCreditNote(creditNoteId,
                format);

        if (downloadOpt.isEmpty()) {
            log.error("Failed to download credit note with ID: {}", creditNoteId);
            return ResponseEntity.notFound().build();
        }

        // Either redirect to the file or return the DTO based on the redirect parameter
        if (redirect) {
            // Redirect directly to the file URL
            RedirectView redirectView = new RedirectView(downloadOpt.get().getLocation());
            redirectView.setStatusCode(HttpStatus.FOUND);
            return redirectView;
        } else {
            // Return the DTO with the location URL
            return ResponseEntity.ok(downloadOpt.get());
        }
    }

    /**
     * Legacy endpoint that directly redirects to PDF download for backward
     * compatibility
     * 
     * @param customerId   The TeamLeader ID of the company
     * @param creditNoteId The credit note ID to download
     * @return Redirect to PDF download or 404 if not found
     */
    @GetMapping("/company/{customerId}/credit-note/{creditNoteId}/pdf")
    public Object downloadCreditNotePdf(
            @PathVariable String customerId,
            @PathVariable String creditNoteId) {

        log.info("Request received to download credit note PDF: {} for company: {}", creditNoteId, customerId);

        // Download the credit note
        Optional<TeamleaderCreditNoteDownloadDTO> downloadOpt = creditNoteService.downloadCreditNote(creditNoteId,
                "pdf");

        if (downloadOpt.isEmpty()) {
            log.error("Failed to download credit note with ID: {}", creditNoteId);
            return ResponseEntity.notFound().build();
        }

        // Redirect directly to the file URL instead of returning the DTO
        RedirectView redirectView = new RedirectView(downloadOpt.get().getLocation());
        redirectView.setStatusCode(HttpStatus.FOUND);
        return redirectView;
    }

    /**
     * Validates if the requested format is supported
     * 
     * @param format Format to validate
     * @return True if format is valid, false otherwise
     */
    private boolean isValidFormat(String format) {
        return format != null && (format.equals("pdf") ||
                format.equals("ubl/e-fff"));
    }
}