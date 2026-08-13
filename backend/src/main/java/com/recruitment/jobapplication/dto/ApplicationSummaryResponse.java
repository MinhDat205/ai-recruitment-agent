package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationSummaryResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String companyName,
        ApplicationStatus status,
        Instant appliedAt,
        Instant updatedAt) {
}
