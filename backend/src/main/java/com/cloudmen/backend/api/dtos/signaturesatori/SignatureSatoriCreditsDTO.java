package com.cloudmen.backend.api.dtos.signaturesatori;

import java.util.List;

/**
 * DTO for SignatureSatori customer credit information
 */
public class SignatureSatoriCreditsDTO {
    private String customerId;
    private int creditBalance;
    private List<String> domains;
    private String ownerEmail;
    private double creditDiscountPercent;

    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public int getCreditBalance() {
        return creditBalance;
    }

    public void setCreditBalance(int creditBalance) {
        this.creditBalance = creditBalance;
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public double getCreditDiscountPercent() {
        return creditDiscountPercent;
    }

    public void setCreditDiscountPercent(double creditDiscountPercent) {
        this.creditDiscountPercent = creditDiscountPercent;
    }
}