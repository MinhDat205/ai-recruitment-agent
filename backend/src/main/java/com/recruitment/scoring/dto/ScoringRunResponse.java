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
// ResumeParsingErrorCode.formatted() (xem ScoringRunErrorCode, Dot 4) - KHONG BAO GIO duoc chua
// stack trace hay output tho cua LLM, dung nguyen tac da ap dung cho resumes.parse_error.
//
// criteriaScored/criteriaTotal (Dot 5): CHI phuc vu hien thi tien do ("da cham N/M tieu chi") -
// KHONG suy ra diem so, KHONG tinh ti le phan tram diem tu hai so nay. criteriaTotal dem tu
// rubric_snapshot cua CHINH luot do (khong query rubric_criteria song - mot luot da tao xong thi
// so tieu chi cua no la co dinh, bat ke rubric goc co doi sau nay hay khong). criteriaScored dem
// dong criterion_scores theo scoring_run_id - int nguyen thuy (khong wrapper): luon dem duoc, "0"
// va "chua biet" khong co gi khac nhau o day (khac voi cac field diem so trong CriterionScorePayload,
// noi "0" va "khong tim thay" LA hai y nghia khac nhau can phan biet bang wrapper).
public record ScoringRunResponse(
        UUID id,
        UUID applicationId,
        ScoringRunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        int criteriaScored,
        int criteriaTotal) {
}
