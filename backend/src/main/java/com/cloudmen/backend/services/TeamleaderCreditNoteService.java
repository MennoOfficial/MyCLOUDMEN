package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteListDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for accessing TeamLeader credit notes.
 * All data is fetched directly from the TeamLeader API without local storage.
 * 
 * SECURITY NOTE: Methods that don't require a customer/company context are
 * marked as protected and should not be called directly from controllers
 * without
 * applying proper filtering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamleaderCreditNoteService {

    private final WebClient webClient;
    private final TeamleaderOAuthService oAuthService;

    // Constants for common values and messages
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final String CREDIT_NOTES_LIST_ENDPOINT = "/creditNotes.list";
    private static final String CREDIT_NOTES_DOWNLOAD_ENDPOINT = "/creditNotes.download";
    private static final String AUTH_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ERROR_NO_ACCESS_TOKEN = "No valid access token available";
    private static final String ERROR_MAPPING_CREDIT_NOTE = "Error mapping credit note: {}";
    private static final String ERROR_API_CALL = "API call to {} failed: {}";
    private static final String DEBUG_PARSE_DATE = "Failed to parse date: {}";
    private static final String DEBUG_PARSE_DECIMAL = "Failed to parse decimal: {}";

    /**
     * Find all credit notes with pagination - fetches directly from TeamLeader API
     * SECURITY NOTE: This method returns all credit notes without customer
     * filtering.
     */
    protected Page<TeamleaderCreditNoteListDTO> findAllCreditNotes(Pageable pageable) {
        String requestBody = String.format(
                "{\"page\":{\"size\":%d,\"number\":%d},\"sort\":[{\"field\":\"creditnote_date\",\"order\":\"desc\"}]}",
                pageable.getPageSize(), pageable.getPageNumber() + 1);

        return executeApiCall(
                CREDIT_NOTES_LIST_ENDPOINT,
                requestBody,
                response -> createPageFromResponse(response, pageable, this::mapToCreditNoteListDto),
                new PageImpl<>(Collections.emptyList()));
    }

    /**
     * Find credit notes by invoice ID - fetches directly from TeamLeader API
     */
    public List<TeamleaderCreditNoteListDTO> findByInvoiceId(String invoiceId) {
        if (invoiceId == null || invoiceId.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Searching for credit notes for invoice ID: {}", invoiceId);

        // Try the format from the API documentation first
        String requestBody1 = String.format(
                "{\"page\":{\"size\":%d,\"number\":1},\"filter\":{\"invoice_id\":\"%s\"}}",
                DEFAULT_PAGE_SIZE, invoiceId);

        log.debug("Credit notes API request body (format 1): {}", requestBody1);

        List<TeamleaderCreditNoteListDTO> result = executeApiCall(
                CREDIT_NOTES_LIST_ENDPOINT,
                requestBody1,
                this::parseApiResponseToList,
                Collections.emptyList());

        if (!result.isEmpty()) {
            log.info("Found {} credit notes for invoice {} using format 1", result.size(), invoiceId);
            return result;
        }

        // Try alternative format
        String requestBody2 = String.format(
                "{\"page\":{\"size\":%d,\"number\":1},\"filter\":{\"invoice\":{\"type\":\"invoice\",\"id\":\"%s\"}}}",
                DEFAULT_PAGE_SIZE, invoiceId);

        log.debug("Credit notes API request body (format 2): {}", requestBody2);

        result = executeApiCall(
                CREDIT_NOTES_LIST_ENDPOINT,
                requestBody2,
                this::parseApiResponseToList,
                Collections.emptyList());

        log.info("Found {} credit notes for invoice {} using format 2", result.size(), invoiceId);
        return result;
    }

    /**
     * Download a credit note from TeamLeader API
     * 
     * @param creditNoteId The ID of the credit note to download
     * @param format       The format for download (pdf, ubl)
     * @return Byte array of the credit note file
     */
    public byte[] downloadCreditNote(String creditNoteId, String format) {
        if (creditNoteId == null || creditNoteId.isEmpty()) {
            log.warn("Cannot download credit note: creditNoteId is null or empty");
            return new byte[0];
        }

        // Default to PDF if format not specified or invalid
        String downloadFormat = (format != null && ("pdf".equals(format) || "ubl".equals(format))) ? format : "pdf";

        String requestBody = String.format(
                "{\"id\":\"%s\",\"format\":\"%s\"}",
                creditNoteId, downloadFormat);

        log.info("Attempting to download credit note {} in {} format", creditNoteId, downloadFormat);
        log.debug("Download request body: {}", requestBody);

        String accessToken = getAccessToken();
        if (accessToken == null) {
            log.error("Cannot download credit note: {}", ERROR_NO_ACCESS_TOKEN);
            return new byte[0];
        }

        try {
            log.info("Making download request to TeamLeader API for credit note {}", creditNoteId);

            byte[] response = webClient.post()
                    .uri(CREDIT_NOTES_DOWNLOAD_ENDPOINT)
                    .header(AUTH_HEADER, BEARER_PREFIX + accessToken)
                    .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), clientResponse -> {
                        log.error("TeamLeader API returned error status: {} for credit note {}",
                                clientResponse.statusCode(), creditNoteId);
                        return clientResponse.bodyToMono(String.class)
                                .doOnNext(body -> log.error("Error response body: {}", body))
                                .then(Mono.error(new RuntimeException(
                                        "Download failed with status: " + clientResponse.statusCode())));
                    })
                    .bodyToMono(byte[].class)
                    .block();

            if (response != null && response.length > 0) {
                log.info("Successfully downloaded credit note {} ({} bytes)", creditNoteId, response.length);
                return response;
            } else {
                log.warn("Empty response received for credit note download: {}", creditNoteId);
                return new byte[0];
            }

        } catch (Exception e) {
            log.error("Error downloading credit note {}: {}", creditNoteId, e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * Find credit notes by date range - fetches directly from TeamLeader API
     * SECURITY NOTE: This method returns credit notes filtered by date but not by
     * customer.
     */
    protected List<TeamleaderCreditNoteListDTO> findByDateRange(LocalDate startDate, LocalDate endDate) {
        String requestBody = String.format(
                "{\"page\":{\"size\":%d,\"number\":1},\"filter\":{\"creditnote_date\":{\"from\":\"%s\",\"until\":\"%s\"}}}",
                DEFAULT_PAGE_SIZE, startDate.toString(), endDate.toString());

        return executeApiCall(
                CREDIT_NOTES_LIST_ENDPOINT,
                requestBody,
                this::parseApiResponseToList,
                Collections.emptyList());
    }

    /**
     * Search credit notes by term - fetches directly from TeamLeader API
     * SECURITY NOTE: This method returns all matching credit notes without customer
     * filtering.
     */
    protected List<TeamleaderCreditNoteListDTO> searchCreditNotes(String term) {
        String requestBody = String.format(
                "{\"page\":{\"size\":50,\"number\":1},\"filter\":{\"term\":\"%s\"}}",
                term);

        return executeApiCall(
                CREDIT_NOTES_LIST_ENDPOINT,
                requestBody,
                this::parseApiResponseToList,
                Collections.emptyList());
    }

    /**
     * Find all credit notes for a specific company
     * This method is safe to expose through controllers as it filters by company
     * context.
     */
    public List<TeamleaderCreditNoteListDTO> findByCustomerId(String companyId,
            TeamleaderInvoiceService invoiceService) {
        if (companyId == null || companyId.isEmpty()) {
            return Collections.emptyList();
        }

        // Using the standard TeamLeader API format for filtering by company ID
        String requestBody = String.format(
                "{\"page\":{\"size\":%d,\"number\":1},\"filter\":{\"customer\":{\"type\":\"company\",\"id\":\"%s\"}}}",
                DEFAULT_PAGE_SIZE, companyId);

        List<TeamleaderCreditNoteListDTO> directResults = executeApiCall(
                CREDIT_NOTES_LIST_ENDPOINT,
                requestBody,
                this::parseApiResponseToList,
                Collections.emptyList());

        if (!directResults.isEmpty()) {
            return directResults;
        }

        // Fall back to getting credit notes via company's invoices if API doesn't
        // support direct filtering
        return invoiceService.findByCustomerId(companyId).stream()
                .map(invoice -> findByInvoiceId(invoice.getId()))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Search credit notes for a specific company's invoices
     * This method is safe to expose through controllers as it filters by company
     * context.
     */
    public List<TeamleaderCreditNoteListDTO> searchCreditNotesByCustomer(String term, String companyId,
            TeamleaderInvoiceService invoiceService) {
        // Validate input parameters
        if (companyId == null || companyId.isEmpty() || term == null || term.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all credit notes for this company
        List<TeamleaderCreditNoteListDTO> companyCreditNotes = findByCustomerId(companyId, invoiceService);

        // Then filter by search term
        String searchTermLower = term.toLowerCase();
        return companyCreditNotes.stream()
                .filter(creditNote -> containsIgnoreCase(creditNote.getNumber(), searchTermLower) ||
                        containsIgnoreCase(creditNote.getStatus(), searchTermLower) ||
                        containsIgnoreCase(creditNote.getInvoiceNumber(), searchTermLower))
                .collect(Collectors.toList());
    }

    private boolean containsIgnoreCase(String text, String searchTerm) {
        return text != null && text.toLowerCase().contains(searchTerm);
    }

    /**
     * Find credit notes for a specific company in a date range
     * This method is safe to expose through controllers as it filters by company
     * context.
     */
    public List<TeamleaderCreditNoteListDTO> findByCustomerAndDateRange(
            String companyId, LocalDate startDate, LocalDate endDate, TeamleaderInvoiceService invoiceService) {
        // First validate company ID and date range
        if (companyId == null || companyId.isEmpty() ||
                startDate == null || endDate == null ||
                endDate.isBefore(startDate)) {
            return Collections.emptyList();
        }

        // Get all credit notes for this company
        List<TeamleaderCreditNoteListDTO> companyCreditNotes = findByCustomerId(companyId, invoiceService);

        // Then filter by date range
        return companyCreditNotes.stream()
                .filter(creditNote -> isDateInRange(creditNote.getDate(), startDate, endDate))
                .collect(Collectors.toList());
    }

    private boolean isDateInRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
        return date != null &&
                (date.isEqual(startDate) || date.isAfter(startDate)) &&
                (date.isEqual(endDate) || date.isBefore(endDate));
    }

    /**
     * Generic method to execute TeamLeader API calls with complete error handling
     * and response processing
     * 
     * @param <T>             Return type for the API call
     * @param endpoint        API endpoint to call (e.g. "/creditNotes.list")
     * @param requestBody     JSON request body as a string
     * @param responseHandler Function to process the API response
     * @param defaultResult   Default result to return in case of errors
     * @return Processed API response or default result in case of errors
     */
    private <T> T executeApiCall(String endpoint, String requestBody,
            Function<JsonNode, T> responseHandler, T defaultResult) {
        String accessToken = getAccessToken();
        if (accessToken == null) {
            return defaultResult;
        }

        try {
            JsonNode response = executeApiCallWithToken(endpoint, requestBody, accessToken);

            // Additional check for null response
            if (response == null) {
                log.warn("Null response received from API endpoint: {}", endpoint);
                return defaultResult;
            }

            return responseHandler.apply(response);
        } catch (Exception e) {
            log.error(ERROR_API_CALL, endpoint, e.getMessage());
            return defaultResult;
        }
    }

    /**
     * Execute an API call with a provided access token
     * 
     * @param endpoint    API endpoint
     * @param requestBody JSON request body as string
     * @param accessToken OAuth access token
     * @return API response as JsonNode
     */
    private JsonNode executeApiCallWithToken(String endpoint, String requestBody, String accessToken) {
        return webClient.post()
                .uri(endpoint)
                .header(AUTH_HEADER, BEARER_PREFIX + accessToken)
                .header(CONTENT_TYPE_HEADER, JSON_CONTENT_TYPE)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * Get a valid access token or return null
     * 
     * @return Valid access token or null if not available
     */
    private String getAccessToken() {
        String accessToken = oAuthService.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            log.error(ERROR_NO_ACCESS_TOKEN);
            return null;
        }
        return accessToken;
    }

    /**
     * Parse API response to a list of credit note DTOs
     * 
     * @param response API response JSON
     * @return List of credit note DTOs
     */
    private List<TeamleaderCreditNoteListDTO> parseApiResponseToList(JsonNode response) {
        if (response == null || !response.has("data")) {
            log.warn("No data field in credit notes API response");
            return Collections.emptyList();
        }

        log.info("Credit notes API response data: {}", response.get("data").toString());

        return StreamSupport.stream(response.get("data").spliterator(), false)
                .map(this::mapToCreditNoteListDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Create a Page from API response
     * 
     * @param <T>      Type of items in the page
     * @param response API response JSON
     * @param pageable Pagination information
     * @param mapper   Function to map each item
     * @return Page of items
     */
    private <T> Page<T> createPageFromResponse(JsonNode response, Pageable pageable,
            Function<JsonNode, T> mapper) {
        if (response == null || !response.has("data")) {
            return new PageImpl<>(Collections.emptyList());
        }

        List<T> items = StreamSupport.stream(response.get("data").spliterator(), false)
                .map(node -> {
                    try {
                        return mapper.apply(node);
                    } catch (Exception e) {
                        log.error("Error parsing item: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        int totalCount = response.has("meta") && response.get("meta").has("count")
                ? response.get("meta").get("count").asInt()
                : items.size();

        return new PageImpl<>(items, pageable, totalCount);
    }

    /**
     * Helper method to map credit note data to list DTO
     * 
     * @param node JSON node containing credit note data
     * @return Mapped DTO or null if mapping fails
     */
    private TeamleaderCreditNoteListDTO mapToCreditNoteListDto(JsonNode node) {
        try {
            log.debug("Mapping credit note node: {}", node.toString());

            TeamleaderCreditNoteListDTO dto = new TeamleaderCreditNoteListDTO();

            dto.setId(getTextOrNull(node, "id"));

            // Try different field names for credit note number
            dto.setNumber(getTextOrNull(node, "credit_note_number"));
            if (dto.getNumber() == null) {
                dto.setNumber(getTextOrNull(node, "number"));
            }

            dto.setStatus(getTextOrNull(node, "status"));

            // Map date - try different field names
            if (node.has("credit_note_date")) {
                dto.setDate(parseLocalDate(node.get("credit_note_date")));
            } else if (node.has("date")) {
                dto.setDate(parseLocalDate(node.get("date")));
            }

            // Initialize total to ensure it's never null
            dto.setTotal(BigDecimal.ZERO);

            // Map total amount - try multiple approaches
            if (node.has("total")) {
                JsonNode total = node.get("total");
                log.debug("Total node: {}", total.toString());

                if (total.isObject()) {
                    // Try tax_inclusive first (most common in TeamLeader)
                    if (total.has("tax_inclusive")) {
                        JsonNode taxInclusive = total.get("tax_inclusive");
                        if (taxInclusive.has("amount")) {
                            BigDecimal amount = parseBigDecimal(taxInclusive.get("amount"));
                            dto.setTotal(amount);
                            log.debug("Set total from tax_inclusive.amount: {}", dto.getTotal());
                        }
                    }
                    // Fallback to direct amount
                    else if (total.has("amount")) {
                        BigDecimal amount = parseBigDecimal(total.get("amount"));
                        dto.setTotal(amount);
                        log.debug("Set total from amount: {}", dto.getTotal());
                    }

                    // Get currency
                    if (total.has("currency")) {
                        dto.setCurrency(getTextOrNull(total, "currency"));
                    } else if (total.has("tax_inclusive") && total.get("tax_inclusive").has("currency")) {
                        dto.setCurrency(getTextOrNull(total.get("tax_inclusive"), "currency"));
                    }
                } else if (total.isNumber()) {
                    // If total is directly a number
                    BigDecimal amount = parseBigDecimal(total);
                    dto.setTotal(amount);
                    log.debug("Set total from direct number: {}", dto.getTotal());
                }
            }

            // Map customer name - try different possible fields
            if (node.has("invoicee")) {
                JsonNode invoicee = node.get("invoicee");
                dto.setCustomerName(getTextOrNull(invoicee, "name"));
            } else if (node.has("customer")) {
                JsonNode customer = node.get("customer");
                if (customer.isObject()) {
                    dto.setCustomerName(getTextOrNull(customer, "name"));
                } else {
                    dto.setCustomerName(getTextOrNull(node, "customer"));
                }
            }

            // Map invoice info
            JsonNode invoiceNode = null;
            if (node.has("invoice")) {
                invoiceNode = node.get("invoice");
                log.debug("Found invoice node: {}", invoiceNode.toString());
            } else if (node.has("for_invoice")) {
                invoiceNode = node.get("for_invoice");
                log.debug("Found for_invoice node: {}", invoiceNode.toString());
            }

            if (invoiceNode != null && invoiceNode.isObject()) {
                dto.setInvoiceId(getTextOrNull(invoiceNode, "id"));
                dto.setInvoiceNumber(getTextOrNull(invoiceNode, "number"));

                // If number not found, use ID as fallback
                if (dto.getInvoiceNumber() == null && dto.getInvoiceId() != null) {
                    dto.setInvoiceNumber(dto.getInvoiceId());
                }
            }

            log.info("Mapped credit note DTO: id={}, number={}, total={}, currency={}, invoiceId={}",
                    dto.getId(), dto.getNumber(), dto.getTotal(), dto.getCurrency(), dto.getInvoiceId());

            return dto;
        } catch (Exception e) {
            log.error(ERROR_MAPPING_CREDIT_NOTE, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Safely parse a JSON node to LocalDate
     * 
     * @param node JSON node with date string
     * @return Parsed LocalDate or null
     */
    private LocalDate parseLocalDate(JsonNode node) {
        if (node != null && !node.isNull()) {
            try {
                return LocalDate.parse(node.asText());
            } catch (Exception e) {
                log.debug(DEBUG_PARSE_DATE, node.asText());
            }
        }
        return null;
    }

    /**
     * Safely parse a JSON node to BigDecimal
     * 
     * @param node JSON node with numeric value
     * @return Parsed BigDecimal or null
     */
    private BigDecimal parseBigDecimal(JsonNode node) {
        if (node != null && !node.isNull()) {
            try {
                if (node.isNumber()) {
                    return new BigDecimal(node.asDouble());
                } else if (node.isTextual()) {
                    String text = node.asText().trim();
                    if (!text.isEmpty()) {
                        return new BigDecimal(text);
                    }
                }
            } catch (Exception e) {
                log.debug(DEBUG_PARSE_DECIMAL, node.asText());
            }
        }
        return BigDecimal.ZERO; // Return zero instead of null for amounts
    }

    /**
     * Helper method to safely get text from a JsonNode
     * 
     * @param node      JsonNode to extract text from
     * @param fieldName Field name to extract
     * @return String value or null if not available
     */
    private String getTextOrNull(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }
}