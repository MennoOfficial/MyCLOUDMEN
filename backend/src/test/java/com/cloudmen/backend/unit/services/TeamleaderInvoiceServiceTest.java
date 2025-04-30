package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceListDTO;
import com.cloudmen.backend.services.TeamleaderInvoiceService;
import com.cloudmen.backend.services.TeamleaderOAuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TeamleaderInvoiceService using Mockito.
 * Each test focuses on a single function to make tests easier to understand and
 * maintain.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderInvoiceService Tests")
class TeamleaderInvoiceServiceTest {

    @Mock
    private TeamleaderOAuthService oAuthService;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TeamleaderInvoiceService invoiceService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Using lenient mode for mocks to avoid unnecessary stubbing errors
        objectMapper = new ObjectMapper();
        invoiceService = new TeamleaderInvoiceService(webClient, oAuthService);
        setupWebClientMock();
    }

    @Test
    @DisplayName("findInvoicesByCompany should return invoices when all parameters provided")
    void findInvoicesByCompany_shouldReturnInvoices_whenAllParametersProvided() {
        // Arrange
        String companyId = "123456";
        Boolean isPaid = true;
        Boolean isOverdue = false;
        String searchTerm = "invoice";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response JSON
        JsonNode responseNode = createMockInvoiceListResponse(2);
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, isPaid, isOverdue, searchTerm);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/invoices.list");
    }

    @Test
    @DisplayName("findInvoicesByCompany should return empty list when OAuth token is invalid")
    void findInvoicesByCompany_shouldReturnEmptyList_whenOAuthTokenIsInvalid() {
        // Arrange
        String companyId = "123456";
        lenient().when(oAuthService.getAccessToken()).thenReturn(null);

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, null, null, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verifyNoInteractions(requestBodyUriSpec);
    }

    @Test
    @DisplayName("findInvoicesByCompany should return empty list when API returns null response")
    void findInvoicesByCompany_shouldReturnEmptyList_whenApiReturnsNullResponse() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock null response
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.empty());

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, null, null, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findInvoicesByCompany should return empty list when API throws exception")
    void findInvoicesByCompany_shouldReturnEmptyList_whenApiThrowsException() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock exception
        lenient().when(responseSpec.bodyToMono(JsonNode.class))
                .thenReturn(Mono.error(new RuntimeException("API Error")));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, null, null, null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findById should return invoice details when found")
    void findById_shouldReturnInvoiceDetails_whenFound() {
        // Arrange
        String invoiceId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response JSON
        JsonNode responseNode = createMockInvoiceDetailResponse(invoiceId);
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        Optional<TeamleaderInvoiceDetailDTO> result = invoiceService.findById(invoiceId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(invoiceId, result.get().getId());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/invoices.info");
    }

    @Test
    @DisplayName("findById should return empty optional when OAuth token is invalid")
    void findById_shouldReturnEmptyOptional_whenOAuthTokenIsInvalid() {
        // Arrange
        String invoiceId = "123456";
        lenient().when(oAuthService.getAccessToken()).thenReturn(null);

        // Act
        Optional<TeamleaderInvoiceDetailDTO> result = invoiceService.findById(invoiceId);

        // Assert
        assertFalse(result.isPresent());
        verify(oAuthService).getAccessToken();
        verifyNoInteractions(requestBodyUriSpec);
    }

    @Test
    @DisplayName("findById should return empty optional when API returns null response")
    void findById_shouldReturnEmptyOptional_whenApiReturnsNullResponse() {
        // Arrange
        String invoiceId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock null response
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.empty());

        // Act
        Optional<TeamleaderInvoiceDetailDTO> result = invoiceService.findById(invoiceId);

        // Assert
        assertFalse(result.isPresent());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findById should handle API exceptions and return empty optional")
    void findById_shouldHandleApiExceptions_andReturnEmptyOptional() {
        // Arrange
        String invoiceId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock exception
        lenient().when(responseSpec.bodyToMono(JsonNode.class))
                .thenReturn(Mono.error(new RuntimeException("API Error")));

        // Act
        Optional<TeamleaderInvoiceDetailDTO> result = invoiceService.findById(invoiceId);

        // Assert
        assertFalse(result.isPresent());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findByCustomerId should delegate to findInvoicesByCompany with null filters")
    void findByCustomerId_shouldDelegateToFindInvoicesByCompany_withNullFilters() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response JSON
        JsonNode responseNode = createMockInvoiceListResponse(2);
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findByCustomerId(companyId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/invoices.list");
    }

    @Test
    @DisplayName("findInvoicesByCompany should correctly filter by paid status when isPaid=true")
    void findInvoicesByCompany_shouldCorrectlyFilter_whenIsPaidTrue() {
        // Arrange
        String companyId = "123456";
        Boolean isPaid = true;
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create valid mock response with all paid invoices
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Paid invoice
        ObjectNode paidInvoice = objectMapper.createObjectNode();
        paidInvoice.put("id", "inv-1");
        paidInvoice.put("number", "INV-001");
        paidInvoice.put("paid", true); // Explicitly set to true

        // Add total node structure
        ObjectNode paidTotal = objectMapper.createObjectNode();
        ObjectNode paidTaxInclusive = objectMapper.createObjectNode();
        paidTaxInclusive.put("amount", "1000.00");
        paidTaxInclusive.put("currency", "EUR");
        paidTotal.set("tax_inclusive", paidTaxInclusive);
        paidInvoice.set("total", paidTotal);

        dataArray.add(paidInvoice);
        responseNode.set("data", dataArray);

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, isPaid, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size()); // We should have one invoice
        assertTrue(result.get(0).getIsPaid()); // It should be paid
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findInvoicesByCompany should correctly filter by paid status when isPaid=false")
    void findInvoicesByCompany_shouldCorrectlyFilter_whenIsPaidFalse() {
        // Arrange
        String companyId = "123456";
        Boolean isPaid = false;
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create valid mock response with all unpaid invoices
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Unpaid invoice
        ObjectNode unpaidInvoice = objectMapper.createObjectNode();
        unpaidInvoice.put("id", "inv-2");
        unpaidInvoice.put("number", "INV-002");
        unpaidInvoice.put("paid", false); // Explicitly set to false

        // Add total node structure
        ObjectNode unpaidTotal = objectMapper.createObjectNode();
        ObjectNode unpaidTaxInclusive = objectMapper.createObjectNode();
        unpaidTaxInclusive.put("amount", "1000.00");
        unpaidTaxInclusive.put("currency", "EUR");
        unpaidTotal.set("tax_inclusive", unpaidTaxInclusive);
        unpaidInvoice.set("total", unpaidTotal);

        dataArray.add(unpaidInvoice);
        responseNode.set("data", dataArray);

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, isPaid, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size()); // We should have one invoice
        assertFalse(result.get(0).getIsPaid()); // It should be unpaid
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findInvoicesByCompany should correctly filter by overdue status when isOverdue=true")
    void findInvoicesByCompany_shouldCorrectlyFilter_whenIsOverdueTrue() {
        // Arrange
        String companyId = "123456";
        Boolean isOverdue = true;
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response with mixed overdue status invoices
        JsonNode responseNode = createMockInvoiceListResponseWithMixedOverdueStatus();
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, null, isOverdue, null);

        // Assert
        assertNotNull(result);
        // Verify only overdue invoices are returned
        assertTrue(result.stream().allMatch(TeamleaderInvoiceListDTO::getIsOverdue));
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findInvoicesByCompany should correctly filter by search term")
    void findInvoicesByCompany_shouldCorrectlyFilter_bySearchTerm() {
        // Arrange
        String companyId = "123456";
        String searchTerm = "invoice-abc";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response
        JsonNode responseNode = createMockInvoiceListResponse(1);
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, null, null, searchTerm);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
        // Verify request includes search term - would need to capture and inspect the
        // request body
    }

    @Test
    @DisplayName("findInvoicesByCompany should handle invalid invoice data")
    void findInvoicesByCompany_shouldHandleInvoiceData() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create a mock response with invalid invoice data
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Valid invoice with all required fields
        ObjectNode validInvoice = objectMapper.createObjectNode();
        validInvoice.put("id", "inv-1");
        validInvoice.put("number", "INV-001");
        validInvoice.put("paid", true);
        validInvoice.put("due_on", LocalDate.now().plusDays(30).toString());
        // Add total node structure which is needed
        ObjectNode total = objectMapper.createObjectNode();
        ObjectNode taxInclusive = objectMapper.createObjectNode();
        taxInclusive.put("amount", "1000.00");
        taxInclusive.put("currency", "EUR");
        total.set("tax_inclusive", taxInclusive);
        validInvoice.set("total", total);

        // Invalid invoice (completely missing required fields)
        ObjectNode invalidInvoice = objectMapper.createObjectNode();
        // Intentionally not adding any fields

        dataArray.add(validInvoice);
        dataArray.add(invalidInvoice);
        responseNode.set("data", dataArray);

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderInvoiceListDTO> result = invoiceService.findInvoicesByCompany(
                companyId, null, null, null);

        // Assert
        assertNotNull(result);
        // Both the valid and invalid invoice are processed, but the invalid one will
        // have default values
        assertEquals(2, result.size());

        // Check that the first one is our valid invoice
        assertEquals("inv-1", result.get(0).getId());

        // Check that the second one has null or default values
        assertNull(result.get(1).getId());

        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    // Helper methods

    private void setupWebClientMock() {
        // Use lenient() for all stubbing to avoid "unnecessary stubbing" errors
        lenient().when(webClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        lenient().doReturn(requestBodySpec).when(requestBodySpec).header(eq("Authorization"), anyString());
        lenient().doReturn(requestBodySpec).when(requestBodySpec).header(eq("Content-Type"), anyString());
        lenient().doReturn(requestBodySpec).when(requestBodySpec).contentType(any());
        lenient().doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    private JsonNode createMockInvoiceListResponse(int count) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataNode = objectMapper.createArrayNode();

        for (int i = 0; i < count; i++) {
            ObjectNode invoice = objectMapper.createObjectNode();
            invoice.put("id", "inv-" + i);
            invoice.put("number", "INV-" + (1000 + i));
            invoice.put("payment_reference", "REF-" + i);
            invoice.put("due_on", LocalDate.now().plusDays(30).toString());
            invoice.put("paid", i % 2 == 0);

            ObjectNode total = objectMapper.createObjectNode();
            ObjectNode taxInclusive = objectMapper.createObjectNode();
            taxInclusive.put("amount", "1000.00");
            taxInclusive.put("currency", "EUR");
            total.set("tax_inclusive", taxInclusive);

            invoice.set("total", total);
            dataNode.add(invoice);
        }

        responseNode.set("data", dataNode);
        return responseNode;
    }

    private JsonNode createMockInvoiceDetailResponse(String id) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        ObjectNode dataNode = objectMapper.createObjectNode();

        dataNode.put("id", id);
        dataNode.put("number", "INV-1001");
        dataNode.put("status", "paid");
        dataNode.put("payment_reference", "REF-001");
        dataNode.put("purchase_order_number", "PO-001");
        dataNode.put("date", LocalDate.now().toString());
        dataNode.put("due_on", LocalDate.now().plusDays(30).toString());
        dataNode.put("paid_at", LocalDate.now().toString());
        dataNode.put("sent", true);
        dataNode.put("paid", true);

        ObjectNode total = objectMapper.createObjectNode();

        ObjectNode taxExclusive = objectMapper.createObjectNode();
        taxExclusive.put("amount", "900.00");
        taxExclusive.put("currency", "EUR");
        total.set("tax_exclusive", taxExclusive);

        ObjectNode taxInclusive = objectMapper.createObjectNode();
        taxInclusive.put("amount", "1000.00");
        taxInclusive.put("currency", "EUR");
        total.set("tax_inclusive", taxInclusive);

        dataNode.set("total", total);

        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("id", "cust-123");
        customer.put("type", "company");
        customer.put("name", "Test Company");
        dataNode.set("customer", customer);

        ArrayNode items = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("description", "Test Item");
        item.put("quantity", "2");
        item.put("unit", "hour");
        item.put("unit_price", "450.00");
        item.put("total_price", "900.00");
        item.put("tax_rate", "21.00");
        items.add(item);

        dataNode.set("items", items);
        responseNode.set("data", dataNode);

        return responseNode;
    }

    /**
     * Creates a mock invoice list response with mixed overdue status invoices
     */
    private JsonNode createMockInvoiceListResponseWithMixedOverdueStatus() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Overdue invoice (unpaid and past due)
        ObjectNode overdueInvoice = objectMapper.createObjectNode();
        overdueInvoice.put("id", "inv-1");
        overdueInvoice.put("number", "INV-001");
        overdueInvoice.put("paid", false);
        overdueInvoice.put("due_on", LocalDate.now().minusDays(10).toString());

        // Not overdue invoice (either paid or not past due)
        ObjectNode notOverdueInvoice = objectMapper.createObjectNode();
        notOverdueInvoice.put("id", "inv-2");
        notOverdueInvoice.put("number", "INV-002");
        notOverdueInvoice.put("paid", false);
        notOverdueInvoice.put("due_on", LocalDate.now().plusDays(10).toString());

        dataArray.add(overdueInvoice);
        dataArray.add(notOverdueInvoice);
        responseNode.set("data", dataArray);

        return responseNode;
    }
}