package com.recruitment.user.dto;

import com.recruitment.catalog.dto.CatalogResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// FR-U14 muc 4 - desiredIndustries/desiredLocations la {code,label} da tra nhan qua CatalogRegistry
// (giong ResumeParsedDataResponse.industry cua FR-C05), khong tra ma tran. embedding/embeddingModel
// KHONG co trong response nay - chi phuc vu scheduler noi bo (dot 3), khong phoi ra API.
public record CandidateProfileResponse(
        UUID id,
        String headline,
        String location,
        String currentTitle,
        BigDecimal yearsExperience,
        LocalDate dateOfBirth,
        Instant createdAt,
        Instant updatedAt,
        List<CatalogResponse.Item> desiredIndustries,
        List<CatalogResponse.Item> desiredLocations,
        List<String> desiredWorkModes,
        List<String> skills,
        Integer desiredSalaryMinMillions,
        String bio,
        Instant onboardingCompletedAt) {
}
