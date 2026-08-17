package com.recruitment.scoring.dto;

import com.recruitment.scoring.ScoringRunStatus;
import java.time.Instant;
import java.util.UUID;

// KHONG co totalScore, KHONG co rank - D2 khong duoc tinh/hien thi hai thu do (viec cua D3, FR-H05).
//
// Dung chung cho CA POST tao luot cham (Dot 2) LAN GET xem tien do (Dot 5) - cung mot tai nguyen
// "mot luot cham", chi khac thoi diem doc. startedAt/finishedAt/errorMessage deu la field co san
// tren entity, khong can query them - rieng criteriaScored/criteriaTotal (Dot 5) can dem
// criterion_scores nen KHONG dua vao day, de lai cho luc thuc su can.
//
// finishedAt: tin hieu DUY NHAT de FE dung polling (Q1, ke hoach D2) - khac null la D2 da cham
// xong (RUNNING cho D3) hoac da FAILED, khong phan biet hai truong hop do o day, chi status moi
// phan biet.
//
// errorMessage: CHI duoc chua "MA: mo ta tieng Viet co dinh" theo dung mau
// ResumeParsingErrorCode.formatted() (xem ScoringRunErrorCode se lam o Dot 4) - KHONG BAO GIO duoc
// chua stack trace hay output tho cua LLM, dung nguyen tac da ap dung cho resumes.parse_error.
// Dot 4 (noi thuc su ghi cot scoring_runs.error_message) phai tuan dung dieu nay.
public record ScoringRunResponse(
        UUID id,
        UUID applicationId,
        ScoringRunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage) {
}
