package com.recruitment.scoring;

import com.recruitment.common.FormattedErrorCode;

// Ma loi chuan hoa cho scoring_runs.error_message KHI loi KHONG den tu mot CriterionScoringFailedException
// da co ma rieng (xem CriterionScoringErrorCode, package ai.criterion) - danh cho luoi an toan cuoi
// cung o ScoringRunOrchestrator.processOne (vd resume_parsed_data bien mat giua luc claim va luc doc,
// don ung tuyen bi xoa...), mau ResumeParsingErrorCode.formatted(). Loi phat sinh trong qua trinh goi
// LLM/kiem evidence cho MOT tieu chi da co ma chi tiet rieng (CriterionScoringErrorCode) -
// ScoringRunOrchestrator truyen THANG enum do (khong phai String) vao
// ScoringRunStateService.markFailed(FormattedErrorCode), KHONG boc lai qua enum nay.
public enum ScoringRunErrorCode implements FormattedErrorCode {

    UNEXPECTED_ERROR("Có lỗi không lường trước xảy ra trong quá trình chấm điểm hồ sơ này");

    private final String description;

    ScoringRunErrorCode(String description) {
        this.description = description;
    }

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}
