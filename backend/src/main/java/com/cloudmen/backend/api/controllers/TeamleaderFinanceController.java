package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteDetailDto;
import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteListDto;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceDetailDto;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceListDto;
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
     * Returns invoices with credit note counts
     *
     * @param companyId The TeamLeader ID of the company
     * @return Map containing lists of invoices with credit note counts
     */
    @GetMapping("/company/{companyId}")
    public ResponseEntity<Map<String, Object>> getFinancialDocumentsByCompany(@PathVariable String companyId) {
        log.info("Request received for all financial documents for company with ID: {}", companyId);

        // Get invoices for the company
        List<TeamleaderInvoiceListDto> invoices = invoiceService.findByCustomerId(companyId);

        // For each invoice, count related credit notes and add to the DTO
        int totalCreditNotes = 0;
        for (TeamleaderInvoiceListDto invoice : invoices) {
            List<TeamleaderCreditNoteListDto> creditNotes = creditNoteService.findByInvoiceId(invoice.getId());
            int count = creditNotes != null ? creditNotes.size() : 0;
            invoice.setCreditNoteCount(count);
            totalCreditNotes += count;
        }

        log.info("Found {} invoices with a total of {} credit notes for company {}",
                invoices.size(), totalCreditNotes, companyId);

        // Create a response map with invoices that include credit note counts
        Map<String, Object> result = new HashMap<>();
        result.put("invoices", invoices);

        return ResponseEntity.ok(result);
    }

    /**
     * Get invoices for a specific company, with optional filtering by status
     * 
     * @param companyId The TeamLeader ID of the company
     * @param status    Optional invoice status filter
     * @return List of invoice list DTOs
     */
    @GetMapping("/company/{companyId}/invoices")
    public ResponseEntity<List<TeamleaderInvoiceListDto>> getCompanyInvoices(
            @PathVariable String companyId,
            @RequestParam(required = false) String status) {

        log.info("Request received for invoices - company: {}, status: {}", companyId, status);

        // Get invoices for the specific company
        List<TeamleaderInvoiceListDto> invoices = invoiceService.findByCustomerId(companyId);
        log.debug("Found {} raw invoices for company {}", invoices.size(), companyId);

        // For each invoice, count related credit notes and add to the DTO
        int totalCreditNotes = 0;
        int invoicesWithCreditNotes = 0;

        for (TeamleaderInvoiceListDto invoice : invoices) {
            log.debug("Processing invoice ID: {}, Number: {}", invoice.getId(), invoice.getNumber());

            // Get credit notes for this invoice
            List<TeamleaderCreditNoteListDto> creditNotes = creditNoteService.findByInvoiceId(invoice.getId());

            // Set the count (ensure we handle null)
            int count = (creditNotes != null) ? creditNotes.size() : 0;
            invoice.setCreditNoteCount(count);

            log.debug("Invoice ID: {} has {} credit notes", invoice.getId(), count);

            if (count > 0) {
                invoicesWithCreditNotes++;
                totalCreditNotes += count;

                // Log detailed info about the first credit note for debugging
                if (!creditNotes.isEmpty()) {
                    TeamleaderCreditNoteListDto firstCreditNote = creditNotes.get(0);
                    log.debug("Example credit note - ID: {}, Number: {}, Related Invoice: {}",
                            firstCreditNote.getId(),
                            firstCreditNote.getNumber(),
                            firstCreditNote.getInvoiceId());
                }
            }
        }

        log.info("Found {} invoices with {} having credit notes (total of {} credit notes) for company {}",
                invoices.size(), invoicesWithCreditNotes, totalCreditNotes, companyId);

        // Debug: log the structure of the first invoice to help with frontend debugging
        if (!invoices.isEmpty()) {
            TeamleaderInvoiceListDto example = invoices.get(0);
            log.info("Example invoice structure - ID: {}, Number: {}, Total: {}, Currency: {}, Credit Notes: {}",
                    example.getId(), example.getNumber(), example.getTotal(), example.getCurrency(),
                    example.getCreditNoteCount());
        } else {
            log.info("No invoices found for company {}", companyId);
        }

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
     * @param companyId The TeamLeader ID of the company
     * @param invoiceId The invoice ID
     * @return Invoice detail or 404 if not found
     */
    @GetMapping("/company/{companyId}/invoices/{invoiceId}")
    public ResponseEntity<TeamleaderInvoiceDetailDto> getCompanyInvoiceById(
            @PathVariable String companyId,
            @PathVariable String invoiceId) {

        log.info("Request received for invoice with ID: {} for company: {}", invoiceId, companyId);

        Optional<TeamleaderInvoiceDetailDto> invoiceOpt = invoiceService.findById(invoiceId);

        // Security check: only return if the invoice belongs to the specified company
        if (invoiceOpt.isPresent()) {
            TeamleaderInvoiceDetailDto invoice = invoiceOpt.get();
            if (companyId.equals(invoice.getCustomerId())) {
                return ResponseEntity.ok(invoice);
            }
            log.warn("Security check failed: Invoice {} does not belong to company {}", invoiceId, companyId);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get credit notes for a specific invoice of a company
     * 
     * @param companyId The TeamLeader ID of the company
     * @param invoiceId The invoice ID
     * @return List of credit notes
     */
    @GetMapping("/company/{companyId}/invoices/{invoiceId}/credit-notes")
    public ResponseEntity<List<TeamleaderCreditNoteListDto>> getCompanyInvoiceCreditNotes(
            @PathVariable String companyId,
            @PathVariable String invoiceId) {

        log.info("Request received for credit notes for invoice: {} of company: {}", invoiceId, companyId);

        // First check if the invoice belongs to the company
        Optional<TeamleaderInvoiceDetailDto> invoiceOpt = invoiceService.findById(invoiceId);

        if (!invoiceOpt.isPresent()) {
            log.warn("Invoice with ID {} not found", invoiceId);
            return ResponseEntity.notFound().build();
        }

        TeamleaderInvoiceDetailDto invoice = invoiceOpt.get();
        if (!companyId.equals(invoice.getCustomerId())) {
            log.warn("Security check failed: Invoice {} belongs to company {} not the requested company {}",
                    invoiceId, invoice.getCustomerId(), companyId);
            return ResponseEntity.notFound().build();
        }

        log.info("Security check passed. Fetching credit notes for invoice {} of company {}", invoiceId, companyId);

        // Call the service to fetch credit notes
        List<TeamleaderCreditNoteListDto> creditNotes = creditNoteService.findByInvoiceId(invoiceId);

        // Log how many credit notes were found
        log.info("Found {} credit notes for invoice {}", creditNotes.size(), invoiceId);

        // Log details of each credit note for debugging
        if (!creditNotes.isEmpty()) {
            for (int i = 0; i < creditNotes.size(); i++) {
                TeamleaderCreditNoteListDto cn = creditNotes.get(i);
                log.debug("Credit Note {}: ID={}, Number={}, InvoiceID={}",
                        i + 1, cn.getId(), cn.getNumber(), cn.getInvoiceId());
            }
        } else {
            log.debug("No credit notes found for invoice {}", invoiceId);
        }

        return ResponseEntity.ok(creditNotes);
    }

    /**
     * Get detailed information about a specific credit note, with security check
     * Only allows access if the credit note is related to the specified company
     * 
     * @param companyId    The TeamLeader ID of the company
     * @param creditNoteId The credit note ID
     * @return Credit note detail or 404 if not found
     */
    @GetMapping("/company/{companyId}/credit-notes/{creditNoteId}")
    public ResponseEntity<TeamleaderCreditNoteDetailDto> getCompanyCreditNoteById(
            @PathVariable String companyId,
            @PathVariable String creditNoteId) {

        log.info("Request received for credit note with ID: {} for company: {}", creditNoteId, companyId);

        Optional<TeamleaderCreditNoteDetailDto> creditNoteOpt = creditNoteService.findById(creditNoteId);

        // Security check: only return if the credit note belongs to the specified
        // company
        if (creditNoteOpt.isPresent()) {
            TeamleaderCreditNoteDetailDto creditNote = creditNoteOpt.get();
            if (companyId.equals(creditNote.getCustomerId())) {
                return ResponseEntity.ok(creditNote);
            }
            log.warn("Security check failed: Credit note {} does not belong to company {}", creditNoteId, companyId);
        }

        return ResponseEntity.notFound().build();
    }

    /**
     * Get company invoices filtered by date range
     * 
     * @param companyId The TeamLeader ID of the company
     * @param startDate Start date
     * @param endDate   End date
     * @return Invoices within the specified date range for the company
     */
    @GetMapping("/company/{companyId}/invoices/date-range")
    public ResponseEntity<List<TeamleaderInvoiceListDto>> getCompanyInvoicesByDateRange(
            @PathVariable String companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Request received for company: {} invoices in date range: {} to {}",
                companyId, startDate, endDate);

        // Use the company-specific date range method
        List<TeamleaderInvoiceListDto> dateFilteredInvoices = invoiceService.findByCustomerAndDateRange(
                companyId, startDate, endDate);

        return ResponseEntity.ok(dateFilteredInvoices);
    }

    /**
     * Get overdue invoices for a specific company
     * 
     * @param companyId The TeamLeader ID of the company
     * @return List of overdue invoices for the company
     */
    @GetMapping("/company/{companyId}/invoices/overdue")
    public ResponseEntity<List<TeamleaderInvoiceListDto>> getCompanyOverdueInvoices(
            @PathVariable String companyId) {

        log.info("Request received for overdue invoices for company: {}", companyId);

        // Use the company-specific overdue invoices method
        List<TeamleaderInvoiceListDto> overdueInvoices = invoiceService.findOverdueInvoicesByCustomer(companyId);

        return ResponseEntity.ok(overdueInvoices);
    }

    /**
     * Search invoices for a specific company
     * 
     * @param companyId The TeamLeader ID of the company
     * @param term      Search term
     * @return List of matching invoices for the company
     */
    @GetMapping("/company/{companyId}/invoices/search")
    public ResponseEntity<List<TeamleaderInvoiceListDto>> searchCompanyInvoices(
            @PathVariable String companyId,
            @RequestParam String term) {

        log.info("Request received to search invoices with term: {} for company: {}", term, companyId);

        // Use the company-specific search method
        List<TeamleaderInvoiceListDto> searchResults = invoiceService.searchInvoicesByCustomer(term, companyId);

        return ResponseEntity.ok(searchResults);
    }

    /**
     * Get company credit notes filtered by date range
     * 
     * @param companyId The TeamLeader ID of the company
     * @param startDate Start date
     * @param endDate   End date
     * @return Credit notes within the specified date range for the company
     */
    @GetMapping("/company/{companyId}/credit-notes/date-range")
    public ResponseEntity<List<TeamleaderCreditNoteListDto>> getCompanyCreditNotesByDateRange(
            @PathVariable String companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Request received for company: {} credit notes in date range: {} to {}",
                companyId, startDate, endDate);

        // Use the company-specific date range method
        List<TeamleaderCreditNoteListDto> dateFilteredCreditNotes = creditNoteService.findByCustomerAndDateRange(
                companyId, startDate, endDate, invoiceService);

        return ResponseEntity.ok(dateFilteredCreditNotes);
    }

    /**
     * Search credit notes for a specific company
     * 
     * @param companyId The TeamLeader ID of the company
     * @param term      Search term
     * @return List of matching credit notes for the company
     */
    @GetMapping("/company/{companyId}/credit-notes/search")
    public ResponseEntity<List<TeamleaderCreditNoteListDto>> searchCompanyCreditNotes(
            @PathVariable String companyId,
            @RequestParam String term) {

        log.info("Request received to search credit notes with term: {} for company: {}", term, companyId);

        // Use the company-specific search method
        List<TeamleaderCreditNoteListDto> searchResults = creditNoteService.searchCreditNotesByCustomer(
                term, companyId, invoiceService);

        return ResponseEntity.ok(searchResults);
    }
}