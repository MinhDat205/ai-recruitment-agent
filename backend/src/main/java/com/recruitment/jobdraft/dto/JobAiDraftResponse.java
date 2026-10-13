package com.recruitment.jobdraft.dto;

// FR-H11 J1 (muc 4.4) - mot o cho moi phan; phan khong duoc yeu cau = null. Luon 200 khi da qua kiem dau vao, ke ca khi
// moi phan FAILED (R-N6). KHONG co khoa model/promptVersion (R-K4-3) - hai gia tri do chi nam trong token.
public record JobAiDraftResponse(
        PartResult<JobDraft> job, PartResult<CriteriaDraft> criteria, PartResult<QuestionsDraft> questions) {
}
