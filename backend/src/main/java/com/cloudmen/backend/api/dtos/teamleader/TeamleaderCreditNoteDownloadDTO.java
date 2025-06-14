package com.cloudmen.backend.api.dtos.teamleader;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * DTO for TeamLeader credit note download response
 * Contains the temporary download URL and expiration time
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamleaderCreditNoteDownloadDTO {

    /**
     * Temporary URL where the credit note file can be downloaded
     */
    private String location;

    /**
     * Expiration time of the temporary download link
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private ZonedDateTime expires;
}