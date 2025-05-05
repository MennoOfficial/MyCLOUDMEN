package com.cloudmen.backend.api.dtos.googleworkspace;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Response DTO for Google Workspace license purchase operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleWorkspaceLicenseResponseDTO {

    /**
     * The number of licenses purchased.
     */
    private Integer count;

    /**
     * The type of license purchased.
     */
    private String licenseType;

    /**
     * The domain for which licenses were purchased.
     */
    private String domain;

    /**
     * Timestamp of the purchase.
     */
    private ZonedDateTime purchaseDate;

    /**
     * Request ID associated with this purchase.
     */
    private String requestId;

    /**
     * Status of the purchase request (e.g., "PENDING", "APPROVED", "REJECTED").
     */
    private String status;

    /**
     * Additional information or message about the purchase.
     */
    private String message;
}