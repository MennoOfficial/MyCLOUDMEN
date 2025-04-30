package com.cloudmen.backend.api.dtos.googleworkspace;

/**
 * Request to create a new Google Workspace subscription
 */
public class GoogleWorkspaceCreateSubscriptionRequestDTO {
    private String skuId;
    private String planType;
    private int numberOfLicenses;

    public GoogleWorkspaceCreateSubscriptionRequestDTO() {
    }

    public GoogleWorkspaceCreateSubscriptionRequestDTO(String skuId, String planType, int numberOfLicenses) {
        this.skuId = skuId;
        this.planType = planType;
        this.numberOfLicenses = numberOfLicenses;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public int getNumberOfLicenses() {
        return numberOfLicenses;
    }

    public void setNumberOfLicenses(int numberOfLicenses) {
        this.numberOfLicenses = numberOfLicenses;
    }
}