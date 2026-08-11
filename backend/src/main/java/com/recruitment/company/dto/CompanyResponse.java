package com.recruitment.company.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        UUID ownerId,
        String name,
        String logoUrl,
        String description,
        String companySize,
        String industry,
        String website,
        String contactEmail,
        String contactPhone,
        String address,
        Instant createdAt,
        Instant updatedAt) {
}
