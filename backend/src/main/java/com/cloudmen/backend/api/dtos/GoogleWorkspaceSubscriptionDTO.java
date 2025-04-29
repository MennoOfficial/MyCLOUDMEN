package com.cloudmen.backend.api.dtos;

import java.util.List;

/**
 * Simplified DTO for Google Workspace subscriptions
 */
public class GoogleWorkspaceSubscriptionDTO {

    private String skuId;
    private String skuName;
    private int totalLicenses;
    private String planType;
    private String status;

    /**
     * Response containing summary of all subscriptions
     */
    public static class SubscriptionListResponse {
        private List<GoogleWorkspaceSubscriptionDTO> subscriptions;
        private int totalSubscriptions;

        public List<GoogleWorkspaceSubscriptionDTO> getSubscriptions() {
            return subscriptions;
        }

        public void setSubscriptions(List<GoogleWorkspaceSubscriptionDTO> subscriptions) {
            this.subscriptions = subscriptions;
            this.totalSubscriptions = subscriptions != null ? subscriptions.size() : 0;
        }

        public int getTotalSubscriptions() {
            return totalSubscriptions;
        }

        public void setTotalSubscriptions(int totalSubscriptions) {
            this.totalSubscriptions = totalSubscriptions;
        }
    }

    /**
     * Request to create a new subscription
     */
    public static class CreateSubscriptionRequest {
        private String skuId;
        private String planType;
        private int numberOfLicenses;

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
