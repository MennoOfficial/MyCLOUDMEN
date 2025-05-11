package com.cloudmen.backend.unit.services;

import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionDTO;
import com.cloudmen.backend.api.dtos.googleworkspace.GoogleWorkspaceSubscriptionListResponseDTO;
import com.cloudmen.backend.services.GoogleWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused test for GoogleWorkspaceService license matching logic
 */
@DisplayName("Google Workspace License Matching Tests")
class GoogleWorkspaceLicenseMatchingTest {

    /**
     * A test-friendly subclass of GoogleWorkspaceService that doesn't need
     * actual dependencies when we're only testing the hasMatchingLicense method
     */
    static class TestableService extends GoogleWorkspaceService {
        public TestableService() {
            super(null, null);
        }

        @Override
        public void init() {
            // No initialization needed for testing hasMatchingLicense
        }
    }

    @Test
    @DisplayName("Should match active licenses with correct name")
    void shouldMatchActiveWithCorrectName() {
        // Create service
        TestableService service = new TestableService();

        // Create active subscription
        GoogleWorkspaceSubscriptionDTO subscription = new GoogleWorkspaceSubscriptionDTO();
        subscription.setSkuName("Google Workspace Business Standard");
        subscription.setStatus("ACTIVE");
        subscription.setTotalLicenses(5);

        // Create list with single subscription
        GoogleWorkspaceSubscriptionListResponseDTO list = new GoogleWorkspaceSubscriptionListResponseDTO();
        list.setSubscriptions(Collections.singletonList(subscription));

        // Basic matching
        assertTrue(service.hasMatchingLicense(list, "Google Workspace Business Standard"));
        assertTrue(service.hasMatchingLicense(list, "Business Standard"));
    }
}