package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteDetailDTO;
import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceListDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for accessing TeamLeader credit notes.
 * All data is fetched directly from the TeamLeader API without local storage.
 * 
 * SECURITY NOTE: Methods that don't require a customer/company context are
 * marked
 * as protected and should not be called directly from controllers without
 * applying proper filtering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamleaderCreditNoteService {

    private final WebClient webClient;
    private final TeamleaderOAuthService oAuthService;

    /**
     * Find all credit notes with pagination - fetches directly from TeamLeader API
     * This uses the list DTO with minimal information for better performance
     * 
     * SECURITY NOTE: This method returns all credit notes without customer
     * filtering.
     * It should not be exposed directly through controllers.
     * 
     * @param pageable Pagination information
     * @return Page of credit note list DTOs
     */
    protected Page<TeamleaderCreditNoteListDTO> findAllCreditNotes(Pageable pageable) {
        log.info("Fetching all credit notes directly from TeamLeader API - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return new PageImpl<>(Collections.emptyList());
            }

            // TeamLeader API uses 1-based pagination
            int teamleaderPage = pageable.getPageNumber() + 1;
            int pageSize = pageable.getPageSize();

            String requestBody = String.format(
                    "{\"page\":{\"size\":%d,\"number\":%d},\"sort\":[{\"field\":\"creditnote_date\",\"order\":\"desc\"}]}",
                    pageSize, teamleaderPage);

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No credit notes found or invalid response from TeamLeader API");
                return new PageImpl<>(Collections.emptyList());
            }

            List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDTO creditNote = mapToCreditNoteListDto(creditNoteNode);
                    creditNotes.add(creditNote);
                } catch (Exception e) {
                    log.error("Error parsing credit note data", e);
                }
            }

            // Get total count for pagination
            int totalCount = response.has("meta") && response.get("meta").has("count")
                    ? response.get("meta").get("count").asInt()
                    : creditNotes.size();

            return new PageImpl<>(creditNotes, pageable, totalCount);
        } catch (Exception e) {
            log.error("Error fetching credit notes from TeamLeader API", e);
            return new PageImpl<>(Collections.emptyList());
        }
    }

    /**
     * Find credit note by ID - fetches detailed information directly from
     * TeamLeader API
     * 
     * Note: This method returns a single credit note by ID. Customer validation
     * should be performed
     * at the controller level.
     * 
     * @param id Credit note ID
     * @return Optional containing the detailed credit note if found
     */
    public Optional<TeamleaderCreditNoteDetailDTO> findById(String id) {
        log.info("Fetching credit note by ID directly from TeamLeader API - id: {}", id);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Optional.empty();
            }

            JsonNode detailedCreditNote = fetchCreditNoteDetails(id, accessToken);
            if (detailedCreditNote == null || !detailedCreditNote.has("data")) {
                return Optional.empty();
            }

            return Optional.of(mapToCreditNoteDetailDto(detailedCreditNote.get("data")));
        } catch (Exception e) {
            log.error("Error fetching credit note by ID from TeamLeader API", e);
            return Optional.empty();
        }
    }

    /**
     * Find credit notes by invoice ID - fetches directly from TeamLeader API
     * 
     * Note: This method returns credit notes for a specific invoice. It's safe to
     * use
     * as long as the invoice ID has been validated to belong to the correct
     * customer.
     * 
     * @param invoiceId Invoice ID
     * @return List of credit note list DTOs
     */
    public List<TeamleaderCreditNoteListDTO> findByInvoiceId(String invoiceId) {
        if (invoiceId == null || invoiceId.isEmpty()) {
            log.error("Cannot find credit notes: invoice ID is null or empty");
            return Collections.emptyList();
        }

        log.info("Fetching credit notes for invoice ID: {}", invoiceId);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            // According to TeamLeader API docs, use filter.invoice_id
            String requestBody = String.format(
                    "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"invoice_id\":\"%s\"}}",
                    invoiceId);

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // If there's an API error or no results, try alternative format
            if (response == null || !response.has("data") || response.get("data").size() == 0) {
                log.warn(
                        "No credit notes found for invoice ID: {} using direct filter. Trying alternative format.",
                        invoiceId);

                // Try alternative format with invoice object
                String alternativeRequestBody = String.format(
                        "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"invoice\":{\"type\":\"invoice\",\"id\":\"%s\"}}}",
                        invoiceId);

                try {
                    response = webClient.post()
                            .uri("/creditNotes.list")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json")
                            .bodyValue(alternativeRequestBody)
                            .retrieve()
                            .bodyToMono(JsonNode.class)
                            .block();

                    if (response == null || !response.has("data") || response.get("data").size() == 0) {
                        log.warn(
                                "Still no credit notes found with alternative format. Falling back to manual filtering.");
                        return findAllAndFilterByInvoiceId(invoiceId, accessToken);
                    }
                } catch (Exception altError) {
                    log.error("Error with alternative request format: {}", altError.getMessage());
                    return findAllAndFilterByInvoiceId(invoiceId, accessToken);
                }
            }

            List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {

                    TeamleaderCreditNoteListDTO creditNote = mapToCreditNoteListDto(creditNoteNode);

                    // Check if the credit note is correctly related to this invoice
                    boolean isValid = creditNote != null && invoiceId.equals(creditNote.getInvoiceId());

                    // If no match yet, try checking the raw JSON directly
                    if (!isValid && creditNoteNode.has("invoice") && creditNoteNode.get("invoice").has("id")) {
                        String rawInvoiceId = creditNoteNode.get("invoice").get("id").asText();
                        isValid = invoiceId.equals(rawInvoiceId);
                        if (isValid) {
                            // Update the credit note's invoiceId if matched via raw JSON
                            creditNote.setInvoiceId(rawInvoiceId);
                        }
                    }

                    if (isValid) {
                        creditNotes.add(creditNote);
                    } else {
                        log.warn("Skipped credit note {} because it's not related to invoice {}",
                                creditNote != null ? creditNote.getId() : "null", invoiceId);
                    }
                } catch (Exception e) {
                    log.error("Error parsing credit note data", e);
                }
            }

            log.info("Returning {} actual credit notes for invoice {}", creditNotes.size(), invoiceId);
            return creditNotes;
        } catch (Exception e) {
            log.error("Error fetching credit notes by invoice ID from TeamLeader API: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Helper method to find credit notes by getting all and manually filtering by
     * invoice ID
     */
    private List<TeamleaderCreditNoteListDTO> findAllAndFilterByInvoiceId(String invoiceId, String accessToken) {
        try {
            // Get all credit notes (limited to reasonable batch)
            String requestBody = "{\"page\":{\"size\":100,\"number\":1}}";

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No credit notes found at all when searching for invoice ID: {}", invoiceId);
                return Collections.emptyList();
            }

            List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDTO creditNote = mapToCreditNoteListDto(creditNoteNode);

                    // Check if this credit note relates to our invoice
                    if (creditNote != null && invoiceId.equals(creditNote.getInvoiceId())) {
                        creditNotes.add(creditNote);
                    } else if (creditNoteNode.has("invoice") && creditNoteNode.get("invoice").has("id") &&
                            invoiceId.equals(creditNoteNode.get("invoice").get("id").asText())) {
                        // Direct JSON check
                        creditNote.setInvoiceId(invoiceId);
                        creditNotes.add(creditNote);

                    }
                } catch (Exception e) {
                    log.error("Error processing credit note in manual filtering: {}", e.getMessage());
                }
            }

            log.info("Manual filtering found {} credit notes for invoice {}", creditNotes.size(), invoiceId);
            return creditNotes;
        } catch (Exception e) {
            log.error("Error in manual filtering of credit notes: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Find credit notes by date range - fetches directly from TeamLeader API
     * 
     * SECURITY NOTE: This method returns credit notes filtered by date but not by
     * customer.
     * It should not be exposed directly through controllers.
     * 
     * @param startDate Start date
     * @param endDate   End date
     * @return List of credit note list DTOs
     */
    protected List<TeamleaderCreditNoteListDTO> findByDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching credit notes by date range directly from TeamLeader API - from: {} to: {}",
                startDate, endDate);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            String requestBody = String.format(
                    "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"creditnote_date\":{\"from\":\"%s\",\"until\":\"%s\"}}}",
                    startDate.toString(), endDate.toString());

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No credit notes found in date range: {} to {}", startDate, endDate);
                return Collections.emptyList();
            }

            List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDTO creditNote = mapToCreditNoteListDto(creditNoteNode);
                    creditNotes.add(creditNote);
                } catch (Exception e) {
                    log.error("Error parsing credit note data", e);
                }
            }

            return creditNotes;
        } catch (Exception e) {
            log.error("Error fetching credit notes by date range from TeamLeader API", e);
            return Collections.emptyList();
        }
    }

    /**
     * Search credit notes by term - fetches directly from TeamLeader API
     * 
     * SECURITY NOTE: This method returns all matching credit notes without customer
     * filtering.
     * It should not be exposed directly through controllers.
     * 
     * @param term Search term
     * @return List of credit note list DTOs
     */
    protected List<TeamleaderCreditNoteListDTO> searchCreditNotes(String term) {
        log.info("Searching credit notes directly from TeamLeader API - term: {}", term);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            String requestBody = String.format(
                    "{\"page\":{\"size\":50,\"number\":1},\"filter\":{\"term\":\"%s\"}}",
                    term);

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No credit notes found matching term: {}", term);
                return Collections.emptyList();
            }

            List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDTO creditNote = mapToCreditNoteListDto(creditNoteNode);
                    creditNotes.add(creditNote);
                } catch (Exception e) {
                    log.error("Error parsing credit note data", e);
                }
            }

            return creditNotes;
        } catch (Exception e) {
            log.error("Error searching credit notes from TeamLeader API", e);
            return Collections.emptyList();
        }
    }

    /**
     * Find all credit notes for a specific company by collecting them from the
     * company's invoices
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param companyId      Company ID
     * @param invoiceService Invoice service to fetch company invoices
     * @return List of credit note list DTOs for the specified company
     */
    public List<TeamleaderCreditNoteListDTO> findByCustomerId(String companyId,
            TeamleaderInvoiceService invoiceService) {
        log.info("Fetching credit notes for company ID: {}", companyId);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            // First try direct API filtering by customer ID using the proper format
            String requestBody = String.format(
                    "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"customer\":{\"type\":\"company\",\"id\":\"%s\"}}}",
                    companyId);

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // If successful, process directly
            if (response != null && response.has("data") && response.get("data").size() > 0) {
                List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();
                log.info("Found {} credit notes directly for company ID {}", response.get("data").size(), companyId);

                for (JsonNode creditNoteNode : response.get("data")) {
                    try {
                        TeamleaderCreditNoteListDTO creditNote = mapToCreditNoteListDto(creditNoteNode);
                        creditNotes.add(creditNote);
                    } catch (Exception e) {
                        log.error("Error parsing credit note data", e);
                    }
                }

                return creditNotes;
            }

            log.warn("No credit notes found with direct company filter. Falling back to finding by invoices.");
        } catch (Exception e) {
            log.error("Error fetching credit notes directly by company ID: {}", e.getMessage());
            log.info("Falling back to finding by invoices.");
        }

        // Fall back to original approach: Get credit notes via company's invoices
        // First get all invoices for this company
        List<TeamleaderInvoiceListDTO> companyInvoices = invoiceService.findByCustomerId(companyId);

        // Then collect all credit notes for these invoices
        List<TeamleaderCreditNoteListDTO> allCreditNotes = new ArrayList<>();

        for (TeamleaderInvoiceListDTO invoice : companyInvoices) {
            List<TeamleaderCreditNoteListDTO> creditNotes = findByInvoiceId(invoice.getId());
            if (!creditNotes.isEmpty()) {
                allCreditNotes.addAll(creditNotes);
            }
        }

        return allCreditNotes;
    }

    /**
     * Search credit notes for a specific company's invoices
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param term           Search term
     * @param companyId      Company ID
     * @param invoiceService Invoice service to fetch company invoices
     * @return List of matching credit notes for the company
     */
    public List<TeamleaderCreditNoteListDTO> searchCreditNotesByCustomer(String term, String companyId,
            TeamleaderInvoiceService invoiceService) {
        log.info("Searching credit notes for company {} with term: {}", companyId, term);

        // First get all credit notes for this company
        List<TeamleaderCreditNoteListDTO> companyCreditNotes = findByCustomerId(companyId, invoiceService);

        // Then filter by search term
        if (term == null || term.isEmpty()) {
            return companyCreditNotes;
        }

        String searchTermLower = term.toLowerCase();
        return companyCreditNotes.stream()
                .filter(creditNote -> (creditNote.getNumber() != null
                        && creditNote.getNumber().toLowerCase().contains(searchTermLower)) ||
                        (creditNote.getStatus() != null
                                && creditNote.getStatus().toLowerCase().contains(searchTermLower))
                        ||
                        (creditNote.getInvoiceNumber() != null
                                && creditNote.getInvoiceNumber().toLowerCase().contains(searchTermLower)))
                .collect(Collectors.toList());
    }

    /**
     * Find credit notes for a specific company in a date range
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param companyId      Company ID
     * @param startDate      Start date
     * @param endDate        End date
     * @param invoiceService Invoice service to fetch company invoices
     * @return List of credit notes that fall within the date range and belong to
     *         the company
     */
    public List<TeamleaderCreditNoteListDTO> findByCustomerAndDateRange(
            String companyId, LocalDate startDate, LocalDate endDate, TeamleaderInvoiceService invoiceService) {
        log.info("Fetching credit notes for company {} in date range: {} to {}", companyId, startDate, endDate);

        // First get all credit notes for this company
        List<TeamleaderCreditNoteListDTO> companyCreditNotes = findByCustomerId(companyId, invoiceService);

        // Then filter by date range
        return companyCreditNotes.stream()
                .filter(creditNote -> {
                    LocalDate creditNoteDate = creditNote.getDate();
                    return creditNoteDate != null &&
                            (creditNoteDate.isEqual(startDate) || creditNoteDate.isAfter(startDate)) &&
                            (creditNoteDate.isEqual(endDate) || creditNoteDate.isBefore(endDate));
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper method to fetch detailed credit note information
     * 
     * @param creditNoteId Credit note ID
     * @param accessToken  OAuth access token
     * @return JsonNode containing detailed credit note data or null if not found
     */
    private JsonNode fetchCreditNoteDetails(String creditNoteId, String accessToken) {
        try {
            String requestBody = String.format("{\"id\":\"%s\"}", creditNoteId);

            return webClient.post()
                    .uri("/creditNotes.info")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            log.error("Error fetching detailed credit note information for ID: {}", creditNoteId, e);
            return null;
        }
    }

    /**
     * Helper method to map credit note data to list DTO
     * 
     * @param node JSON node containing credit note data
     * @return TeamleaderCreditNoteListDTO
     */
    private TeamleaderCreditNoteListDTO mapToCreditNoteListDto(JsonNode node) {
        TeamleaderCreditNoteListDTO dto = new TeamleaderCreditNoteListDTO();

        dto.setId(getTextOrNull(node, "id"));
        dto.setNumber(getTextOrNull(node, "number"));

        if (node.has("date") && !node.get("date").isNull()) {
            dto.setDate(LocalDate.parse(node.get("date").asText()));
        }

        dto.setStatus(getTextOrNull(node, "status"));

        // Initialize total to zero by default
        dto.setTotal(BigDecimal.ZERO);

        // Parse total amount if present
        if (node.has("total")) {
            JsonNode total = node.get("total");
            if (total.has("amount") && !total.get("amount").isNull()) {
                dto.setTotal(new BigDecimal(total.get("amount").asText()));
            }
            if (total.has("currency") && !total.get("currency").isNull()) {
                dto.setCurrency(total.get("currency").asText());
            }
        }

        // Parse customer info if available
        if (node.has("customer")) {
            JsonNode customer = node.get("customer");
            if (customer.has("name") && !customer.get("name").isNull()) {
                dto.setCustomerName(customer.get("name").asText());
            }
        }

        // Parse related invoice info if available
        if (node.has("for_invoice")) {
            JsonNode invoice = node.get("for_invoice");
            dto.setInvoiceId(getTextOrNull(invoice, "id"));

            // Enhanced extraction of invoice number
            String invoiceNumber = getTextOrNull(invoice, "number");
            if (invoiceNumber == null || invoiceNumber.isEmpty()) {
                // Try alternative field
                invoiceNumber = getTextOrNull(invoice, "invoice_number");
                if (invoiceNumber == null || invoiceNumber.isEmpty() && dto.getInvoiceId() != null) {
                    // Use invoice ID as a last resort
                    invoiceNumber = dto.getInvoiceId();
                }
            }
            dto.setInvoiceNumber(invoiceNumber);
        } else if (node.has("invoice")) {
            // Handle the case where the field is named 'invoice' instead of 'for_invoice'
            JsonNode invoice = node.get("invoice");
            dto.setInvoiceId(getTextOrNull(invoice, "id"));

            // Enhanced extraction of invoice number
            String invoiceNumber = getTextOrNull(invoice, "number");
            if (invoiceNumber == null || invoiceNumber.isEmpty()) {
                // Try alternative field
                invoiceNumber = getTextOrNull(invoice, "invoice_number");
                if (invoiceNumber == null || invoiceNumber.isEmpty() && dto.getInvoiceId() != null) {
                    // Use invoice ID as a last resort
                    invoiceNumber = dto.getInvoiceId();
                }
            }
            dto.setInvoiceNumber(invoiceNumber);
        }

        return dto;
    }

    /**
     * Helper method to map credit note data to detail DTO
     * 
     * @param node JSON node containing credit note data
     * @return TeamleaderCreditNoteDetailDTO
     */
    private TeamleaderCreditNoteDetailDTO mapToCreditNoteDetailDto(JsonNode node) {
        // Create a new TeamleaderCreditNoteDetailDTO instance
        TeamleaderCreditNoteDetailDTO dto = new TeamleaderCreditNoteDetailDTO();

        // Map basic fields from JSON to DTO
        dto.setId(getTextOrNull(node, "id"));
        dto.setNumber(getTextOrNull(node, "number"));

        // Map dates
        if (node.has("date") && !node.get("date").isNull()) {
            dto.setDate(LocalDate.parse(node.get("date").asText()));
        }

        if (node.has("due_on") && !node.get("due_on").isNull()) {
            dto.setDueOn(LocalDate.parse(node.get("due_on").asText()));
        }

        // Map status
        dto.setStatus(getTextOrNull(node, "status"));

        // Initialize total to zero by default
        dto.setTotal(BigDecimal.ZERO);

        // Parse total amount if present
        if (node.has("total")) {
            JsonNode total = node.get("total");
            if (total.has("amount") && !total.get("amount").isNull()) {
                dto.setTotal(new BigDecimal(total.get("amount").asText()));
            }
            if (total.has("currency") && !total.get("currency").isNull()) {
                dto.setCurrency(total.get("currency").asText());
            }
        }

        // Parse related invoice info if available
        if (node.has("for_invoice")) {
            JsonNode invoice = node.get("for_invoice");
            dto.setInvoiceId(getTextOrNull(invoice, "id"));
            dto.setInvoiceNumber(getTextOrNull(invoice, "number"));
        }

        // Extract payment reference if available
        if (node.has("payment_reference") && !node.get("payment_reference").isNull()) {
            dto.setPaymentReference(node.get("payment_reference").asText());
        }

        // Set payment details if available
        if (node.has("paid") && !node.get("paid").isNull()) {
            dto.setPaid(node.get("paid").asBoolean());
        }

        if (node.has("paid_at") && !node.get("paid_at").isNull()) {
            try {
                dto.setPaidAt(ZonedDateTime.parse(node.get("paid_at").asText()));
            } catch (Exception e) {
                // If parsing as ZonedDateTime fails, try as LocalDate
                try {
                    LocalDate paidDate = LocalDate.parse(node.get("paid_at").asText());
                    dto.setPaidAt(paidDate.atStartOfDay(ZoneId.systemDefault()));
                } catch (Exception ex) {
                    log.warn("Failed to parse paid_at date: {}", node.get("paid_at").asText());
                }
            }
        }

        // Parse customer info if available
        if (node.has("customer")) {
            JsonNode customer = node.get("customer");
            dto.setCustomerId(getTextOrNull(customer, "id"));
            dto.setCustomerType(getTextOrNull(customer, "type"));
            dto.setCustomerName(getTextOrNull(customer, "name"));
        }

        // Set department info if available
        if (node.has("department")) {
            JsonNode department = node.get("department");
            dto.setDepartmentId(getTextOrNull(department, "id"));
            dto.setDepartmentName(getTextOrNull(department, "name"));
        }

        // Set credit note lines if available
        if (node.has("items") && node.get("items").isArray()) {
            List<TeamleaderCreditNoteDetailDTO.CreditNoteLineDTO> lines = new ArrayList<>();

            // Process each line item
            for (JsonNode lineNode : node.get("items")) {
                TeamleaderCreditNoteDetailDTO.CreditNoteLineDTO lineItem = new TeamleaderCreditNoteDetailDTO.CreditNoteLineDTO();

                // Map line item properties
                lineItem.setDescription(getTextOrNull(lineNode, "description"));
                lineItem.setUnit(getTextOrNull(lineNode, "unit"));

                if (lineNode.has("quantity") && !lineNode.get("quantity").isNull()) {
                    lineItem.setQuantity(new BigDecimal(lineNode.get("quantity").asText()));
                }

                if (lineNode.has("unit_price") && !lineNode.get("unit_price").isNull()) {
                    lineItem.setUnitPrice(new BigDecimal(lineNode.get("unit_price").asText()));
                }

                if (lineNode.has("total") && !lineNode.get("total").isNull()) {
                    lineItem.setTotalPrice(new BigDecimal(lineNode.get("total").asText()));
                }

                // Add tax info if available
                if (lineNode.has("tax_rate") && !lineNode.get("tax_rate").isNull()) {
                    JsonNode taxRate = lineNode.get("tax_rate");
                    if (taxRate.has("rate") && !taxRate.get("rate").isNull()) {
                        lineItem.setTaxRate(new BigDecimal(taxRate.get("rate").asText()));
                    }
                }

                // Add product info if available
                if (lineNode.has("product") && !lineNode.get("product").isNull()) {
                    JsonNode product = lineNode.get("product");
                    lineItem.setProductId(getTextOrNull(product, "id"));
                }

                lines.add(lineItem);
            }

            // Set the lines collection on the DTO
            dto.setLines(lines);
        }

        // Set metadata if available
        if (node.has("created_at") && !node.get("created_at").isNull()) {
            dto.setCreatedAt(parseZonedDateTime(node.get("created_at").asText()));
        }

        if (node.has("updated_at") && !node.get("updated_at").isNull()) {
            dto.setUpdatedAt(parseZonedDateTime(node.get("updated_at").asText()));
        }

        return dto;
    }

    /**
     * Helper method to safely get text from a JsonNode
     * 
     * @param node      JsonNode to extract from
     * @param fieldName Field name to extract
     * @return String value or null if not found
     */
    private String getTextOrNull(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }

    /**
     * Helper method to safely parse ZonedDateTime
     * 
     * @param dateTimeStr Date/time string
     * @return ZonedDateTime or null if parsing fails
     */
    private ZonedDateTime parseZonedDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }

        try {
            return ZonedDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e) {
            log.warn("Error parsing ZonedDateTime: {}", dateTimeStr, e);
            return null;
        }
    }
}