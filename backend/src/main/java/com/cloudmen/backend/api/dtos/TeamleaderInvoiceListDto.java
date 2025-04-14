package com.cloudmen.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Simplified DTO for TeamLeader invoice list views
 * Contains only essential fields for display in lists
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderInvoiceListDTO {

    /**
     * Invoice ID
     */
    private String id;
    
    /**
     * Invoice number as shown in TeamLeader (e.g., "2023/123")
     */
    private String invoiceNumber;
    
    /**
     * Payment reference for the invoice (e.g., +++084/2613/66074+++)
     */
    private String paymentReference;
    
    /**
     * Due date for payment
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueOn;
    
    /**
     * Total amount payable
     */
    private BigDecimal total;
    
    /**
     * Currency code (e.g., "EUR")
     */
    private String currency;
    
    /**
     * Whether the invoice has been paid
     */
    private Boolean isPaid;
    
    /**
     * Whether the invoice is overdue (due date has passed and not paid)
     */
    private Boolean isOverdue;
} 