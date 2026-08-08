package com.recruitment.job.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        String title,
        String category,
        String location,
        String employmentType,
        String workMode,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        Instant publishedAt,
        CompanyRef company) {
}
