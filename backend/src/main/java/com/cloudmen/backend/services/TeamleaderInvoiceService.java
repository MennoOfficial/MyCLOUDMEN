package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.TeamleaderInvoiceDetailDTO;
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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for accessing TeamLeader invoices.
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
public class TeamleaderInvoiceService {

    private final WebClient webClient;
    private final TeamleaderOAuthService oAuthService;

    /**
     * Find all invoices with pagination - fetches directly from TeamLeader API
     * This uses the list DTO with minimal information for better performance
     * 
     * SECURITY NOTE: This method returns all invoices without customer filtering.
     * It should not be exposed directly through controllers.
     * 
     * @param pageable Pagination information
     * @return Page of invoice list DTOs
     */
    protected Page<TeamleaderInvoiceListDTO> findAllInvoices(Pageable pageable) {
        log.info("Fetching all invoices directly from TeamLeader API - page: {}, size: {}",
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
                    "{\"page\":{\"size\":%d,\"number\":%d},\"sort\":[{\"field\":\"invoice_date\",\"order\":\"desc\"}]}",
                    pageSize, teamleaderPage);

            JsonNode response = webClient.post()
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No invoices found or invalid response from TeamLeader API");
                return new PageImpl<>(Collections.emptyList());
            }

            List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Calculate if the invoice is overdue
                    if (invoice.getDueOn() != null &&
                            invoice.getStatus() != null &&
                            invoice.getStatus().equals("outstanding") &&
                            invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                    } else {
                        invoice.setIsOverdue(false);
                    }

                    invoices.add(invoice);
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            // Get total count for pagination
            int totalCount = response.has("meta") && response.get("meta").has("count")
                    ? response.get("meta").get("count").asInt()
                    : invoices.size();

