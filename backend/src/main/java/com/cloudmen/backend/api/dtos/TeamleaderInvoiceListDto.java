package com.cloudmen.backend.api.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for TeamLeader invoice list view with minimal fields needed for an
 * overview display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderInvoiceListDto {

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
    @JsonProperty("total")
    private BigDecimal total = BigDecimal.ZERO;

    @JsonProperty("total_formatted")
    public String getTotalFormatted() {
        return total != null ? total.toString() : "0";
    }

    /**
     * Currency code (e.g., "EUR")
     */
    private String currency;

    /**
     * Customer name (company or contact)
     */
    private String customerName;

    /**
     * Whether the invoice has been paid
     */
    private Boolean isPaid;

    /**
     * Whether the invoice is overdue (calculated field)
     */
    private Boolean isOverdue;
}