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
 * DTO for TeamLeader credit note list view with minimal fields needed for an
 * overview display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderCreditNoteListDto {

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
     * Credit note status
     */
    private String status;

    /**
     * Total credit note amount
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
     * ID of the invoice this credit note is related to
     */
    private String invoiceId;

    /**
     * Number of the invoice this credit note is related to
     */
    private String invoiceNumber;
}