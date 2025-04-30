package com.cloudmen.backend.api.dtos.teamleader;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * DTO for TeamLeader invoice download response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderInvoiceDownloadDTO {

    /**
     * URL where the invoice can be downloaded
     */
    private String location;

    /**
     * Expiration time of the temporary download link
     */
    private ZonedDateTime expires;
}