package com.cloudmen.backend.api.dtos.googleworkspace;

/**
 * Simplified DTO for Google Workspace subscription information
 */
public class GoogleWorkspaceSubscriptionDTO {

    private String skuId;
    private String skuName;
    private int totalLicenses;
    private String planType;
    private String status;

    public GoogleWorkspaceSubscriptionDTO() {
    }

    public GoogleWorkspaceSubscriptionDTO(String skuId, String skuName, int totalLicenses, String planType,
            String status) {
        this.skuId = skuId;
        this.skuName = skuName;
        this.totalLicenses = totalLicenses;
        this.planType = planType;
        this.status = status;
    }

    // Getters and Setters
    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public int getTotalLicenses() {
        return totalLicenses;
    }

    public void setTotalLicenses(int totalLicenses) {
        this.totalLicenses = totalLicenses;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}