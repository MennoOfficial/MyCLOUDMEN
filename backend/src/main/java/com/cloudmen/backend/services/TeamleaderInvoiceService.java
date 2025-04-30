package com.cloudmen.backend.services;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceDownloadDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceListDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for interacting with TeamLeader API for invoice operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamleaderInvoiceService {

    private final WebClient webClient;
    private final TeamleaderOAuthService oAuthService;

    /**
     * Find invoices for a specific company with optional filters
     * 
     * @param companyId  Company ID in TeamLeader format
     * @param isPaid     Optional filter for paid status
     * @param isOverdue  Optional filter for overdue status
     * @param searchTerm Optional search term
     * @return List of invoice list DTOs
     */
    public List<TeamleaderInvoiceListDTO> findInvoicesByCompany(
            String companyId,
            Boolean isPaid,
            Boolean isOverdue,
            String searchTerm) {

        log.info("Fetching invoices for company ID: {} with filters", companyId);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Collections.emptyList();
            }

            // Create request as a proper JSON structure using ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();

            // Create the main request object
            Map<String, Object> requestBody = new HashMap<>();

            // Add pagination
            Map<String, Object> page = new HashMap<>();
            page.put("size", 100);
            page.put("number", 1);
            requestBody.put("page", page);

            // Create filter object
            Map<String, Object> filter = new HashMap<>();

            // Add company filter
            Map<String, Object> customer = new HashMap<>();
            customer.put("type", "company");
            customer.put("id", companyId.trim()); // Ensure ID is trimmed
            filter.put("customer", customer);

            // Add status filter if isPaid is specified
            if (isPaid != null) {
                // Always use an array for status, even for a single value
                List<String> statuses = new ArrayList<>();
                if (isPaid) {
                    statuses.add("matched"); // "matched" is used for paid invoices in Teamleader API
                } else {
                    statuses.add("outstanding");
                }
                filter.put("status", statuses);
            }

            // Add search term if provided
            if (searchTerm != null && !searchTerm.isEmpty()) {
                filter.put("term", searchTerm);
            }

            // Add filter to request
            requestBody.put("filter", filter);

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // Call the API
            JsonNode response = webClient.post()
                    .uri("/invoices.list")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data")) {
                return Collections.emptyList();
            }

            // Process the results
            List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (JsonNode invoiceNode : response.get("data")) {
                try {
                    TeamleaderInvoiceListDTO invoice = mapToInvoiceListDto(invoiceNode);

                    // Mark overdue if not paid and past due date
                    if (!Boolean.TRUE.equals(invoice.getIsPaid()) &&
                            invoice.getDueOn() != null &&
                            invoice.getDueOn().isBefore(today)) {
                        invoice.setIsOverdue(true);
                    }

                    // Add to results if matches overdue filter or if no filter
                    if (isOverdue == null || isOverdue.equals(invoice.getIsOverdue())) {
                        invoices.add(invoice);
                    }
                } catch (Exception e) {
                    log.error("Error parsing invoice data", e);
                }
            }

            return invoices;
        } catch (Exception e) {
            log.error("Error fetching invoices for company ID: {}", companyId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Convenience method to find all invoices for a company
     * 
     * @param companyId Company ID in TeamLeader format
     * @return List of all invoice list DTOs for the company
     */
    public List<TeamleaderInvoiceListDTO> findByCustomerId(String companyId) {
        return findInvoicesByCompany(companyId, null, null, null);
    }

    /**
     * Find invoice by ID
     * 
     * @param id Invoice ID
     * @return Optional containing the detailed invoice if found
     */
    public Optional<TeamleaderInvoiceDetailDTO> findById(String id) {
        log.info("Fetching invoice by ID: {}", id);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                return Optional.empty();
            }

            // Create request as proper JSON using ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("id", id.trim()); // Ensure ID is trimmed

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // Call the API directly
            JsonNode response = webClient.post()
                    .uri("/invoices.info")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // Return empty if no valid response
            if (response == null || !response.has("data")) {
                return Optional.empty();
            }

            // Map and return the details
            TeamleaderInvoiceDetailDTO detailDTO = mapToInvoiceDetailDto(response);
            return Optional.of(detailDTO);
        } catch (Exception e) {
            log.error("Error fetching invoice by ID from TeamLeader API", e);
            return Optional.empty();
        }
    }

    /**
     * Maps a JSON node from TeamLeader API to an invoice list DTO
     * 
     * @param invoice JSON node containing invoice data
     * @return Mapped TeamleaderInvoiceListDTO object
     */
    private TeamleaderInvoiceListDTO mapToInvoiceListDto(JsonNode invoice) {
        // Extract the data node if present
        if (invoice.has("data")) {
            invoice = invoice.get("data");
        }

        TeamleaderInvoiceListDTO dto = new TeamleaderInvoiceListDTO();

        // Map basic properties
        dto.setId(getTextOrNull(invoice, "id"));
        dto.setInvoiceNumber(getTextOrNull(invoice, "number"));
        dto.setPaymentReference(getTextOrNull(invoice, "payment_reference"));

        // Parse dates
        if (invoice.has("due_on") && !invoice.get("due_on").isNull()) {
            dto.setDueOn(LocalDate.parse(invoice.get("due_on").asText()));
        }

        // Set default values
        dto.setTotal(BigDecimal.ZERO);
        dto.setIsPaid(false);
        dto.setIsOverdue(false);

        // Parse total and currency
        if (invoice.has("total")) {
            JsonNode total = invoice.get("total");
            if (total.has("tax_inclusive") && total.get("tax_inclusive").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("tax_inclusive").get("amount").asText()));
                if (total.get("tax_inclusive").has("currency")) {
                    dto.setCurrency(total.get("tax_inclusive").get("currency").asText());
                }
            }
        }

        // Check paid status
        if (invoice.has("paid") && !invoice.get("paid").isNull()) {
            dto.setIsPaid(invoice.get("paid").asBoolean());
        } else if (invoice.has("status") && !invoice.get("status").isNull()) {
            String status = invoice.get("status").asText().toLowerCase();
            dto.setIsPaid(status.equals("paid") || status.equals("matched"));
        }

        return dto;
    }

    /**
     * Maps a JSON node from TeamLeader API to an invoice detail DTO
     * 
     * @param node JSON node containing detailed invoice data
     * @return Mapped TeamleaderInvoiceDetailDTO object
     */
    private TeamleaderInvoiceDetailDTO mapToInvoiceDetailDto(JsonNode node) {
        // Extract the data node if present
        if (node.has("data")) {
            node = node.get("data");
        }

        TeamleaderInvoiceDetailDTO dto = new TeamleaderInvoiceDetailDTO();

        // Map basic properties
        dto.setId(getTextOrNull(node, "id"));
        dto.setNumber(getTextOrNull(node, "number"));
        dto.setStatus(getTextOrNull(node, "status"));
        dto.setPaymentReference(getTextOrNull(node, "payment_reference"));
        dto.setPurchaseOrderNumber(getTextOrNull(node, "purchase_order_number"));

        // Parse dates
        if (node.has("date") && !node.get("date").isNull()) {
            dto.setDate(LocalDate.parse(node.get("date").asText()));
        }
        if (node.has("due_on") && !node.get("due_on").isNull()) {
            dto.setDueOn(LocalDate.parse(node.get("due_on").asText()));
        }
        if (node.has("paid_at") && !node.get("paid_at").isNull()) {
            dto.setPaidAt(LocalDate.parse(node.get("paid_at").asText()));
        }

        // Parse boolean values
        if (node.has("sent") && !node.get("sent").isNull()) {
            dto.setSent(node.get("sent").asBoolean());
        }
        if (node.has("paid") && !node.get("paid").isNull()) {
            dto.setIsPaid(node.get("paid").asBoolean());
        }

        // Parse numeric values with defaults
        dto.setTotal(BigDecimal.ZERO);
        dto.setSubtotal(BigDecimal.ZERO);
        dto.setTaxAmount(BigDecimal.ZERO);

        // Parse total amounts
        if (node.has("total")) {
            JsonNode total = node.get("total");
            if (total.has("tax_exclusive") && total.get("tax_exclusive").has("amount")) {
                dto.setSubtotal(new BigDecimal(total.get("tax_exclusive").get("amount").asText()));
                if (total.get("tax_exclusive").has("currency")) {
                    dto.setCurrency(total.get("tax_exclusive").get("currency").asText());
                }
            }

            if (total.has("tax_inclusive") && total.get("tax_inclusive").has("amount")) {
                dto.setTotal(new BigDecimal(total.get("tax_inclusive").get("amount").asText()));
                if (total.get("tax_inclusive").has("currency")) {
                    dto.setCurrency(total.get("tax_inclusive").get("currency").asText());
                }
            }

            // Calculate tax amount
            if (dto.getTotal().compareTo(BigDecimal.ZERO) > 0 &&
                    dto.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
                dto.setTaxAmount(dto.getTotal().subtract(dto.getSubtotal()));
            }
        }

        // Parse customer info
        if (node.has("customer")) {
            JsonNode customer = node.get("customer");
            dto.setCustomerId(getTextOrNull(customer, "id"));
            dto.setCustomerType(getTextOrNull(customer, "type"));
            dto.setCustomerName(getTextOrNull(customer, "name"));
        }

        // Parse invoice lines
        if (node.has("items") && node.get("items").isArray()) {
            List<TeamleaderInvoiceDetailDTO.InvoiceLineDTO> lines = new ArrayList<>();

            for (JsonNode lineNode : node.get("items")) {
                TeamleaderInvoiceDetailDTO.InvoiceLineDTO line = new TeamleaderInvoiceDetailDTO.InvoiceLineDTO();

                line.setDescription(getTextOrNull(lineNode, "description"));
                line.setUnit(getTextOrNull(lineNode, "unit"));

                if (lineNode.has("quantity") && !lineNode.get("quantity").isNull()) {
                    line.setQuantity(new BigDecimal(lineNode.get("quantity").asText()));
                }
                if (lineNode.has("unit_price") && !lineNode.get("unit_price").isNull()) {
                    line.setUnitPrice(new BigDecimal(lineNode.get("unit_price").asText()));
                }
                if (lineNode.has("total_price") && !lineNode.get("total_price").isNull()) {
                    line.setTotalPrice(new BigDecimal(lineNode.get("total_price").asText()));
                }
                if (lineNode.has("tax_rate") && !lineNode.get("tax_rate").isNull()) {
                    line.setTaxRate(new BigDecimal(lineNode.get("tax_rate").asText()));
                }

                lines.add(line);
            }

            dto.setLines(lines);
        }

        return dto;
    }

    /**
     * Helper method to safely extract text from a JsonNode
     * 
     * @param node      JsonNode to extract text from
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
     * Download an invoice in the specified format
     * 
     * @param invoiceId Invoice ID in Teamleader format
     * @param format    The format to download (pdf, ubl/e-fff, ubl/peppol_bis_3)
     * @return Optional containing the download information if successful
     */
    public Optional<TeamleaderInvoiceDownloadDTO> downloadInvoice(String invoiceId, String format) {
        log.info("Downloading invoice ID: {} in format: {}", invoiceId, format);

        try {
            String accessToken = oAuthService.getAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("No valid access token available for TeamLeader API");
                return Optional.empty();
            }

            // Create request as proper JSON using ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("id", invoiceId.trim()); // Ensure ID is trimmed
            requestBody.put("format", format);

            // Convert to JSON string
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // Call the API directly
            JsonNode response = webClient.post()
                    .uri("/invoices.download")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(jsonBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // Return empty if no valid response
            if (response == null || !response.has("data")) {
                log.error("Invalid or empty response from Teamleader API for invoice download");
                return Optional.empty();
            }

            // Map and return the download information
            JsonNode data = response.get("data");
            TeamleaderInvoiceDownloadDTO downloadDTO = new TeamleaderInvoiceDownloadDTO();

            if (data.has("location")) {
                downloadDTO.setLocation(data.get("location").asText());
            }

            if (data.has("expires")) {
                downloadDTO.setExpires(ZonedDateTime.parse(data.get("expires").asText()));
            }

            return Optional.of(downloadDTO);
        } catch (Exception e) {
            log.error("Error downloading invoice with ID: {}", invoiceId, e);
            return Optional.empty();
        }
    }
}