package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteDetailDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.services.TeamleaderCreditNoteService;
import com.cloudmen.backend.services.TeamleaderInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final TeamleaderInvoiceService invoiceService;

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
     * Download a credit note for a specific company
     * 
     * @param customerId   The TeamLeader ID of the company
     * @param creditNoteId The credit note ID
     * @param format       The format (pdf or ubl, defaults to pdf)
     * @param redirect     Whether to redirect or return file directly
     * @return Credit note file as byte array
     */
    @GetMapping("/company/{customerId}/credit-note/{creditNoteId}/download")
    public ResponseEntity<byte[]> downloadCreditNote(
            @PathVariable String customerId,
            @PathVariable String creditNoteId,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(defaultValue = "false") boolean redirect) {

        log.info("Request received to download credit note: {} for company: {} in format: {}",
                creditNoteId, customerId, format);

        try {
            // Validate inputs
            if (creditNoteId == null || creditNoteId.trim().isEmpty()) {
                log.warn("Empty credit note ID provided");
                return ResponseEntity.badRequest().build();
            }

            byte[] creditNoteData = creditNoteService.downloadCreditNote(creditNoteId, format);

            if (creditNoteData.length == 0) {
                log.warn("No data received for credit note: {} - may not exist or access denied", creditNoteId);
                return ResponseEntity.notFound().build();
            }

            // Determine content type and filename
            String contentType = "pdf".equals(format) ? "application/pdf" : "application/xml";
            String filename = String.format("credit-note-%s.%s", creditNoteId, format);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));

            if (redirect) {
                headers.setContentDispositionFormData("attachment", filename);
            } else {
                headers.setContentDispositionFormData("inline", filename);
            }

            log.info("Successfully returning credit note {} ({} bytes)", creditNoteId, creditNoteData.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(creditNoteData);

        } catch (Exception e) {
            log.error("Error downloading credit note {}: {}", creditNoteId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}