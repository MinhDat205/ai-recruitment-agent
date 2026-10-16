package com.recruitment.talentpool.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

// FR-H15 P8. KHONG co diem, hang, evidence, giai thich, noi dung CV hay tin nhan (R-A2). candidateName cung nguon voi E1
// cua H09 (users.full_name). jobDeleted = jobs.deleted_at IS NOT NULL (R-M8).
public record TalentPoolEntryResponse(
        UUID applicationId,
        String candidateName,
        UUID jobId,
        String jobTitle,
        boolean jobDeleted,
        ApplicationStatus applicationStatus,
        Instant appliedAt,
        Instant addedAt) {
}
