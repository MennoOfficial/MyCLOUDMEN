package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteDetailDto;
import com.cloudmen.backend.api.dtos.TeamleaderCreditNoteListDto;
import com.cloudmen.backend.api.dtos.TeamleaderInvoiceListDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final ObjectMapper objectMapper;

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
    protected Page<TeamleaderCreditNoteListDto> findAllCreditNotes(Pageable pageable) {
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

            List<TeamleaderCreditNoteListDto> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDto creditNote = mapToCreditNoteListDto(creditNoteNode);
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
    public Optional<TeamleaderCreditNoteDetailDto> findById(String id) {
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
    public List<TeamleaderCreditNoteListDto> findByInvoiceId(String invoiceId) {
        log.info("Fetching credit notes by invoice ID directly from TeamLeader API - invoice ID: {}", invoiceId);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            String requestBody = String.format(
                    "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"invoice\":{\"id\":\"%s\"}}}",
                    invoiceId);

            JsonNode response = webClient.post()
                    .uri("/creditNotes.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No credit notes found for invoice ID: {}", invoiceId);
                return Collections.emptyList();
            }

            List<TeamleaderCreditNoteListDto> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDto creditNote = mapToCreditNoteListDto(creditNoteNode);
                    creditNotes.add(creditNote);
                } catch (Exception e) {
                    log.error("Error parsing credit note data", e);
                }
            }

            return creditNotes;
        } catch (Exception e) {
            log.error("Error fetching credit notes by invoice ID from TeamLeader API", e);
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
    protected List<TeamleaderCreditNoteListDto> findByDateRange(LocalDate startDate, LocalDate endDate) {
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

            List<TeamleaderCreditNoteListDto> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDto creditNote = mapToCreditNoteListDto(creditNoteNode);
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
    protected List<TeamleaderCreditNoteListDto> searchCreditNotes(String term) {
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

            List<TeamleaderCreditNoteListDto> creditNotes = new ArrayList<>();

            for (JsonNode creditNoteNode : response.get("data")) {
                try {
                    TeamleaderCreditNoteListDto creditNote = mapToCreditNoteListDto(creditNoteNode);
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
     * Find all credit notes for a specific customer by collecting them from the
     * customer's invoices
     * This method is safe to expose through controllers as it filters by customer
     * context.
     * 
     * @param customerId     Customer ID
     * @param invoiceService Invoice service to fetch customer invoices
     * @return List of credit note list DTOs for the specified customer
     */
    public List<TeamleaderCreditNoteListDto> findByCustomerId(String customerId,
            TeamleaderInvoiceService invoiceService) {
        log.info("Fetching credit notes for customer ID: {}", customerId);

        // First get all invoices for this customer
        List<TeamleaderInvoiceListDto> customerInvoices = invoiceService.findByCustomerId(customerId);

        // Then collect all credit notes for these invoices
        List<TeamleaderCreditNoteListDto> allCreditNotes = new ArrayList<>();

        for (TeamleaderInvoiceListDto invoice : customerInvoices) {
            List<TeamleaderCreditNoteListDto> creditNotes = findByInvoiceId(invoice.getId());
            if (!creditNotes.isEmpty()) {
                allCreditNotes.addAll(creditNotes);
            }
        }

        return allCreditNotes;
    }

    /**
     * Search credit notes for a specific customer's invoices
     * This method is safe to expose through controllers as it filters by customer
     * context.
     * 
     * @param term           Search term
     * @param customerId     Customer ID
     * @param invoiceService Invoice service to fetch customer invoices
     * @return List of matching credit notes for the customer
     */
    public List<TeamleaderCreditNoteListDto> searchCreditNotesByCustomer(String term, String customerId,
            TeamleaderInvoiceService invoiceService) {
        log.info("Searching credit notes for customer {} with term: {}", customerId, term);

        // First get all credit notes for this customer
        List<TeamleaderCreditNoteListDto> customerCreditNotes = findByCustomerId(customerId, invoiceService);

        // Then filter by search term
        if (term == null || term.isEmpty()) {
            return customerCreditNotes;
        }

        String searchTermLower = term.toLowerCase();
        return customerCreditNotes.stream()
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
     * Find credit notes for a specific customer in a date range
     * This method is safe to expose through controllers as it filters by customer
     * context.
     * 
     * @param customerId     Customer ID
     * @param startDate      Start date
     * @param endDate        End date
     * @param invoiceService Invoice service to fetch customer invoices
     * @return List of credit notes that fall within the date range and belong to
     *         the customer
     */
    public List<TeamleaderCreditNoteListDto> findByCustomerAndDateRange(
            String customerId, LocalDate startDate, LocalDate endDate, TeamleaderInvoiceService invoiceService) {
        log.info("Fetching credit notes for customer {} in date range: {} to {}", customerId, startDate, endDate);

        // First get all credit notes for this customer
        List<TeamleaderCreditNoteListDto> customerCreditNotes = findByCustomerId(customerId, invoiceService);

        // Then filter by date range
        return customerCreditNotes.stream()
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
     * @return TeamleaderCreditNoteListDto
     */
    private TeamleaderCreditNoteListDto mapToCreditNoteListDto(JsonNode node) {
        TeamleaderCreditNoteListDto dto = new TeamleaderCreditNoteListDto();

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
            dto.setInvoiceNumber(getTextOrNull(invoice, "number"));
        }

        return dto;
    }

    /**
     * Helper method to map credit note data to detail DTO
     * 
     * @param node JSON node containing credit note data
     * @return TeamleaderCreditNoteDetailDto
     */
    private TeamleaderCreditNoteDetailDto mapToCreditNoteDetailDto(JsonNode node) {
        TeamleaderCreditNoteDetailDto dto = new TeamleaderCreditNoteDetailDto();

        dto.setId(getTextOrNull(node, "id"));
        dto.setNumber(getTextOrNull(node, "number"));

        if (node.has("date") && !node.get("date").isNull()) {
            dto.setDate(LocalDate.parse(node.get("date").asText()));
        }

        if (node.has("due_on") && !node.get("due_on").isNull()) {
            dto.setDueOn(LocalDate.parse(node.get("due_on").asText()));
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

        // Parse related invoice info if available
        if (node.has("for_invoice")) {
            JsonNode invoice = node.get("for_invoice");
            dto.setInvoiceId(getTextOrNull(invoice, "id"));
            dto.setInvoiceNumber(getTextOrNull(invoice, "number"));
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
            List<TeamleaderCreditNoteDetailDto.CreditNoteLineDto> lines = new ArrayList<>();
            for (JsonNode lineNode : node.get("items")) {
                TeamleaderCreditNoteDetailDto.CreditNoteLineDto line = new TeamleaderCreditNoteDetailDto.CreditNoteLineDto();

                line.setDescription(getTextOrNull(lineNode, "description"));

                if (lineNode.has("quantity") && !lineNode.get("quantity").isNull()) {
                    line.setQuantity(new BigDecimal(lineNode.get("quantity").asText()));
                }

                line.setUnit(getTextOrNull(lineNode, "unit"));

                if (lineNode.has("unit_price") && !lineNode.get("unit_price").isNull()) {
                    line.setUnitPrice(new BigDecimal(lineNode.get("unit_price").asText()));
                }

                if (lineNode.has("total") && !lineNode.get("total").isNull()) {
                    line.setTotalPrice(new BigDecimal(lineNode.get("total").asText()));
                }

                // Add tax info if available
                if (lineNode.has("tax_rate") && !lineNode.get("tax_rate").isNull()) {
                    JsonNode taxRate = lineNode.get("tax_rate");
                    if (taxRate.has("rate") && !taxRate.get("rate").isNull()) {
                        line.setTaxRate(new BigDecimal(taxRate.get("rate").asText()));
                    }
                }

                // Add product info if available
                if (lineNode.has("product") && !lineNode.get("product").isNull()) {
                    JsonNode product = lineNode.get("product");
                    line.setProductId(getTextOrNull(product, "id"));
                }

                lines.add(line);
            }
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