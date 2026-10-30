package com.recruitment.jobapplication;

import java.time.Instant;
import java.util.UUID;

// Interface projection cho native query (JobApplicationRepository.findSummariesByCandidateId).
// getStatus() phai la String, KHONG phai ApplicationStatus: Spring Data khong tu convert
// String->enum cho projection cua native query (nem ConverterNotFoundException luc chay).
// Convert sang enum o tang service.
public interface ApplicationSummaryView {

    UUID getId();

    UUID getJobId();

    String getJobTitle();

    String getCompanyName();

    String getStatus();

    Instant getAppliedAt();

    Instant getUpdatedAt();

    // FR-C09 R-C2 - trang thai yeu cau xem xet lai (chuoi, null khi chua co); convert sang enum o service.
    String getReconsiderationStatus();
}
