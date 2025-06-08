package com.cloudmen.backend.domain.models;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Model for storing purchase requests in MongoDB.
 */
@Document(collection = "purchase_requests")
public class PurchaseRequest {

    @Id
    private String id;
    private String userEmail;
    private Date requestDate;
    private Date processedDate;
    private String status; // PENDING, APPROVED, REJECTED
    private String type; // licenses, credits, etc.
    private String skuId; // SKU ID for Google Workspace licenses (e.g., "1010020020")
    private String licenseType; // Type of license for Google Workspace (kept for backward compatibility)
    private Integer quantity; // Number of licenses or credits
    private String domain; // Domain for license requests
    private Double cost; // Cost of the purchase request

    public PurchaseRequest() {
        // Default constructor
    }

    public PurchaseRequest(String id, String userEmail) {
        this.id = id;
        this.userEmail = userEmail;
        this.requestDate = new Date();
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Date getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }

    public Date getProcessedDate() {
        return processedDate;
    }

    public void setProcessedDate(Date processedDate) {
        this.processedDate = processedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    @Override
    public String toString() {
        return "PurchaseRequest{" +
                "id='" + id + '\'' +
                ", userEmail='" + userEmail + '\'' +
                ", requestDate=" + requestDate +
                ", processedDate=" + processedDate +
                ", status='" + status + '\'' +
                ", type='" + type + '\'' +
                ", skuId='" + skuId + '\'' +
                ", licenseType='" + licenseType + '\'' +
                ", quantity=" + quantity +
                ", domain='" + domain + '\'' +
                ", cost=" + cost +
                '}';
    }
}