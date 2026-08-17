package com.recruitment.jobapplication.dto;

import com.recruitment.resume.ParseStatus;
import com.recruitment.scoring.ScoringRunStatus;
import java.time.Instant;
import java.util.UUID;

// GET /api/hr/jobs/{jobId}/applications (Dot 5, D2/FR-H04) - danh sach don CHO HR XEM TIEN DO, KHONG
// PHAI xem ket qua cham diem. Co y KHONG co totalScore, KHONG co rank, KHONG co diem tung tieu chi -
// day la viec cua D3 (FR-H05, tinh tong + xep hang) va D4 (giai thich co evidence), ca hai deu chua
// lam trong D2. latestScoringRun* chi phan anh TRANG THAI/TIEN DO cua lot cham GAN NHAT (neu co),
// khong phai ket qua cham.
public record ApplicationHrListItemResponse(
        UUID id,
        String candidateName,
        ParseStatus resumeParseStatus,
        Instant appliedAt,
        UUID latestScoringRunId,
        ScoringRunStatus latestScoringRunStatus,
        Instant latestScoringRunFinishedAt) {
}
