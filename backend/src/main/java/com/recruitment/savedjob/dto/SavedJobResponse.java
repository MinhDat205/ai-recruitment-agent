package com.recruitment.savedjob.dto;

import com.recruitment.job.JobAvailability;
import com.recruitment.job.dto.CompanyRef;
import com.recruitment.job.dto.JobSummaryResponse;
import com.recruitment.jobapplication.ApplicationStatus;
import java.time.Instant;
import java.util.UUID;

// FR-U16 E3 (REQUIREMENT muc 4.3) - mot dong "Tin da luu". KHONG them field nao khac; khong diem, khong hang, khong do
// phu hop (R-G2), khong candidateId/deletedAt.
public record SavedJobResponse(
        UUID jobId,
        String title, // ban HIEN TAI cua tin
        CompanyRef company, // ten + logo HIEN TAI
        Instant savedAt,
        JobAvailability availability, // R-A, dung 5 gia tri
        JobSummaryResponse summary, // null khi availability = UNAVAILABLE (R-V6)
        CurrentApplication application) { // null khi chua co don o chu ky hien tai (R-V3)

    public record CurrentApplication(UUID id, ApplicationStatus status) {}
}
