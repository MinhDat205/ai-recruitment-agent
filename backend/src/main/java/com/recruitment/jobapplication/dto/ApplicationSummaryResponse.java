package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.reconsideration.ReconsiderationStatus;
import java.time.Instant;
import java.util.UUID;

public record ApplicationSummaryResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String companyName,
        ApplicationStatus status,
        Instant appliedAt,
        Instant updatedAt,
        ReconsiderationStatus reconsiderationStatus) { // FR-C09 R-C2 - null khi don chua co yeu cau xem xet lai
}
