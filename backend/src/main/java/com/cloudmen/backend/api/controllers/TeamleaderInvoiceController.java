package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceListDTO;
import com.cloudmen.backend.services.TeamleaderInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller specifically for TeamLeader invoices
 * All financial data access requires a company/customer context for security
 */
@RestController
@RequestMapping("/api/teamleader/finance")
@RequiredArgsConstructor
@Slf4j
public class TeamleaderInvoiceController {

    private final TeamleaderInvoiceService invoiceService;

    /**
     * Get invoices for a specific company, with flexible filtering options
     * 
     * @param companyId  The TeamLeader ID of the company/customer
     * @param status     Optional filter: 'paid', 'unpaid', 'overdue', or 'all'
     * @param searchTerm Optional search term to filter invoices
     * @return List of invoice list DTOs
     */
    @GetMapping("/company/{companyId}/invoices")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> getCompanyInvoices(
            @PathVariable String companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String searchTerm) {

        log.info("Request received for invoices - company: {}, status: {}, search: {}",
                companyId, status, searchTerm);

        // Convert status parameter to boolean isPaid value (or null for all)
        Boolean isPaid = null;
        Boolean isOverdue = null;

        if (status != null && !status.isEmpty()) {
            if (status.equalsIgnoreCase("paid")) {
                isPaid = true;
            } else if (status.equalsIgnoreCase("unpaid") || status.equalsIgnoreCase("outstanding")) {
                isPaid = false;
            } else if (status.equalsIgnoreCase("overdue")) {
                isPaid = false;
                isOverdue = true;
            }
        }

        // Fetch invoices with appropriate filters
        List<TeamleaderInvoiceListDTO> invoices = invoiceService.findInvoicesByCompany(
                companyId, isPaid, isOverdue, searchTerm);

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
    public ResponseEntity<TeamleaderInvoiceDetailDTO> getCompanyInvoiceById(
            @PathVariable String companyId,
            @PathVariable String invoiceId) {

        log.info("Request received for invoice with ID: {} for company: {}", invoiceId, companyId);

        Optional<TeamleaderInvoiceDetailDTO> invoiceOpt = invoiceService.findById(invoiceId);

        if (invoiceOpt.isEmpty()) {
            log.warn("Invoice not found with ID: {}", invoiceId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(invoiceOpt.get());
    }

    /**
     * Mock endpoint that returns test data for frontend development - disabled in
     * production
     * 
     * @return 404 Not Found response to force frontend to use real API
     */
    @GetMapping("/mock/invoices")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> getMockInvoices() {
        log.info("Mock endpoints are disabled in production");
        return ResponseEntity.notFound().build();
    }

    /**
     * Mock endpoint that returns test data for a single invoice - disabled in
     * production
     * 
     * @param invoiceId The invoice ID
     * @return 404 Not Found response to force frontend to use real API
     */
    @GetMapping("/mock/invoices/{invoiceId}")
    public ResponseEntity<TeamleaderInvoiceDetailDTO> getMockInvoice(@PathVariable String invoiceId) {
        log.info("Mock endpoints are disabled in production");
        return ResponseEntity.notFound().build();
    }

    /**
     * Endpoint for testing empty unpaid invoice responses - disabled in production
     * 
     * @return 404 Not Found response to force frontend to use real API
     */
    @GetMapping("/mock/invoices/empty/unpaid")
    public ResponseEntity<List<TeamleaderInvoiceListDTO>> getMockEmptyUnpaidInvoices() {
        log.info("Mock endpoints are disabled in production");
        return ResponseEntity.notFound().build();
    }
}