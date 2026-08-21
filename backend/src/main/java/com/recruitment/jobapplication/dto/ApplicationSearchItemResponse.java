package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.resume.ParseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// GET /api/hr/candidates (F3, FR-H08) - danh sach ung vien TOAN CONG TY, phan trang that o tang
// SQL. KHONG co field rank: FR-H05 dinh nghia xep hang trong PHAM VI MOT chien dich tuyen dung
// ("...cung mot chien dich...", SRS FR-H05) - mot con so rank xuyen nhieu job khong co y nghia
// nghiep vu (xem quyet dinh #2 trong plan). totalScore la diem tho, sap theo
// total_score DESC NULLS LAST, applied_at ASC - cung thu tu tuong doi voi ApplicationOwnerService.
// sortByRank cua D3, chi khac la khong gan so thu hang.
//
// totalScore la BigDecimal, co the null khi don chua co luot DONE nao - KHONG suy dien gia tri
// thay the, cung quy uoc voi ApplicationHrListItemResponse cua D3/D4.
public record ApplicationSearchItemResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String candidateName,
        ParseStatus resumeParseStatus,
        Instant appliedAt,
        ApplicationStatus status,
        UUID latestScoringRunId,
        BigDecimal totalScore) {}
