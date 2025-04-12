package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteDetailDTO;
import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceListDTO;
import com.cloudmen.backend.services.TeamleaderCreditNoteService;
import com.cloudmen.backend.services.TeamleaderInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for TeamLeader finances (invoices and credit notes)
 * All financial data access requires a company/customer context for security
 */
@RestController
@RequestMapping("/api/teamleader/finance")
@RequiredArgsConstructor
@Slf4j
public class TeamleaderFinanceController {

    private final TeamleaderInvoiceService invoiceService;
    private final TeamleaderCreditNoteService creditNoteService;

    /**
     * Get both invoices and credit notes for a specific company in a single request
     * Returns a combined result with both types of financial documents
     *
     * @param customerId The TeamLeader ID of the company/customer
     * @return Map containing lists of invoices and credit notes
     */
    @GetMapping("/company/{customerId}")
    public ResponseEntity<Map<String, Object>> getFinancialDocumentsByCompany(@PathVariable String customerId) {
        log.info("Request received for all financial documents for company with ID: {}", customerId);

        // Get invoices for the company
        List<TeamleaderInvoiceListDTO> invoices = invoiceService.findByCustomerId(customerId);

        // Get all credit notes related to this company's invoices
        Map<String, List<TeamleaderCreditNoteListDTO>> creditNotesByInvoice = new HashMap<>();

        // For each invoice, fetch related credit notes
        for (TeamleaderInvoiceListDTO invoice : invoices) {
            List<TeamleaderCreditNoteListDTO> creditNotes = creditNoteService.findByInvoiceId(invoice.getId());
            if (!creditNotes.isEmpty()) {
                creditNotesByInvoice.put(invoice.getId(), creditNotes);
            }
        }

        // Create a response map with both types of documents
        Map<String, Object> result = new HashMap<>();
        result.put("invoices", invoices);
        result.put("creditNotesByInvoice", creditNotesByInvoice);

        return ResponseEntity.ok(result);
    }

    /**
     * Get invoices for a specific company, with optional filtering by status
     * 
     * @param customerId The TeamLeader ID of the company/customer
     * @param status     Optional invoice status filter
     * @return List of invoice list DTOs
     */
    @GetMapping("/company/{customerId}/invoices")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> getCompanyInvoices(
            @PathVariable String customerId,
            @RequestParam(required = false) String status) {

        log.info("Request received for invoices - company: {}, status: {}",
                customerId, status);

        // Get invoices for the specific company
        List<TeamleaderInvoiceListDTO> invoices = invoiceService.findByCustomerId(customerId);

        // If status filter is provided, filter the results
        if (status != null && !status.isEmpty()) {
            invoices = invoices.stream()
                    .filter(invoice -> status.equals(invoice.getStatus()))
                    .toList();
        }

        return ResponseEntity.ok(invoices);
    }

