package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.resume.ParseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// FR-U08 E1 - GET /api/candidates/applications/{id}. DTO RIENG cho ung vien, dung tung field cua
// REQUIREMENT.md muc 4, KHONG them field nao. TUYET DOI khong tai dung DTO phia HR va khong co diem, hang,
// tieu chi, giai thich AI, luot cham, sentBy/changedBy (SRS "Nguyen tac bo sung", CLAUDE.md muc 2/7).
public record ApplicationCandidateDetailResponse(
        UUID id,
        ApplicationStatus status,
        Instant appliedAt,
        Instant updatedAt,
        String coverLetter, // nguyen van, null khi khong nhap (R-D7)
        JobInfo job,
        ResumeInfo resume) {

    // Thong tin tin la ban HIEN TAI cua dong jobs, khong phai ban luc nop (R-D3).
    public record JobInfo(
            UUID id,
            String title,
            UUID companyId,
            String companyName, // ten cong ty HIEN TAI
            JobAvailability availability, // R-D4
            String categoryCode, // 6 field danh muc tu JobCatalogFields.of (R-D6)
            String categoryLabel,
            String locationCode,
            String locationLabel,
            String legacyCategory,
            String legacyLocation,
            String employmentType,
            String workMode,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            LocalDate deadline) {}

    public record ResumeInfo(
            UUID id, // = job_applications.resume_id, KHONG phai CV chinh hien tai (R-D2)
            String fileName,
            ParseStatus parseStatus,
            String parseError) {} // "MA: mo ta" da chuan hoa hoac null (R-D8)

    // R-D4 - dung 5 gia tri. Tin da xoa mem va tin dua ve nhap GOP chung UNAVAILABLE: ung vien khong can
    // biet HR da xoa tin hay dua ve nhap.
    public enum JobAvailability {
        OPEN,
        EXPIRED,
        PAUSED,
        CLOSED,
        UNAVAILABLE
    }
}
