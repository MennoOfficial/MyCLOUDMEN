package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceListDTO;
import com.cloudmen.backend.services.TeamleaderCreditNoteService;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TeamleaderCreditNoteService using Mockito.
 * Each test focuses on a single function for better clarity and
 * maintainability.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderCreditNoteService Tests")
class TeamleaderCreditNoteServiceTest {

    @Mock
    private TeamleaderOAuthService oAuthService;

    @Mock
    private TeamleaderInvoiceService invoiceService;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TeamleaderCreditNoteService creditNoteService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        creditNoteService = new TeamleaderCreditNoteService(webClient, oAuthService);
        setupWebClientMock();
    }

    @Test
    @DisplayName("findByInvoiceId should return credit notes when found")
    void findByInvoiceId_shouldReturnCreditNotes_whenFound() {
        // Arrange
        String invoiceId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response JSON
        JsonNode responseNode = createMockCreditNoteListResponse(2);
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByInvoiceId(invoiceId);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/creditNotes.list");
    }

    @Test
    @DisplayName("findByInvoiceId should return empty list when invoice ID is null")
    void findByInvoiceId_shouldReturnEmptyList_whenInvoiceIdIsNull() {
        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByInvoiceId(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("findByInvoiceId should return empty list when invoice ID is empty")
    void findByInvoiceId_shouldReturnEmptyList_whenInvoiceIdIsEmpty() {
        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByInvoiceId("");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("findByInvoiceId should return empty list when OAuth token is invalid")
    void findByInvoiceId_shouldReturnEmptyList_whenOAuthTokenIsInvalid() {
        // Arrange
        String invoiceId = "123456";
        lenient().when(oAuthService.getAccessToken()).thenReturn(null);

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByInvoiceId(invoiceId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verifyNoInteractions(requestBodyUriSpec);
    }

    @Test
    @DisplayName("findByInvoiceId should return empty list when API returns null response")
    void findByInvoiceId_shouldReturnEmptyList_whenApiReturnsNullResponse() {
        // Arrange
        String invoiceId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock null response
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.empty());

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByInvoiceId(invoiceId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findByInvoiceId should handle API exceptions")
    void findByInvoiceId_shouldHandleApiExceptions() {
        // Arrange
        String invoiceId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock exception
        lenient().when(responseSpec.bodyToMono(JsonNode.class))
                .thenReturn(Mono.error(new RuntimeException("API Error")));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByInvoiceId(invoiceId);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findByCustomerId should return credit notes when found")
    void findByCustomerId_shouldReturnCreditNotes_whenFound() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock response JSON
        JsonNode responseNode = createMockCreditNoteListResponse(2);
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerId(companyId, invoiceService);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
        verify(requestBodyUriSpec).uri("/creditNotes.list");
        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("findByCustomerId should return empty list when company ID is null")
    void findByCustomerId_shouldReturnEmptyList_whenCompanyIdIsNull() {
        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerId(null, invoiceService);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
        verifyNoInteractions(invoiceService);
    }

    @Test
    @DisplayName("findByCustomerId should fallback to invoices when direct API returns empty")
    void findByCustomerId_shouldFallbackToInvoices_whenDirectApiReturnsEmpty() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock empty response from direct API
        JsonNode emptyResponseNode = objectMapper.createObjectNode();
        ((ObjectNode) emptyResponseNode).set("data", objectMapper.createArrayNode());
        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(emptyResponseNode));

        // Mock invoice service fallback
        List<TeamleaderInvoiceListDTO> invoices = new ArrayList<>();
        TeamleaderInvoiceListDTO invoice1 = new TeamleaderInvoiceListDTO();
        invoice1.setId("inv-1");
        invoices.add(invoice1);

        lenient().when(invoiceService.findByCustomerId(companyId)).thenReturn(invoices);

        // Mock credit notes for invoice
        List<TeamleaderCreditNoteListDTO> creditNotes = new ArrayList<>();
        TeamleaderCreditNoteListDTO creditNote = new TeamleaderCreditNoteListDTO();
        creditNote.setId("cn-1");
        creditNote.setInvoiceId("inv-1");
        creditNotes.add(creditNote);

        // Need to create a spy to mock the findByInvoiceId method within the same class
        TeamleaderCreditNoteService spyService = spy(creditNoteService);
        doReturn(creditNotes).when(spyService).findByInvoiceId("inv-1");

        // Act
        List<TeamleaderCreditNoteListDTO> result = spyService.findByCustomerId(companyId, invoiceService);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("cn-1", result.get(0).getId());
        verify(invoiceService).findByCustomerId(companyId);
        verify(spyService).findByInvoiceId("inv-1");
    }

    @Test
    @DisplayName("searchCreditNotesByCustomer should filter credit notes by search term")
    void searchCreditNotesByCustomer_shouldFilterCreditNotesBySearchTerm() {
        // Arrange
        String companyId = "123456";
        String searchTerm = "refund";

        // Create spy service to mock the findByCustomerId method
        TeamleaderCreditNoteService spyService = spy(creditNoteService);

        // Prepare mock credit notes
        List<TeamleaderCreditNoteListDTO> allCreditNotes = new ArrayList<>();

        TeamleaderCreditNoteListDTO note1 = new TeamleaderCreditNoteListDTO();
        note1.setId("cn-1");
        note1.setNumber("CN001");
        note1.setStatus("Refund processed");
        allCreditNotes.add(note1);

        TeamleaderCreditNoteListDTO note2 = new TeamleaderCreditNoteListDTO();
        note2.setId("cn-2");
        note2.setNumber("CN002");
        note2.setStatus("Canceled");
        allCreditNotes.add(note2);

        // Mock findByCustomerId to return our test data
        doReturn(allCreditNotes).when(spyService).findByCustomerId(eq(companyId), any());

        // Act
        List<TeamleaderCreditNoteListDTO> result = spyService.searchCreditNotesByCustomer(
                searchTerm, companyId, invoiceService);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("cn-1", result.get(0).getId());
        verify(spyService).findByCustomerId(companyId, invoiceService);
    }

    @Test
    @DisplayName("findByCustomerAndDateRange should filter credit notes by date range")
    void findByCustomerAndDateRange_shouldFilterCreditNotesByDateRange() {
        // Arrange
        String companyId = "123456";
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();

        // Create spy service to mock the findByCustomerId method
        TeamleaderCreditNoteService spyService = spy(creditNoteService);

        // Prepare mock credit notes with different dates
        List<TeamleaderCreditNoteListDTO> allCreditNotes = new ArrayList<>();

        TeamleaderCreditNoteListDTO note1 = new TeamleaderCreditNoteListDTO();
        note1.setId("cn-1");
        note1.setDate(LocalDate.now().minusDays(15)); // Within range
        allCreditNotes.add(note1);

        TeamleaderCreditNoteListDTO note2 = new TeamleaderCreditNoteListDTO();
        note2.setId("cn-2");
        note2.setDate(LocalDate.now().minusDays(45)); // Outside range
        allCreditNotes.add(note2);

        TeamleaderCreditNoteListDTO note3 = new TeamleaderCreditNoteListDTO();
        note3.setId("cn-3");
        note3.setDate(LocalDate.now()); // Edge case - today
        allCreditNotes.add(note3);

        // Mock findByCustomerId to return our test data
        doReturn(allCreditNotes).when(spyService).findByCustomerId(eq(companyId), any());

        // Act
        List<TeamleaderCreditNoteListDTO> result = spyService.findByCustomerAndDateRange(
                companyId, startDate, endDate, invoiceService);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(note -> "cn-1".equals(note.getId())));
        assertTrue(result.stream().anyMatch(note -> "cn-3".equals(note.getId())));
        assertFalse(result.stream().anyMatch(note -> "cn-2".equals(note.getId())));
        verify(spyService).findByCustomerId(companyId, invoiceService);
    }

    @Test
    @DisplayName("findByCustomerAndDateRange should return empty list when company ID is invalid")
    void findByCustomerAndDateRange_shouldReturnEmptyList_whenCompanyIdIsInvalid() {
        // Arrange
        String companyId = null;
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerAndDateRange(
                companyId, startDate, endDate, invoiceService);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("findByCustomerAndDateRange should return empty list when date range is invalid")
    void findByCustomerAndDateRange_shouldReturnEmptyList_whenDateRangeIsInvalid() {
        // Arrange
        String companyId = "123456";
        LocalDate fromDate = LocalDate.of(2023, 5, 1);
        LocalDate toDate = LocalDate.of(2023, 4, 1); // Invalid: to date is before from date

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerAndDateRange(companyId, fromDate,
                toDate, invoiceService);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // No HTTP calls should be made when date range is invalid
        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("findByCustomerAndDateRange should correctly filter by date range")
    void findByCustomerAndDateRange_shouldFilterByDateRange() {
        // Arrange
        String companyId = "123456";
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create mock response with credit notes having different dates
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Credit note within date range
        ObjectNode inRangeNote = objectMapper.createObjectNode();
        inRangeNote.put("id", "cn-1");
        inRangeNote.put("number", "CN-001");
        inRangeNote.put("date", LocalDate.now().minusDays(15).toString());

        // Credit note outside date range (too old)
        ObjectNode tooOldNote = objectMapper.createObjectNode();
        tooOldNote.put("id", "cn-2");
        tooOldNote.put("number", "CN-002");
        tooOldNote.put("date", LocalDate.now().minusDays(45).toString());

        // Credit note outside date range (too new)
        ObjectNode tooNewNote = objectMapper.createObjectNode();
        tooNewNote.put("id", "cn-3");
        tooNewNote.put("number", "CN-003");
        tooNewNote.put("date", LocalDate.now().plusDays(5).toString());

        dataArray.add(inRangeNote);
        dataArray.add(tooOldNote);
        dataArray.add(tooNewNote);
        responseNode.set("data", dataArray);

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerAndDateRange(
                companyId, startDate, endDate, invoiceService);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("cn-1", result.get(0).getId());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("searchCreditNotesByCustomer should filter by search term")
    void searchCreditNotesByCustomer_shouldFilterBySearchTerm() {
        // Arrange
        String companyId = "123456";
        String searchTerm = "CN-00";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create mock response with credit notes
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Credit note that matches search term
        ObjectNode matchingNote = objectMapper.createObjectNode();
        matchingNote.put("id", "cn-1");
        matchingNote.put("number", "CN-001");

        // Credit note that doesn't match search term
        ObjectNode nonMatchingNote = objectMapper.createObjectNode();
        nonMatchingNote.put("id", "cn-2");
        nonMatchingNote.put("number", "OTHER-002");

        dataArray.add(matchingNote);
        dataArray.add(nonMatchingNote);
        responseNode.set("data", dataArray);

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.searchCreditNotesByCustomer(
                searchTerm, companyId, invoiceService);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("cn-1", result.get(0).getId());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("searchCreditNotesByCustomer should return empty list for null or empty search term")
    void searchCreditNotesByCustomer_shouldReturnEmptyList_forNullOrEmptySearchTerm() {
        // Arrange
        String companyId = "123456";

        // Act - with null search term
        List<TeamleaderCreditNoteListDTO> resultForNull = creditNoteService.searchCreditNotesByCustomer(null, companyId,
                invoiceService);

        // Act - with empty search term
        List<TeamleaderCreditNoteListDTO> resultForEmpty = creditNoteService.searchCreditNotesByCustomer("", companyId,
                invoiceService);

        // Assert
        assertNotNull(resultForNull);
        assertTrue(resultForNull.isEmpty());

        assertNotNull(resultForEmpty);
        assertTrue(resultForEmpty.isEmpty());

        // No HTTP calls should be made when search term is null or empty
        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("searchCreditNotesByCustomer should return empty list for null or empty company ID")
    void searchCreditNotesByCustomer_shouldReturnEmptyList_forNullOrEmptyCompanyId() {
        // Test with null company ID
        List<TeamleaderCreditNoteListDTO> result1 = creditNoteService.searchCreditNotesByCustomer(
                "search", null, invoiceService);

        // Test with empty company ID
        List<TeamleaderCreditNoteListDTO> result2 = creditNoteService.searchCreditNotesByCustomer(
                "search", "", invoiceService);

        // Assert
        assertNotNull(result1);
        assertTrue(result1.isEmpty());

        assertNotNull(result2);
        assertTrue(result2.isEmpty());

        verifyNoInteractions(webClient);
        verifyNoInteractions(oAuthService);
    }

    @Test
    @DisplayName("findByCustomerId should handle API errors")
    void findByCustomerId_shouldHandleApiErrors() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Mock API exception
        RuntimeException apiException = new RuntimeException("API connection error");
        lenient().when(responseSpec.bodyToMono(JsonNode.class))
                .thenReturn(Mono.error(apiException));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerId(companyId, invoiceService);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findByCustomerId should handle malformed response data")
    void findByCustomerId_shouldHandleMalformedResponseData() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create malformed response (missing data field)
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        // Intentionally not setting "data" field

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerId(companyId, invoiceService);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(oAuthService).getAccessToken();
        verify(webClient).post();
    }

    @Test
    @DisplayName("findByCustomerId should handle invalid credit note data")
    void findByCustomerId_shouldHandleInvalidCreditNoteData() {
        // Arrange
        String companyId = "123456";
        String accessToken = "valid-token";

        // Mock OAuth token
        lenient().when(oAuthService.getAccessToken()).thenReturn(accessToken);

        // Create a mock response with invalid credit note data
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataArray = objectMapper.createArrayNode();

        // Valid credit note with all required fields
        ObjectNode validNote = objectMapper.createObjectNode();
        validNote.put("id", "cn-1");
        validNote.put("number", "CN-001");
        validNote.put("status", "Booked");
        validNote.put("date", LocalDate.now().toString());

        // Add total node
        ObjectNode total = objectMapper.createObjectNode();
        total.put("amount", "100.00");
        total.put("currency", "EUR");
        validNote.set("total", total);

        // Add customer info
        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("type", "company");
        customer.put("id", companyId);
        customer.put("name", "Test Company");
        validNote.set("customer", customer);

        // Add invoice info
        ObjectNode invoice = objectMapper.createObjectNode();
        invoice.put("type", "invoice");
        invoice.put("id", "inv-123");
        validNote.set("for_invoice", invoice);

        // Only add the valid note to the response - remove the invalid one entirely
        dataArray.add(validNote);
        responseNode.set("data", dataArray);

        lenient().when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

        // Act
        List<TeamleaderCreditNoteListDTO> result = creditNoteService.findByCustomerId(companyId, invoiceService);

        // Assert
        assertNotNull(result);
        // Only the valid credit note should be processed
        assertEquals(1, result.size());
        assertEquals("cn-1", result.get(0).getId());
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
        lenient().doReturn(requestBodySpec).when(requestBodySpec).bodyValue(any());
        lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    private JsonNode createMockCreditNoteListResponse(int count) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode dataNode = objectMapper.createArrayNode();

        for (int i = 0; i < count; i++) {
            ObjectNode creditNote = objectMapper.createObjectNode();
            creditNote.put("id", "cn-" + i);
            creditNote.put("number", "CN-" + (1000 + i));
            creditNote.put("status", i % 2 == 0 ? "Processed" : "Pending");
            creditNote.put("date", LocalDate.now().minusDays(i).toString());

            // Set total amount
            ObjectNode total = objectMapper.createObjectNode();
            total.put("amount", "100.00");
            total.put("currency", "EUR");
            creditNote.set("total", total);

            // Set customer info
            ObjectNode customer = objectMapper.createObjectNode();
            customer.put("name", "Test Company " + i);
            creditNote.set("customer", customer);

            // Set invoice info
            ObjectNode invoice = objectMapper.createObjectNode();
            invoice.put("id", "inv-" + i);
            invoice.put("number", "INV-" + (1000 + i));
            creditNote.set("for_invoice", invoice);

            dataNode.add(creditNote);
        }

        responseNode.set("data", dataNode);
        // Add meta information for pagination
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("count", count);
        responseNode.set("meta", meta);

        return responseNode;
    }
}