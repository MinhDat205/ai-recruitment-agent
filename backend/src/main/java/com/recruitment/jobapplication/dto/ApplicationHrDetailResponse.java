package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.resume.ParseStatus;
import java.time.Instant;
import java.util.UUID;

// FR-H09 E1 - GET /api/hr/applications/{id}: dau trang ho so don (R-D1). KHONG chua diem, hang hay
// giai thich - moi tab tai du lieu rieng (E2-E5). KHONG co field verdict/label/isQualified/passed/
// recommendation (CLAUDE.md muc 7).
//
// coverLetter: nguyen van nhu da luu (R-D8), null khi ung vien khong nhap - khong trim/cat/loc o day.
// resumeParseError: chi chua chuoi "MA: mo ta" da chuan hoa (cot resumes.parse_error, xem
// ResumeParsingErrorCode) hoac null - khong bao gio la output tho cua LLM.
public record ApplicationHrDetailResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String candidateName,
        ApplicationStatus status,
        Instant appliedAt,
        String coverLetter,
        ParseStatus resumeParseStatus,
        String resumeParseError,
        String resumeFileName) {
}
