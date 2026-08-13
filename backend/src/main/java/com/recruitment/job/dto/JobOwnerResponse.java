package com.recruitment.job.dto;

import com.recruitment.job.JobStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JobOwnerResponse(
        UUID id,
        UUID companyId,
        String title,
        String description,
        String requirements,
        String category,
        String location,
        String employmentType,
        String workMode,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        JobStatus status,
        int recruitmentCycle,
        LocalDate deadline,
        Instant publishedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID rubricId,
        UUID interviewTemplateId) {
}
