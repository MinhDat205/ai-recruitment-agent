package com.recruitment.jobapplication.dto;

import com.recruitment.job.JobAvailability;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.reconsideration.dto.CandidateReconsiderationResponse;
import com.recruitment.resume.ParseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// FR-U08 E1 - GET /api/candidates/applications/{id}. DTO RIENG cho ung vien, dung tung field cua
// REQUIREMENT.md muc 4 cua FR-U08 + DUNG mot khoa talentPoolConsent do FR-U09 them (ngoai le da duyet, R-C2 cua
// FR-U09) + DUNG mot khoa reconsideration do FR-C09 them (ngoai le da duyet, M2/R-C1 cua FR-C09) - KHONG them field
// nao khac. TUYET DOI khong tai dung DTO phia HR va khong co diem, hang, tieu chi, giai thich AI, luot cham,
// sentBy/changedBy/decidedBy (SRS "Nguyen tac bo sung", CLAUDE.md muc 2/7).
public record ApplicationCandidateDetailResponse(
        UUID id,
        ApplicationStatus status,
        Instant appliedAt,
        Instant updatedAt,
        String coverLetter, // nguyen van, null khi khong nhap (R-D7)
        JobInfo job,
        ResumeInfo resume,
        TalentPoolConsentResponse talentPoolConsent, // FR-U09 R-D1
        CandidateReconsiderationResponse reconsideration) { // FR-C09 R-C1 - null khi khong co khoi

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

    // R-D4 - enum JobAvailability (5 gia tri) chuyen ra job/JobAvailability o FR-U16 R-A1; ten khoa va chuoi JSON giu nguyen.
}