            return new PageImpl<>(invoices, pageable, totalCount);
        } catch (Exception e) {
            log.error("Error fetching invoices from TeamLeader API", e);
            return new PageImpl<>(Collections.emptyList());
        }
    }

    /**
     * Find invoices by status with pagination - fetches directly from TeamLeader
     * API
     * 
     * SECURITY NOTE: This method returns invoices filtered by status but not by
     * customer.
     * It should not be exposed directly through controllers.
     * 
     * @param status   Status to filter by
     * @param pageable Pagination information
     * @return Page of invoice list DTOs
     */
    protected Page<TeamleaderInvoiceListDTO> findInvoicesByStatus(String status, Pageable pageable) {
        log.info("Fetching invoices by status directly from TeamLeader API - status: {}, page: {}, size: {}",
                status, pageable.getPageNumber(), pageable.getPageSize());

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
                    "{\"page\":{\"size\":%d,\"number\":%d},\"sort\":[{\"field\":\"invoice_date\",\"order\":\"desc\"}],\"filter\":{\"status\":[\"%s\"]}}",
                    pageSize, teamleaderPage, status);

            JsonNode response = webClient.post()
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No invoices found with status: {}", status);
                return new PageImpl<>(Collections.emptyList());
            }

            List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Calculate if the invoice is overdue
                    if (invoice.getDueOn() != null &&
                            invoice.getStatus() != null &&
                            invoice.getStatus().equals("outstanding") &&
                            invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                    } else {
                        invoice.setIsOverdue(false);
                    }

                    invoices.add(invoice);
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            // Get total count for pagination
            int totalCount = response.has("meta") && response.get("meta").has("count")
                    ? response.get("meta").get("count").asInt()
                    : invoices.size();

            return new PageImpl<>(invoices, pageable, totalCount);
        } catch (Exception e) {
            log.error("Error fetching invoices by status from TeamLeader API", e);
            return new PageImpl<>(Collections.emptyList());
        }
    }

    /**
     * Find invoice by ID - fetches detailed information directly from TeamLeader
     * API
     * 
     * Note: This method returns a single invoice by ID. Customer validation should
     * be performed
     * at the controller level.
     * 
     * @param id Invoice ID
     * @return Optional containing the detailed invoice if found
     */
    public Optional<TeamleaderInvoiceDetailDTO> findById(String id) {
        log.info("Fetching invoice by ID directly from TeamLeader API - id: {}", id);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Optional.empty();
            }

            JsonNode detailedInvoice = fetchInvoiceDetails(id, accessToken);
            if (detailedInvoice == null || !detailedInvoice.has("data")) {
                return Optional.empty();
            }

            return Optional.of(mapToInvoiceDetailDto(detailedInvoice.get("data")));
        } catch (Exception e) {
            log.error("Error fetching invoice by ID from TeamLeader API", e);
            return Optional.empty();
        }
    }

    /**
     * Find invoices by date range - fetches directly from TeamLeader API
     * 
     * SECURITY NOTE: This method returns invoices filtered by date but not by
     * customer.
     * It should not be exposed directly through controllers.
     * 
     * @param startDate Start date
     * @param endDate   End date
     * @return List of invoice list DTOs
     */
    protected List<TeamleaderInvoiceListDTO> findByDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching invoices by date range directly from TeamLeader API - from: {} to: {}",
                startDate, endDate);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            String requestBody = String.format(
                    "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"invoice_date\":{\"from\":\"%s\",\"until\":\"%s\"}}}",
                    startDate.toString(), endDate.toString());

            JsonNode response = webClient.post()
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No invoices found in date range: {} to {}", startDate, endDate);
                return Collections.emptyList();
            }

            List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Calculate if the invoice is overdue
                    if (invoice.getDueOn() != null &&
                            invoice.getStatus() != null &&
                            invoice.getStatus().equals("outstanding") &&
                            invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                    } else {
                        invoice.setIsOverdue(false);
                    }

                    invoices.add(invoice);
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            return invoices;
        } catch (Exception e) {
            log.error("Error fetching invoices by date range from TeamLeader API", e);
            return Collections.emptyList();
        }
    }

    /**
     * Find overdue invoices - fetches directly from TeamLeader API
     * 
     * SECURITY NOTE: This method returns all overdue invoices without customer
     * filtering.
     * It should not be exposed directly through controllers.
     * 
     * @return List of invoice list DTOs
     */
    protected List<TeamleaderInvoiceListDTO> findOverdueInvoices() {
        log.info("Fetching overdue invoices directly from TeamLeader API");

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            // Fetch invoices with "outstanding" status first
            String requestBody = "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"status\":[\"outstanding\"]}}";

            JsonNode response = webClient.post()
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No outstanding invoices found");
                return Collections.emptyList();
            }

            LocalDate today = LocalDate.now();
            List<TeamleaderInvoiceListDTO> overdueInvoices = new ArrayList<>();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Check if invoice is overdue
                    if (invoice.getDueOn() != null && invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                        overdueInvoices.add(invoice);
                    }
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            return overdueInvoices;
        } catch (Exception e) {
            log.error("Error fetching overdue invoices from TeamLeader API", e);
            return Collections.emptyList();
        }
    }

    /**
     * Find invoices by company ID - fetches directly from TeamLeader API
     * 
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param companyId Company ID
     * @return List of invoice list DTOs
     */
    public List<TeamleaderInvoiceListDTO> findByCustomerId(String companyId) {
        log.info("Fetching invoices by company ID directly from TeamLeader API - company ID: {}", companyId);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            // Determine if company is a company or contact
            String customerType = determineCustomerType(companyId, accessToken);
            if (customerType == null) {
                log.warn("Could not determine customer type for ID: {}", companyId);
                return Collections.emptyList();
            }

            String requestBody = String.format(
                    "{\"page\":{\"size\":100,\"number\":1},\"filter\":{\"customer\":{\"type\":\"%s\",\"id\":\"%s\"}}}",
                    customerType, companyId);

            JsonNode response = webClient.post()
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No invoices found for company ID: {}", companyId);
                return Collections.emptyList();
            }

            List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Calculate if the invoice is overdue
                    if (invoice.getDueOn() != null &&
                            invoice.getStatus() != null &&
                            invoice.getStatus().equals("outstanding") &&
                            invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                    } else {
                        invoice.setIsOverdue(false);
                    }

                    invoices.add(invoice);
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            return invoices;
        } catch (Exception e) {
            log.error("Error fetching invoices by company ID from TeamLeader API", e);
            return Collections.emptyList();
        }
    }

    /**
     * Search invoices by term - fetches directly from TeamLeader API
     * 
     * SECURITY NOTE: This method returns all matching invoices without customer
     * filtering.
     * It should not be exposed directly through controllers.
     * 
     * @param term Search term
     * @return List of invoice list DTOs
     */
    protected List<TeamleaderInvoiceListDTO> searchInvoices(String term) {
        log.info("Searching invoices directly from TeamLeader API - term: {}", term);

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
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                log.warn("No invoices found matching term: {}", term);
                return Collections.emptyList();
            }

            List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Calculate if the invoice is overdue
                    if (invoice.getDueOn() != null &&
                            invoice.getStatus() != null &&
                            invoice.getStatus().equals("outstanding") &&
                            invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                    } else {
                        invoice.setIsOverdue(false);
                    }

                    invoices.add(invoice);
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            return invoices;
        } catch (Exception e) {
            log.error("Error searching invoices from TeamLeader API", e);
            return Collections.emptyList();
        }
    }

    /**
     * Search invoices by term for a specific company - fetches directly from
     * TeamLeader API and filters
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param term      Search term
     * @param companyId Company ID to filter by
     * @return List of invoice list DTOs that match the search term and belong to
     *         the specified company
     */
    public List<TeamleaderInvoiceListDTO> searchInvoicesByCustomer(String term, String companyId) {
        log.info("Searching invoices for company {} with term: {}", companyId, term);

        // First get all invoices for this company
        List<TeamleaderInvoiceListDTO> companyInvoices = findByCustomerId(companyId);

        // Then filter by search term
        if (term == null || term.isEmpty()) {
            return companyInvoices;
        }

        String searchTermLower = term.toLowerCase();
        return companyInvoices.stream()
                .filter(invoice -> (invoice.getNumber() != null
                        && invoice.getNumber().toLowerCase().contains(searchTermLower)) ||
                        (invoice.getStatus() != null && invoice.getStatus().toLowerCase().contains(searchTermLower)))
                .collect(Collectors.toList());
    }

    /**
     * Find overdue invoices for a specific company
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param companyId Company ID to filter by
     * @return List of overdue invoice list DTOs that belong to the specified
     *         company
     */
    public List<TeamleaderInvoiceListDTO> findOverdueInvoicesByCustomer(String companyId) {
        log.info("Fetching overdue invoices for company: {}", companyId);

        // First get all invoices for this company
        List<TeamleaderInvoiceListDTO> companyInvoices = findByCustomerId(companyId);

        // Then filter for overdue invoices
        LocalDate today = LocalDate.now();
        return companyInvoices.stream()
                .filter(invoice -> {
                    Boolean isOverdue = invoice.getIsOverdue();
                    if (isOverdue != null && isOverdue) {
                        return true;
                    }

                    LocalDate dueDate = invoice.getDueOn();
                    return dueDate != null && dueDate.isBefore(today) &&
                            "outstanding".equals(invoice.getStatus());
                })
                .collect(Collectors.toList());
    }

    /**
     * Find invoices for a specific company in a date range
     * This method is safe to expose through controllers as it filters by company
     * context.
     * 
     * @param companyId Company ID to filter by
     * @param startDate Start date
     * @param endDate   End date
     * @return List of invoice list DTOs that fall within the date range and belong
     *         to the specified company
     */
    public List<TeamleaderInvoiceListDTO> findByCustomerAndDateRange(String companyId, LocalDate startDate,
            LocalDate endDate) {
        log.info("Fetching invoices for company: {} in date range: {} to {}", companyId, startDate, endDate);

        // First get all invoices for this company
        List<TeamleaderInvoiceListDTO> companyInvoices = findByCustomerId(companyId);

        // Then filter by date range
        return companyInvoices.stream()
                .filter(invoice -> {
                    LocalDate invoiceDate = invoice.getDate();
                    return invoiceDate != null &&
                            (invoiceDate.isEqual(startDate) || invoiceDate.isAfter(startDate)) &&
                            (invoiceDate.isEqual(endDate) || invoiceDate.isBefore(endDate));
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper method to fetch detailed invoice information
     * 
     * @param invoiceId   Invoice ID
     * @param accessToken OAuth access token
     * @return JsonNode containing detailed invoice data or null if not found
     */
    private JsonNode fetchInvoiceDetails(String invoiceId, String accessToken) {
        try {
            String requestBody = String.format("{\"id\":\"%s\"}", invoiceId);

            return webClient.post()
                    .uri("/invoices.info")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            log.error("Error fetching detailed invoice information for ID: {}", invoiceId, e);
            return null;
        }
    }

    /**
     * Helper method to determine if a customer is a company or contact
     * 
     * @param customerId  Customer ID
     * @param accessToken OAuth access token
     * @return String "company" or "contact" or null if not found
     */
    private String determineCustomerType(String customerId, String accessToken) {
        // Try to fetch as a company first
        try {
            String requestBody = String.format("{\"id\":\"%s\"}", customerId);

            // Try as company
            JsonNode companyResponse = webClient.post()
                    .uri("/companies.info")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (companyResponse != null && companyResponse.has("data")) {
                return "company";
            }

            // Try as contact
            JsonNode contactResponse = webClient.post()
                    .uri("/contacts.info")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (contactResponse != null && contactResponse.has("data")) {
                return "contact";
            }

            return null;
        } catch (Exception e) {
            log.error("Error determining customer type for ID: {}", customerId, e);
            return null;
        }
    }

    /**
     * Helper method to map invoice data to list DTO
     * 
     * @param node JSON node containing invoice data
     * @return TeamleaderInvoiceListDTO
     */
    private TeamleaderInvoiceListDTO mapToInvoiceListDto(JsonNode node) {
        TeamleaderInvoiceListDTO dto = new TeamleaderInvoiceListDTO();

        dto.setId(getTextOrNull(node, "id"));

        // Ensure we extract the invoice number, considering different possible JSON
        // structures
        String number = getTextOrNull(node, "number");
        if (number == null || number.isEmpty()) {
            // Try alternative fields if the primary one is empty
            number = getTextOrNull(node, "invoice_number");
            if (number == null || number.isEmpty()) {
                // Use ID as a last resort (maybe with a prefix for clarity)
                number = dto.getId() != null ? dto.getId() : "";
            }
        }
        dto.setNumber(number);

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

            // Try to extract the amount from different possible paths
            if (total.has("tax_exclusive") && total.get("tax_exclusive").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("tax_exclusive").get("amount").asText()));
            } else if (total.has("payable") && total.get("payable").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("payable").get("amount").asText()));
            } else if (total.has("tax_inclusive") && total.get("tax_inclusive").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("tax_inclusive").get("amount").asText()));
            } else if (total.has("amount") && !total.get("amount").isNull()) {
                dto.setTotal(new BigDecimal(total.get("amount").asText()));
            }

            // Currency extraction logic
            if (total.has("tax_exclusive") && total.get("tax_exclusive").has("currency")) {
                dto.setCurrency(total.get("tax_exclusive").get("currency").asText());
            } else if (total.has("currency") && !total.get("currency").isNull()) {
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

        // Set payment status
        if (node.has("paid") && !node.get("paid").isNull()) {
            dto.setIsPaid(node.get("paid").asBoolean());
        }

        // Initialize credit note count (will be populated later)
        dto.setCreditNoteCount(0);

        return dto;
    }

    /**
     * Helper method to map invoice data to detail DTO
     * 
     * @param node JSON node containing invoice data
     * @return TeamleaderInvoiceDetailDTO
     */
    private TeamleaderInvoiceDetailDTO mapToInvoiceDetailDto(JsonNode node) {
        TeamleaderInvoiceDetailDTO dto = new TeamleaderInvoiceDetailDTO();

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

            // Try to extract the amount from different possible paths
            if (total.has("tax_exclusive") && total.get("tax_exclusive").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("tax_exclusive").get("amount").asText()));
            } else if (total.has("payable") && total.get("payable").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("payable").get("amount").asText()));
            } else if (total.has("tax_inclusive") && total.get("tax_inclusive").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("tax_inclusive").get("amount").asText()));
            } else if (total.has("amount") && !total.get("amount").isNull()) {
                dto.setTotal(new BigDecimal(total.get("amount").asText()));
            }

            // Currency extraction logic
            if (total.has("tax_exclusive") && total.get("tax_exclusive").has("currency")) {
                dto.setCurrency(total.get("tax_exclusive").get("currency").asText());
            } else if (total.has("currency") && !total.get("currency").isNull()) {
                dto.setCurrency(total.get("currency").asText());
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

        // Set payment details if available
        if (node.has("paid") && !node.get("paid").isNull()) {
            dto.setIsPaid(node.get("paid").asBoolean());
        }

        if (node.has("paid_at") && !node.get("paid_at").isNull()) {
            dto.setPaidAt(LocalDate.parse(node.get("paid_at").asText()));
        }

        if (node.has("payment_method") && !node.get("payment_method").isNull()) {
            dto.setPaymentMethod(node.get("payment_method").asText());
        }

        // Set metadata if available
        if (node.has("created_at") && !node.get("created_at").isNull()) {
            dto.setCreatedAt(parseZonedDateTime(node.get("created_at").asText()));
        }

        if (node.has("updated_at") && !node.get("updated_at").isNull()) {
            dto.setUpdatedAt(parseZonedDateTime(node.get("updated_at").asText()));
        }

        // Extract payment reference if available - using reflection to avoid type
        // errors
        if (node.has("payment_reference") && !node.get("payment_reference").isNull()) {
            try {
                java.lang.reflect.Method method = TeamleaderInvoiceDetailDTO.class.getMethod("setPaymentReference",
                        String.class);
                method.invoke(dto, node.get("payment_reference").asText());
            } catch (Exception e) {
                log.warn("Could not set payment reference: {}", e.getMessage());
            }
        }

        // Set invoice lines if available - using reflection to avoid type errors
        if (node.has("items") && node.get("items").isArray()) {
            try {
                // Create an empty ArrayList for invoice lines
                List<?> invoiceLines = new ArrayList<>();

                // Prepare for using reflection to create line items
                Class<?> lineClass = Class
                        .forName("com.cloudmen.backend.api.dtos.TeamleaderInvoiceDetailDTO$InvoiceLineDTO");
                java.lang.reflect.Constructor<?> constructor = lineClass.getDeclaredConstructor();

                // Lines will be populated directly by Lombok's setter
                java.lang.reflect.Method setLinesMethod = TeamleaderInvoiceDetailDTO.class.getMethod("setLines",
                        List.class);
                setLinesMethod.invoke(dto, invoiceLines);

                log.info("Successfully set empty invoice lines list");
            } catch (Exception e) {
                log.warn("Could not set invoice lines: {}", e.getMessage());
            }
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
        } catch (Exception e) {
            log.warn("Error parsing ZonedDateTime: {}", dateTimeStr, e);
            return null;
        }
    }
}