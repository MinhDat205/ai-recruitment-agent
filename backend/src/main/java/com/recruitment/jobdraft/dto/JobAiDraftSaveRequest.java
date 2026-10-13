package com.recruitment.jobdraft.dto;

import com.recruitment.interviewtemplate.dto.InterviewTemplateRequest;
import com.recruitment.job.dto.JobRequest;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

// FR-H11 J2 (muc 4.4). job/interviewTemplate dung lai DTO cua FR-H02 va @Valid nhu POST /api/hr/jobs (R-L4 (3)).
// criteria/screeningQuestions KHONG @Valid tung phan tu: loi tra theo so thu tu tieu chi/cau hoi (R-W3, R-L4 (6)) qua
// INVALID_JOB_DRAFT_SAVE. Khong co truong trang thai (R-L3) va khong co model/promptVersion/generatedAt (R-K4-2) - chi
// token K4 da ky. List null -> List.of(); KHONG List.copyOf (phan tu null phai den duoc buoc kiem).
public record JobAiDraftSaveRequest(
        @Valid @NotNull JobRequest job,
        String benefits,
        @Valid @NotNull InterviewTemplateRequest interviewTemplate,
        List<CriterionInput> criteria,
        List<ScreeningQuestionRequest> screeningQuestions,
        String jobToken,
        String criteriaToken,
        String questionsToken) {

    public JobAiDraftSaveRequest {
        criteria = criteria == null ? List.of() : criteria;
        screeningQuestions = screeningQuestions == null ? List.of() : screeningQuestions;
    }

    // Trong so do HR nhap (R-W1) - co the null o day de tra loi "vui long nhap trong so" theo so thu tu (R-W2).
    public record CriterionInput(String name, String description, BigDecimal weight) {
    }
}
