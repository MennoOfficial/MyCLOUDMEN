package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceListDTO;
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
}