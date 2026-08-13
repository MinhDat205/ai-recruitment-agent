package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

// KHONG co changed_by: khong lo UUID noi bo (vd HR) cho ung vien, va tranh phai lookup them de
// hien thi ten nguoi doi. Neu can hien thi "ai doi" o UI sau nay, them field rieng luc do.
public record ApplicationHistoryEntryResponse(
        UUID id, ApplicationStatus fromStatus, ApplicationStatus toStatus, String note, Instant changedAt) {
}
