package com.cloudmen.backend.api.dtos.companies;

import com.cloudmen.backend.domain.enums.CompanyStatusType;
import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DTO for company list view with minimal information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyListDTO {
    private String id;
    private String teamleaderId;
    private String name;
    private String email;
    private String phoneNumber;
    private String vatNumber;
    private String status;
    private LocalDateTime syncedAt;

    /**
     * Create a CompanyListDTO from a TeamleaderCompany entity
     * 
     * @param company The TeamleaderCompany entity
     * @return A new CompanyListDTO
     */
    public static CompanyListDTO fromEntity(TeamleaderCompany company) {
        if (company == null) {
            return null;
        }

        CompanyListDTO dto = new CompanyListDTO();
        dto.setId(company.getId());
        dto.setTeamleaderId(company.getTeamleaderId());
        dto.setName(company.getName());
        dto.setVatNumber(company.getVatNumber());
        dto.setSyncedAt(company.getSyncedAt());

        // Extract email from contact info (prioritizing primary)
        if (company.getContactInfo() != null && !company.getContactInfo().isEmpty()) {
            Optional<TeamleaderCompany.ContactInfo> primaryEmail = company.getContactInfo().stream()
                    .filter(ci -> ci.getType().equals("email-primary"))
                    .findFirst();

            if (primaryEmail.isPresent()) {
                dto.setEmail(primaryEmail.get().getValue());
            } else {
                // If no primary email, look for any email
                Optional<TeamleaderCompany.ContactInfo> anyEmail = company.getContactInfo().stream()
                        .filter(ci -> ci.getType().startsWith("email"))
                        .findFirst();
                anyEmail.ifPresent(contactInfo -> dto.setEmail(contactInfo.getValue()));
            }
        }

        // Extract phone from contact info (prioritizing primary)
        if (company.getContactInfo() != null && !company.getContactInfo().isEmpty()) {
            Optional<TeamleaderCompany.ContactInfo> primaryPhone = company.getContactInfo().stream()
                    .filter(ci -> ci.getType().equals("phone-primary"))
                    .findFirst();

            if (primaryPhone.isPresent()) {
                dto.setPhoneNumber(primaryPhone.get().getValue());
            } else {
                // If no primary phone, look for any phone
                Optional<TeamleaderCompany.ContactInfo> anyPhone = company.getContactInfo().stream()
                        .filter(ci -> ci.getType().startsWith("phone"))
                        .findFirst();
                anyPhone.ifPresent(contactInfo -> dto.setPhoneNumber(contactInfo.getValue()));
            }
        }

        // Get the status from the company entity
        if (company.getStatus() != null) {
            dto.setStatus(company.getStatus().name());
        } else {
            // For backward compatibility - check customFields if status field is not set
            if (company.getCustomFields() != null && !company.getCustomFields().isEmpty()) {
                Object statusValue = company.getCustomFields().get("status");
                if (statusValue != null) {
                    dto.setStatus(statusValue.toString());
                } else {
                    dto.setStatus(CompanyStatusType.ACTIVE.name());
                }
            } else {
                dto.setStatus(CompanyStatusType.ACTIVE.name());
            }
        }

        return dto;
    }

    /**
     * Convert a list of TeamleaderCompany entities to DTOs
     * 
     * @param companies List of TeamleaderCompany entities
     * @return List of CompanyListDTOs
     */
    public static List<CompanyListDTO> fromEntities(List<TeamleaderCompany> companies) {
        return companies.stream()
                .map(CompanyListDTO::fromEntity)
                .collect(Collectors.toList());
    }
}