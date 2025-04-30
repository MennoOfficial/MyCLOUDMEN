package com.cloudmen.backend.api.dtos.companies;

import com.cloudmen.backend.domain.models.TeamleaderCompany;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO for detailed company information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompanyDetailDTO extends CompanyListDTO {
    private String website;
    private String businessType;
    private AddressDTO primaryAddress;
    private List<ContactInfoDTO> contactInfo;
    private Map<String, Object> customFields;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Create a CompanyDetailDTO from a TeamleaderCompany entity
     * 
     * @param company The TeamleaderCompany entity
     * @return A new CompanyDetailDTO
     */
    public static CompanyDetailDTO fromEntity(TeamleaderCompany company) {
        if (company == null) {
            return null;
        }

        // Start with the base fields from CompanyListDTO
        CompanyListDTO baseDto = CompanyListDTO.fromEntity(company);

        // Create and populate the detail DTO
        CompanyDetailDTO detailDto = new CompanyDetailDTO();

        // Copy properties from the base DTO
        detailDto.setId(baseDto.getId());
        detailDto.setTeamleaderId(baseDto.getTeamleaderId());
        detailDto.setName(baseDto.getName());
        detailDto.setEmail(baseDto.getEmail());
        detailDto.setPhoneNumber(baseDto.getPhoneNumber());
        detailDto.setVatNumber(baseDto.getVatNumber());
        detailDto.setStatus(baseDto.getStatus());
        detailDto.setSyncedAt(baseDto.getSyncedAt());

        // Add detail-specific properties
        detailDto.setWebsite(company.getWebsite());
        detailDto.setBusinessType(company.getBusinessType());
        detailDto.setCreatedAt(company.getCreatedAt());
        detailDto.setUpdatedAt(company.getUpdatedAt());

        // Convert custom fields
        if (company.getCustomFields() != null) {
            detailDto.setCustomFields(company.getCustomFields());
        }

        // Convert address
        if (company.getPrimaryAddress() != null) {
            TeamleaderCompany.Address address = company.getPrimaryAddress();
            AddressDTO addressDto = new AddressDTO(
                    address.getLine1(),
                    address.getLine2(),
                    address.getPostalCode(),
                    address.getCity(),
                    address.getCountry());
            detailDto.setPrimaryAddress(addressDto);
        }

        // Convert contact info
        if (company.getContactInfo() != null && !company.getContactInfo().isEmpty()) {
            List<ContactInfoDTO> contactInfoDtos = new ArrayList<>();

            for (TeamleaderCompany.ContactInfo info : company.getContactInfo()) {
                ContactInfoDTO infoDto = new ContactInfoDTO(info.getType(), info.getValue());
                contactInfoDtos.add(infoDto);
            }

            detailDto.setContactInfo(contactInfoDtos);
        }

        return detailDto;
    }

    /**
     * Address DTO for company
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AddressDTO {
        private String line1;
        private String line2;
        private String postalCode;
        private String city;
        private String country;
    }

    /**
     * Contact information DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ContactInfoDTO {
        private String type;
        private String value;
    }
}