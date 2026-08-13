package com.recruitment.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @Size(max = 50) String companySize,
        @Size(max = 120) String industry,
        @Size(max = 255) String website,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 30) String contactPhone,
        String address) {
}
