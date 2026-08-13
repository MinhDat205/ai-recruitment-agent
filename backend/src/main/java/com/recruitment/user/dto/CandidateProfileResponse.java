package com.recruitment.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CandidateProfileResponse(
        UUID id,
        String headline,
        String location,
        String currentTitle,
        BigDecimal yearsExperience,
        LocalDate dateOfBirth,
        Instant createdAt,
        Instant updatedAt) {
}
