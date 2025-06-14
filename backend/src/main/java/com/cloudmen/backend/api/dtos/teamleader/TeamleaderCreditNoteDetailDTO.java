package com.cloudmen.backend.api.dtos.teamleader;

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
 * DTO for TeamLeader credit note detail view with complete credit note
 * information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderCreditNoteDetailDTO {

    /**
     * Credit note ID in our system (MongoDB ID)
     */
    private String id;

    /**
     * Credit note number as displayed in TeamLeader
     */
    private String number;

    /**
     * Credit note date
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * Due date for the credit note if applicable
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueOn;

    /**
     * Credit note status
     */
    private String status;

    /**
     * Total credit note amount
     */
    private BigDecimal total;

    /**
     * Currency code (e.g., "EUR")
     */
    private String currency;

    /**
     * ID of the invoice this credit note is related to
     */
    private String invoiceId;

    /**
     * Number of the invoice this credit note is related to
     */
    private String invoiceNumber;

    /**
     * Payment reference for the credit note (e.g., +++084/2613/66074+++)
     */
    private String paymentReference;

    /**
     * Boolean indicating if the credit note is paid
     */
    private Boolean paid;

    /**
     * Date when the credit note was paid
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime paidAt;

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
     * Lines/items on the credit note
     */
    private List<CreditNoteLineDTO> lines;

    /**
     * Date when the credit note was created in TeamLeader
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime createdAt;

    /**
     * Date when the credit note was last updated in TeamLeader
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime updatedAt;

    /**
     * DTO for credit note line items
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreditNoteLineDTO {

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