package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.TeamleaderCreditNoteController;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderCreditNoteListDTO;
import com.cloudmen.backend.api.dtos.teamleader.TeamleaderInvoiceDetailDTO;
import com.cloudmen.backend.services.TeamleaderCreditNoteService;
import com.cloudmen.backend.services.TeamleaderInvoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TeamleaderCreditNoteController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamleaderCreditNoteController Integration Tests")
public class TeamleaderCreditNoteControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private TeamleaderCreditNoteService creditNoteService;

    @Mock
    private TeamleaderInvoiceService invoiceService;

    // Use a real ObjectMapper with JavaTimeModule for date handling
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Create controller directly
    private TeamleaderCreditNoteController creditNoteController;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test
        creditNoteController = new TeamleaderCreditNoteController(creditNoteService, invoiceService);

        // Create standalone MockMvc
        mockMvc = MockMvcBuilders
                .standaloneSetup(creditNoteController)
                .build();
    }

    @Test
    @DisplayName("GET /api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes - Returns credit notes when invoice belongs to company")
    void getInvoiceCreditNotes_ReturnsCreditNotes_WhenInvoiceBelongsToCompany() throws Exception {
        // Arrange
        String customerId = "company-123";
        String invoiceId = "inv-123";

        // Create test invoice
        TeamleaderInvoiceDetailDTO invoice = TeamleaderInvoiceDetailDTO.builder()
                .id(invoiceId)
                .customerId(customerId)
                .customerType("company")
                .build();

        // Create test credit notes
        TeamleaderCreditNoteListDTO creditNote1 = TeamleaderCreditNoteListDTO.builder()
                .id("cn-1")
                .number("CN-2023-001")
                .date(LocalDate.now())
                .invoiceId(invoiceId)
                .invoiceNumber("INV-2023-001")
                .total(new BigDecimal("50.00"))
                .currency("EUR")
                .build();

        TeamleaderCreditNoteListDTO creditNote2 = TeamleaderCreditNoteListDTO.builder()
                .id("cn-2")
                .number("CN-2023-002")
                .date(LocalDate.now().minusDays(5))
                .invoiceId(invoiceId)
                .invoiceNumber("INV-2023-001")
                .total(new BigDecimal("25.00"))
                .currency("EUR")
                .build();

        List<TeamleaderCreditNoteListDTO> creditNotes = Arrays.asList(creditNote1, creditNote2);

        // Setup mocks
        when(invoiceService.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(creditNoteService.findByInvoiceId(invoiceId)).thenReturn(creditNotes);

        // Act
        MvcResult result = mockMvc
                .perform(get("/api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes",
                        customerId, invoiceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        List<?> responseList = objectMapper.readValue(responseBody, List.class);

        assertEquals(2, responseList.size());

        // Verify
        verify(invoiceService).findById(invoiceId);
        verify(creditNoteService).findByInvoiceId(invoiceId);
    }

    @Test
    @DisplayName("GET /api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes - Returns 404 when invoice not found")
    void getInvoiceCreditNotes_Returns404_WhenInvoiceNotFound() throws Exception {
        // Arrange
        String customerId = "company-123";
        String invoiceId = "non-existent";

        when(invoiceService.findById(invoiceId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes",
                customerId, invoiceId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify
        verify(invoiceService).findById(invoiceId);
        verifyNoInteractions(creditNoteService);
    }

    @Test
    @DisplayName("GET /api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes - Returns 404 when invoice not owned by company")
    void getInvoiceCreditNotes_Returns404_WhenInvoiceNotOwnedByCompany() throws Exception {
        // Arrange
        String customerId = "company-123";
        String differentCustomerId = "different-company";
        String invoiceId = "inv-123";

        // Create test invoice with a different company owner
        TeamleaderInvoiceDetailDTO invoice = TeamleaderInvoiceDetailDTO.builder()
                .id(invoiceId)
                .customerId(differentCustomerId)
                .customerType("company")
                .build();

        when(invoiceService.findById(invoiceId)).thenReturn(Optional.of(invoice));

        // Act & Assert
        mockMvc.perform(get("/api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes",
                customerId, invoiceId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify
        verify(invoiceService).findById(invoiceId);
        verifyNoInteractions(creditNoteService);
    }

    @Test
    @DisplayName("GET /api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes - Returns empty list when no credit notes found")
    void getInvoiceCreditNotes_ReturnsEmptyList_WhenNoCreditNotesFound() throws Exception {
        // Arrange
        String customerId = "company-123";
        String invoiceId = "inv-123";

        // Create test invoice
        TeamleaderInvoiceDetailDTO invoice = TeamleaderInvoiceDetailDTO.builder()
                .id(invoiceId)
                .customerId(customerId)
                .customerType("company")
                .build();

        when(invoiceService.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(creditNoteService.findByInvoiceId(invoiceId)).thenReturn(Collections.emptyList());

        // Act
        MvcResult result = mockMvc
                .perform(get("/api/teamleader/finance/company/{customerId}/invoices/{invoiceId}/credit-notes",
                        customerId, invoiceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        List<?> responseList = objectMapper.readValue(responseBody, List.class);

        assertEquals(0, responseList.size());

        // Verify
        verify(invoiceService).findById(invoiceId);
        verify(creditNoteService).findByInvoiceId(invoiceId);
    }
}