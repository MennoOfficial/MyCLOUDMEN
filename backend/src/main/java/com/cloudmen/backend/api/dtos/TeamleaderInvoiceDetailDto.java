package com.cloudmen.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * DTO for TeamLeader invoice detail view with complete invoice information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderInvoiceDetailDTO {

    /**
     * Invoice ID in our system (MongoDB ID)
     */
    private String id;

    /**
     * Invoice number as displayed in TeamLeader
     */
    private String number;

    /**
     * Invoice date
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * Due date for payment
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueOn;

    /**
     * Invoice status (e.g., "draft", "outstanding", "paid")
     */
    private String status;

    /**
     * Total invoice amount
     */
    private BigDecimal total;

    /**
     * Currency code (e.g., "EUR")
     */
    private String currency;

    /**
     * Payment reference for the invoice (e.g., +++084/2613/66074+++)
     */
    private String paymentReference;

    /**
     * Customer ID in TeamLeader
     */
    private String customerId;

    /**
     * Customer type ("company" or "contact")
     */
    private String customerType;

    /**
     * Customer name (company or contact)
     */
    private String customerName;

    /**
     * Department ID in TeamLeader
     */
    private String departmentId;

    /**
     * Department name
     */
    private String departmentName;

    /**
     * Whether the invoice has been paid
     */
    private Boolean isPaid;

    /**
     * Whether the invoice is overdue (calculated field)
     */
    private Boolean isOverdue;

    /**
     * Date when the invoice was paid
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paidAt;

    /**
     * Payment method used (e.g., "cash", "bank_transfer")
     */
    private String paymentMethod;

    /**
     * Lines/items on the invoice
     */
    private List<InvoiceLineDTO> lines;

    /**
     * Date when the invoice was created in TeamLeader
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime createdAt;

    /**
     * Date when the invoice was last updated in TeamLeader
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime updatedAt;

    /**
     * DTO for invoice line items
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InvoiceLineDTO {

        /**
         * Line item description
         */
        private String description;

        /**
         * Quantity of items
         */
        private BigDecimal quantity;

        /**
         * Unit of measurement (e.g., "piece", "hour")
         */
        private String unit;

        /**
         * Price per unit
         */
        private BigDecimal unitPrice;

        /**
         * Total price for this line item
         */
        private BigDecimal totalPrice;

        /**
         * Tax rate applied to this line item
         */
        private BigDecimal taxRate;

        /**
         * Product ID in TeamLeader if this line is associated with a product
         */
        private String productId;
    }
}