    /**
     * Get detailed information about a specific invoice, with security check
     * Only allows access if the invoice belongs to the specified company
     * 
     * @param customerId The TeamLeader ID of the company
     * @param invoiceId  The invoice ID
     * @return Invoice detail or 404 if not found
     */
    @GetMapping("/company/{customerId}/invoices/{invoiceId}")
    public ResponseEntity<TeamleaderInvoiceDetailDTO> getCompanyInvoiceById(
            @PathVariable String customerId,
            @PathVariable String invoiceId) {

        log.info("Request received for invoice with ID: {} for company: {}", invoiceId, customerId);

        Optional<TeamleaderInvoiceDetailDTO> invoiceOpt = invoiceService.findById(invoiceId);

        // Security check: only return if the invoice belongs to the specified company
        if (invoiceOpt.isPresent()) {
            TeamleaderInvoiceDetailDTO invoice = invoiceOpt.get();
            if (customerId.equals(invoice.getCustomerId())) {
                return ResponseEntity.ok(invoice);
            }
            log.warn("Security check failed: Invoice {} does not belong to company {}", invoiceId, customerId);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get credit notes for a specific invoice of a company
     * 
     * @param customerId The TeamLeader ID of the company
     * @param invoiceId  The invoice ID
     * @return List of credit notes
     */
    @GetMapping({ "/company/{customerId}/invoice/{invoiceId}/credit-notes",
            "/company/{customerId}/invoices/{invoiceId}/credit-notes" })
    public ResponseEntity<List<TeamleaderCreditNoteListDTO>> getCompanyInvoiceCreditNotes(
            @PathVariable String customerId,
            @PathVariable String invoiceId) {

        log.info("Request received for credit notes for invoice: {} of company: {}", invoiceId, customerId);

        // First check if the invoice belongs to the company
        Optional<TeamleaderInvoiceDetailDTO> invoiceOpt = invoiceService.findById(invoiceId);

        if (invoiceOpt.isPresent() && customerId.equals(invoiceOpt.get().getCustomerId())) {
            List<TeamleaderCreditNoteListDTO> creditNotes = creditNoteService.findByInvoiceId(invoiceId);
            return ResponseEntity.ok(creditNotes);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get detailed information about a specific credit note, with security check
     * Only allows access if the credit note is related to the specified company
     * 
     * @param customerId   The TeamLeader ID of the company
     * @param creditNoteId The credit note ID
     * @return Credit note detail or 404 if not found
     */
    @GetMapping("/company/{customerId}/credit-notes/{creditNoteId}")
    public ResponseEntity<TeamleaderCreditNoteDetailDTO> getCompanyCreditNoteById(
            @PathVariable String customerId,
            @PathVariable String creditNoteId) {

        log.info("Request received for credit note with ID: {} for company: {}", creditNoteId, customerId);

        Optional<TeamleaderCreditNoteDetailDTO> creditNoteOpt = creditNoteService.findById(creditNoteId);

        // Security check: only return if the credit note belongs to the specified
        // company
        if (creditNoteOpt.isPresent()) {
            TeamleaderCreditNoteDetailDTO creditNote = creditNoteOpt.get();
            if (customerId.equals(creditNote.getCustomerId())) {
                return ResponseEntity.ok(creditNote);
            }
            log.warn("Security check failed: Credit note {} does not belong to company {}", creditNoteId, customerId);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get company invoices filtered by date range
     * 
     * @param customerId The TeamLeader ID of the company
     * @param startDate  Start date
     * @param endDate    End date
     * @return Invoices within the specified date range for the company
     */
    @GetMapping("/company/{customerId}/invoices/date-range")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> getCompanyInvoicesByDateRange(
            @PathVariable String customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Request received for company: {} invoices in date range: {} to {}",
                customerId, startDate, endDate);

        // Use the company-specific date range method
        List<TeamleaderInvoiceListDTO> dateFilteredInvoices = invoiceService.findByCustomerAndDateRange(
                customerId, startDate, endDate);

        return ResponseEntity.ok(dateFilteredInvoices);
    }

    /**
     * Get overdue invoices for a specific company
     * 
     * @param customerId The TeamLeader ID of the company
     * @return List of overdue invoices for the company
     */
    @GetMapping("/company/{customerId}/invoices/overdue")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> getCompanyOverdueInvoices(
            @PathVariable String customerId) {

        log.info("Request received for overdue invoices for company: {}", customerId);

        // Use the company-specific overdue invoices method
        List<TeamleaderInvoiceListDTO> overdueInvoices = invoiceService.findOverdueInvoicesByCustomer(customerId);

        return ResponseEntity.ok(overdueInvoices);
    }

    /**
     * Search invoices for a specific company
     * 
     * @param customerId The TeamLeader ID of the company
     * @param term       Search term
     * @return List of matching invoices for the company
     */
    @GetMapping("/company/{customerId}/invoices/search")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> searchCompanyInvoices(
            @PathVariable String customerId,
            @RequestParam String term) {

        log.info("Request received to search invoices with term: {} for company: {}", term, customerId);

        // Use the company-specific search method
        List<TeamleaderInvoiceListDTO> searchResults = invoiceService.searchInvoicesByCustomer(term, customerId);

        return ResponseEntity.ok(searchResults);
    }

    /**
     * Get company credit notes filtered by date range
     * 
     * @param customerId The TeamLeader ID of the company
     * @param startDate  Start date
     * @param endDate    End date
     * @return Credit notes within the specified date range for the company
     */
    @GetMapping("/company/{customerId}/credit-notes/date-range")
    public ResponseEntity<List<TeamleaderCreditNoteListDTO>> getCompanyCreditNotesByDateRange(
            @PathVariable String customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Request received for company: {} credit notes in date range: {} to {}",
                customerId, startDate, endDate);

        // Use the company-specific date range method
        List<TeamleaderCreditNoteListDTO> dateFilteredCreditNotes = creditNoteService.findByCustomerAndDateRange(
                customerId, startDate, endDate, invoiceService);

        return ResponseEntity.ok(dateFilteredCreditNotes);
    }

    /**
     * Search credit notes for a specific company
     * 
     * @param customerId The TeamLeader ID of the company
     * @param term       Search term
     * @return List of matching credit notes for the company
     */
    @GetMapping("/company/{customerId}/credit-notes/search")
    public ResponseEntity<List<TeamleaderCreditNoteListDTO>> searchCompanyCreditNotes(
            @PathVariable String customerId,
            @RequestParam String term) {

        log.info("Request received to search credit notes with term: {} for company: {}", term, customerId);

        // Use the company-specific search method
        List<TeamleaderCreditNoteListDTO> searchResults = creditNoteService.searchCreditNotesByCustomer(
                term, customerId, invoiceService);

        return ResponseEntity.ok(searchResults);
    }

    /**
     * DEVELOPMENT ONLY: Debug endpoint to directly fetch credit notes for any
     * invoice
     * This endpoint bypasses security checks and should be disabled or removed in
     * production
     * 
     * @param invoiceId Invoice ID
     * @return List of credit notes
     */
    @GetMapping("/debug/invoice/{invoiceId}/credit-notes")
    public ResponseEntity<Map<String, Object>> debugInvoiceCreditNotes(@PathVariable String invoiceId) {
        log.info("DEBUG: Direct request for credit notes for invoice: {}", invoiceId);

        Map<String, Object> result = new HashMap<>();

        // Get the invoice details
        Optional<TeamleaderInvoiceDetailDTO> invoiceOpt = invoiceService.findById(invoiceId);
        if (invoiceOpt.isPresent()) {
            result.put("invoice", invoiceOpt.get());
        } else {
            result.put("invoiceError", "Invoice not found with ID: " + invoiceId);
        }

        try {
            // Get credit notes directly
            List<TeamleaderCreditNoteListDTO> creditNotes = creditNoteService.findByInvoiceId(invoiceId);
            result.put("creditNotes", creditNotes);
            result.put("creditNotesCount", creditNotes.size());
        } catch (Exception e) {
            result.put("creditNotesError", "Error fetching credit notes: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}