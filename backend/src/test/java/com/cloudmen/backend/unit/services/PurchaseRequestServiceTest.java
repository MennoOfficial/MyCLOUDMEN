package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceLicenseRequestDTO;
import com.cloudmen.backend.domain.models.PurchaseRequest;
import com.cloudmen.backend.repositories.PurchaseRequestRepository;
import com.cloudmen.backend.services.PurchaseRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PurchaseRequestService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseRequestService Tests")
class PurchaseRequestServiceTest {

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @InjectMocks
    private PurchaseRequestService purchaseRequestService;

    @Captor
    private ArgumentCaptor<PurchaseRequest> purchaseRequestCaptor;

    private PurchaseRequest testRequest;
    private final String TEST_ID = "test-id";
    private final String TEST_EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        testRequest = new PurchaseRequest(TEST_ID, TEST_EMAIL);
        testRequest.setType("licenses");
        testRequest.setStatus("PENDING");
        testRequest.setRequestDate(new Date());
        testRequest.setQuantity(5);
        testRequest.setLicenseType("business_standard");
        testRequest.setDomain("example.com");
        testRequest.setCost(60.0);
    }

    @Test
    @DisplayName("getAllPurchaseRequests - Should return paginated results")
    void getAllPurchaseRequests_ShouldReturnPaginatedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<PurchaseRequest> requests = Arrays.asList(testRequest);
        Page<PurchaseRequest> page = new PageImpl<>(requests, pageable, requests.size());

        when(purchaseRequestRepository.findAll(pageable)).thenReturn(page);

        // Act
        Page<PurchaseRequest> result = purchaseRequestService.getAllPurchaseRequests(pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(requests, result.getContent());
        verify(purchaseRequestRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getPurchaseRequestsByUserEmail - Should return user's requests")
    void getPurchaseRequestsByUserEmail_ShouldReturnUserRequests() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        List<PurchaseRequest> requests = Arrays.asList(testRequest);
        Page<PurchaseRequest> page = new PageImpl<>(requests, pageable, requests.size());

        when(purchaseRequestRepository.findByUserEmail(TEST_EMAIL, pageable)).thenReturn(page);

        // Act
        Page<PurchaseRequest> result = purchaseRequestService.getPurchaseRequestsByUserEmail(TEST_EMAIL, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(requests, result.getContent());
        verify(purchaseRequestRepository).findByUserEmail(TEST_EMAIL, pageable);
    }

    @Test
    @DisplayName("getPurchaseRequestById - Should return request when found")
    void getPurchaseRequestById_ShouldReturnRequest_WhenFound() {
        // Arrange
        when(purchaseRequestRepository.findById(TEST_ID)).thenReturn(Optional.of(testRequest));

        // Act
        Optional<PurchaseRequest> result = purchaseRequestService.getPurchaseRequestById(TEST_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(TEST_ID, result.get().getId());
        verify(purchaseRequestRepository).findById(TEST_ID);
    }

    @Test
    @DisplayName("getPurchaseRequestById - Should return empty when not found")
    void getPurchaseRequestById_ShouldReturnEmpty_WhenNotFound() {
        // Arrange
        when(purchaseRequestRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act
        Optional<PurchaseRequest> result = purchaseRequestService.getPurchaseRequestById(TEST_ID);

        // Assert
        assertFalse(result.isPresent());
        verify(purchaseRequestRepository).findById(TEST_ID);
    }

    @Test
    @DisplayName("savePurchaseRequest - Should save and return request")
    void savePurchaseRequest_ShouldSaveAndReturnRequest() {
        // Arrange
        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenReturn(testRequest);

        // Act
        PurchaseRequest result = purchaseRequestService.savePurchaseRequest(testRequest);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_ID, result.getId());
        verify(purchaseRequestRepository).save(testRequest);
    }

    @Test
    @DisplayName("savePurchaseRequest - Should set processed date when status is APPROVED")
    void savePurchaseRequest_ShouldSetProcessedDate_WhenStatusIsApproved() {
        // Arrange
        testRequest.setStatus("APPROVED");

        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        PurchaseRequest result = purchaseRequestService.savePurchaseRequest(testRequest);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getProcessedDate());
        verify(purchaseRequestRepository).save(purchaseRequestCaptor.capture());

        PurchaseRequest capturedRequest = purchaseRequestCaptor.getValue();
        assertNotNull(capturedRequest.getProcessedDate());
    }

    @Test
    @DisplayName("savePurchaseRequest - Should set processed date when status is REJECTED")
    void savePurchaseRequest_ShouldSetProcessedDate_WhenStatusIsRejected() {
        // Arrange
        testRequest.setStatus("REJECTED");

        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        PurchaseRequest result = purchaseRequestService.savePurchaseRequest(testRequest);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getProcessedDate());
        verify(purchaseRequestRepository).save(purchaseRequestCaptor.capture());

        PurchaseRequest capturedRequest = purchaseRequestCaptor.getValue();
        assertNotNull(capturedRequest.getProcessedDate());
    }

    @Test
    @DisplayName("savePurchaseRequest - Should not set processed date when status is PENDING")
    void savePurchaseRequest_ShouldNotSetProcessedDate_WhenStatusIsPending() {
        // Arrange
        testRequest.setStatus("PENDING");
        testRequest.setProcessedDate(null);

        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        PurchaseRequest result = purchaseRequestService.savePurchaseRequest(testRequest);

        // Assert
        assertNotNull(result);
        assertNull(result.getProcessedDate());
        verify(purchaseRequestRepository).save(testRequest);
    }

    @Test
    @DisplayName("savePurchaseRequest - Should handle exceptions")
    void savePurchaseRequest_ShouldHandleExceptions() {
        // Arrange
        when(purchaseRequestRepository.save(any(PurchaseRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> purchaseRequestService.savePurchaseRequest(testRequest));
        verify(purchaseRequestRepository).save(testRequest);
    }

    @Test
    @DisplayName("updatePurchaseRequestStatus - Should update status when request exists")
    void updatePurchaseRequestStatus_ShouldUpdateStatus_WhenRequestExists() {
        // Arrange
        when(purchaseRequestRepository.findById(TEST_ID)).thenReturn(Optional.of(testRequest));
        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Optional<PurchaseRequest> result = purchaseRequestService.updatePurchaseRequestStatus(TEST_ID, "APPROVED");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("APPROVED", result.get().getStatus());
        assertNotNull(result.get().getProcessedDate());

        verify(purchaseRequestRepository).findById(TEST_ID);
        verify(purchaseRequestRepository).save(purchaseRequestCaptor.capture());

        PurchaseRequest capturedRequest = purchaseRequestCaptor.getValue();
        assertEquals("APPROVED", capturedRequest.getStatus());
        assertNotNull(capturedRequest.getProcessedDate());
    }

    @Test
    @DisplayName("updatePurchaseRequestStatus - Should return empty when request not found")
    void updatePurchaseRequestStatus_ShouldReturnEmpty_WhenRequestNotFound() {
        // Arrange
        when(purchaseRequestRepository.findById(TEST_ID)).thenReturn(Optional.empty());

        // Act
        Optional<PurchaseRequest> result = purchaseRequestService.updatePurchaseRequestStatus(TEST_ID, "APPROVED");

        // Assert
        assertFalse(result.isPresent());
        verify(purchaseRequestRepository).findById(TEST_ID);
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePurchaseRequestStatus - Should handle exceptions")
    void updatePurchaseRequestStatus_ShouldHandleExceptions() {
        // Arrange
        when(purchaseRequestRepository.findById(TEST_ID))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> purchaseRequestService.updatePurchaseRequestStatus(TEST_ID, "APPROVED"));

        verify(purchaseRequestRepository).findById(TEST_ID);
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("getRecentPurchaseRequests - Should return recent requests")
    void getRecentPurchaseRequests_ShouldReturnRecentRequests() {
        // Arrange
        List<PurchaseRequest> recentRequests = Arrays.asList(testRequest);
        when(purchaseRequestRepository.findTop10ByOrderByRequestDateDesc()).thenReturn(recentRequests);

        // Act
        List<PurchaseRequest> result = purchaseRequestService.getRecentPurchaseRequests(10);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(recentRequests, result);
        verify(purchaseRequestRepository).findTop10ByOrderByRequestDateDesc();
    }

    @Test
    @DisplayName("createGoogleWorkspaceLicenseRequest - Should create and save request")
    void createGoogleWorkspaceLicenseRequest_ShouldCreateAndSaveRequest() {
        // Arrange
        GoogleWorkspaceLicenseRequestDTO dto = new GoogleWorkspaceLicenseRequestDTO();
        dto.setCount(5);
        dto.setLicenseType("business_standard");
        dto.setDomain("example.com");
        dto.setCost(60.0);

        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        PurchaseRequest result = purchaseRequestService.createGoogleWorkspaceLicenseRequest(TEST_EMAIL, dto);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_EMAIL, result.getUserEmail());
        assertEquals("licenses", result.getType());
        assertEquals("business_standard", result.getLicenseType());
        assertEquals(5, result.getQuantity());
        assertEquals("example.com", result.getDomain());
        assertEquals(60.0, result.getCost());
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getRequestDate());
        assertNotNull(result.getId()); // UUID should be generated

        verify(purchaseRequestRepository).save(purchaseRequestCaptor.capture());

        PurchaseRequest capturedRequest = purchaseRequestCaptor.getValue();
        assertEquals(TEST_EMAIL, capturedRequest.getUserEmail());
        assertEquals("licenses", capturedRequest.getType());
    }

    @Test
    @DisplayName("createSignatureSatoriCreditsRequest - Should create and save request")
    void createSignatureSatoriCreditsRequest_ShouldCreateAndSaveRequest() {
        // Arrange
        Integer quantity = 50;
        Double cost = 100.0;

        when(purchaseRequestRepository.save(any(PurchaseRequest.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        PurchaseRequest result = purchaseRequestService.createSignatureSatoriCreditsRequest(TEST_EMAIL, quantity, cost);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_EMAIL, result.getUserEmail());
        assertEquals("credits", result.getType());
        assertEquals(50, result.getQuantity());
        assertEquals(100.0, result.getCost());
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getRequestDate());
        assertNotNull(result.getId()); // UUID should be generated

        verify(purchaseRequestRepository).save(purchaseRequestCaptor.capture());

        PurchaseRequest capturedRequest = purchaseRequestCaptor.getValue();
        assertEquals(TEST_EMAIL, capturedRequest.getUserEmail());
        assertEquals("credits", capturedRequest.getType());
    }
}