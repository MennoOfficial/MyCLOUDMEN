package com.cloudmen.backend.api.controllers;

import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.services.TeamleaderCreditNoteService;
import com.cloudmen.backend.services.TeamleaderInvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        // First check if the invoice belongs to the company (security check)
        Optional<TeamleaderInvoiceDetailDTO> invoiceOpt = invoiceService.findById(invoiceId);

        if (invoiceOpt.isPresent() && customerId.equals(invoiceOpt.get().getCustomerId())) {
            List<TeamleaderCreditNoteListDTO> creditNotes = creditNoteService.findByInvoiceId(invoiceId);
            return ResponseEntity.ok(creditNotes);
        }

        return ResponseEntity.notFound().build();
    }
}