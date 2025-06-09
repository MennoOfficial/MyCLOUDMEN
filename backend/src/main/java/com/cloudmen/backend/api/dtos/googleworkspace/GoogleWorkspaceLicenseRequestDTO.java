package com.cloudmen.backend.api.dtos.googleworkspace;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Google Workspace license purchase requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleWorkspaceLicenseRequestDTO {

    /**
     * The number of licenses to purchase.
     */
    @NotNull(message = "License count is required")
    @Min(value = 1, message = "At least one license must be requested")
    private Integer count;

    /**
     * The SKU ID of the license type being requested (e.g., "1010020020",
     * "1010020028").
     */
    @NotNull(message = "SKU ID is required")
    private String skuId;

    /**
     * The type of license (e.g., Business Standard, Business Plus, Enterprise).
     * This is kept for backward compatibility and display purposes.
     */
    @NotNull(message = "License type is required")
    private String licenseType;

    /**
     * Optional domain for which the licenses are being purchased.
     * If not provided, will use the domain of the requesting user.
     */
    private String domain;

    /**
     * The cost of the license purchase.
     */
    private Double cost;
}