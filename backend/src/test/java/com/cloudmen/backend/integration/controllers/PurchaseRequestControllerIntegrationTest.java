package com.cloudmen.backend.integration.controllers;

import com.cloudmen.backend.api.controllers.PurchaseRequestController;
import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.services.PurchaseRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PurchaseRequestController
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseRequestController Integration Tests")
public class PurchaseRequestControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private PurchaseRequestService purchaseRequestService;

    // Use a real ObjectMapper with JavaTimeModule for date handling
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    // Create controller directly
    private PurchaseRequestController purchaseRequestController;

    @BeforeEach
    void setUp() {
        // Create a new controller for each test
        purchaseRequestController = new PurchaseRequestController(purchaseRequestService);

        // Create standalone MockMvc
        mockMvc = MockMvcBuilders
                .standaloneSetup(purchaseRequestController)
                .build();
    }

    @Test
    @DisplayName("GET /api/purchase-requests - Returns paginated purchase requests")
    void getAllPurchaseRequests_ReturnsPaginatedResults() throws Exception {
        // Arrange
        PurchaseRequest request1 = createPurchaseRequest("1", "user1@example.com", "PENDING");
        PurchaseRequest request2 = createPurchaseRequest("2", "user2@example.com", "APPROVED");

        List<PurchaseRequest> requests = Arrays.asList(request1, request2);
        Page<PurchaseRequest> page = new PageImpl<>(requests, PageRequest.of(0, 10), 2);

        when(purchaseRequestService.getAllPurchaseRequests(any(Pageable.class))).thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/purchase-requests")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Response body for getAllPurchaseRequests: " + responseBody);

        // We'll parse the response and check basic structure
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        // Check for items field (not requests)
        assertTrue(responseMap.containsKey("items"), "Response should contain 'items' field");

        // Check items list
        List<?> items = (List<?>) responseMap.get("items");
        assertEquals(2, items.size(), "Should have 2 items");

        // Check pagination info
        assertEquals(2, responseMap.get("totalItems"));
        assertEquals(0, responseMap.get("currentPage"));
        assertEquals(1, responseMap.get("totalPages"));

        // Verify
        verify(purchaseRequestService).getAllPurchaseRequests(any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/purchase-requests/user/{email} - Returns user's purchase requests")
    void getPurchaseRequestsByUser_ReturnsUserRequests() throws Exception {
        // Arrange
        String userEmail = "user@example.com";

        PurchaseRequest request1 = createPurchaseRequest("1", userEmail, "PENDING");
        PurchaseRequest request2 = createPurchaseRequest("2", userEmail, "APPROVED");

        List<PurchaseRequest> requests = Arrays.asList(request1, request2);
        Page<PurchaseRequest> page = new PageImpl<>(requests, PageRequest.of(0, 10), 2);

        when(purchaseRequestService.getPurchaseRequestsByUserEmail(eq(userEmail), any(Pageable.class)))
                .thenReturn(page);

        // Act
        MvcResult result = mockMvc.perform(get("/api/purchase-requests/user/{email}", userEmail)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("Response body for getPurchaseRequestsByUser: " + responseBody);

        // Parse the response
        Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);

        // Check for items field (not requests)
        assertTrue(responseMap.containsKey("items"), "Response should contain 'items' field");

        // Check items list
        List<?> items = (List<?>) responseMap.get("items");
        assertEquals(2, items.size(), "Should have 2 items");

        // Check pagination info
        assertEquals(2, responseMap.get("totalItems"));

        // Verify
        verify(purchaseRequestService).getPurchaseRequestsByUserEmail(eq(userEmail), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/purchase-requests/{id} - Returns specific purchase request when found")
    void getPurchaseRequestById_ReturnsRequest_WhenFound() throws Exception {
        // Arrange
        String requestId = "request-123";
        PurchaseRequest request = createPurchaseRequest(requestId, "user@example.com", "PENDING");

        when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.of(request));

        // Act
        MvcResult result = mockMvc.perform(get("/api/purchase-requests/{id}", requestId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        PurchaseRequest responseRequest = objectMapper.readValue(responseBody, PurchaseRequest.class);

        assertEquals(requestId, responseRequest.getId());
        assertEquals("user@example.com", responseRequest.getUserEmail());
        assertEquals("PENDING", responseRequest.getStatus());

        // Verify
        verify(purchaseRequestService).getPurchaseRequestById(requestId);
    }

    @Test
    @DisplayName("GET /api/purchase-requests/{id} - Returns 404 when request not found")
    void getPurchaseRequestById_Returns404_WhenNotFound() throws Exception {
        // Arrange
        String requestId = "non-existent";

        when(purchaseRequestService.getPurchaseRequestById(requestId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/purchase-requests/{id}", requestId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify
        verify(purchaseRequestService).getPurchaseRequestById(requestId);
    }

    @Test
    @DisplayName("GET /api/purchase-requests/recent - Returns recent purchase requests")
    void getRecentPurchaseRequests_ReturnsRecentRequests() throws Exception {
        // Arrange
        PurchaseRequest request1 = createPurchaseRequest("1", "user1@example.com", "PENDING");
        PurchaseRequest request2 = createPurchaseRequest("2", "user2@example.com", "APPROVED");

        List<PurchaseRequest> recentRequests = Arrays.asList(request1, request2);

        when(purchaseRequestService.getRecentPurchaseRequests(10)).thenReturn(recentRequests);

        // Act
        MvcResult result = mockMvc.perform(get("/api/purchase-requests/recent")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        List<?> responseList = objectMapper.readValue(responseBody, List.class);

        assertEquals(2, responseList.size());

        // Verify
        verify(purchaseRequestService).getRecentPurchaseRequests(10);
    }

    @Test
    @DisplayName("PUT /api/purchase-requests/{id}/status - Updates purchase request status when valid")
    void updatePurchaseRequestStatus_UpdatesStatus_WhenValid() throws Exception {
        // Arrange
        String requestId = "request-123";
        String newStatus = "APPROVED";

        PurchaseRequest request = createPurchaseRequest(requestId, "user@example.com", "PENDING");
        request.setStatus(newStatus);

        when(purchaseRequestService.updatePurchaseRequestStatus(requestId, newStatus))
                .thenReturn(Optional.of(request));

        // Act
        MvcResult result = mockMvc.perform(put("/api/purchase-requests/{id}/status", requestId)
                .param("status", newStatus)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // Assert
        String responseBody = result.getResponse().getContentAsString();
        PurchaseRequest responseRequest = objectMapper.readValue(responseBody, PurchaseRequest.class);

        assertEquals(requestId, responseRequest.getId());
        assertEquals(newStatus, responseRequest.getStatus());

        // Verify
        verify(purchaseRequestService).updatePurchaseRequestStatus(requestId, newStatus);
    }

    @Test
    @DisplayName("PUT /api/purchase-requests/{id}/status - Returns 404 when request not found")
    void updatePurchaseRequestStatus_Returns404_WhenNotFound() throws Exception {
        // Arrange
        String requestId = "non-existent";
        String newStatus = "APPROVED";

        when(purchaseRequestService.updatePurchaseRequestStatus(requestId, newStatus))
                .thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(put("/api/purchase-requests/{id}/status", requestId)
                .param("status", newStatus)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify
        verify(purchaseRequestService).updatePurchaseRequestStatus(requestId, newStatus);
    }

    @Test
    @DisplayName("PUT /api/purchase-requests/{id}/status - Returns 400 when status invalid")
    void updatePurchaseRequestStatus_Returns400_WhenStatusInvalid() throws Exception {
        // Arrange
        String requestId = "request-123";
        String invalidStatus = "INVALID_STATUS";

        // Act & Assert
        mockMvc.perform(put("/api/purchase-requests/{id}/status", requestId)
                .param("status", invalidStatus)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Verify no service calls for invalid status
        verifyNoInteractions(purchaseRequestService);
    }

    // Helper method to create test purchase requests
    private PurchaseRequest createPurchaseRequest(String id, String email, String status) {
        PurchaseRequest request = new PurchaseRequest();
        request.setId(id);
        request.setUserEmail(email);
        request.setStatus(status);
        request.setRequestDate(new Date());
        request.setType("licenses");
        return request;
    }
}