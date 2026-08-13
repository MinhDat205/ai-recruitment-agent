package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID jobId,
        UUID resumeId,
        int recruitmentCycle,
        ApplicationStatus status,
        Instant aiConsentAt,
        String coverLetter,
        Instant appliedAt,
        Instant updatedAt) {
}
