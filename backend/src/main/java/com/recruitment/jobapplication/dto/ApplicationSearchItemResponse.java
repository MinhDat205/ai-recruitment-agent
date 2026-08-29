package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.resume.ParseStatus;
import com.recruitment.scoring.ScoringRunStatus;
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
//
// latestScoringRunId/latestScoringRunStatus/latestScoringRunFinishedAt lay tu LUOT CHAM MOI NHAT
// BAT KE TRANG THAI (LATERAL latest_run trong JobApplicationRepository.searchCandidates/
// searchCandidatesByCriterion). totalScore lay tu LUOT DONE MOI NHAT (LATERAL/JOIN rieng, loc
// status='DONE') - HAI NGUON KHAC NHAU, co the la HAI LUOT KHAC NHAU cua cung mot don (vd: da co
// luot DONE cu cho diem, roi HR bam cham lai -> luot moi dang PENDING/RUNNING: latestScoringRunId
// tro toi luot moi do, con totalScore van la diem cua luot DONE cu). KHONG duoc gia dinh
// latestScoringRunId va totalScore cung thuoc mot luot - mau dung da co san o ApplicationOwnerService
// (D3/D4): LatestScoringRunView (tien do) tach rieng LatestDoneScoringRunView (diem).
public record ApplicationSearchItemResponse(
        UUID id,
        UUID jobId,
        String jobTitle,
        String candidateName,
        ParseStatus resumeParseStatus,
        Instant appliedAt,
        ApplicationStatus status,
        UUID latestScoringRunId,
        BigDecimal totalScore,
        ScoringRunStatus latestScoringRunStatus,
        Instant latestScoringRunFinishedAt) {}
