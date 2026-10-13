package com.recruitment.common.exception;

// FR-H11 R-W2, R-W3, R-L4 (6) - danh sach tieu chi/cau hoi cua J2 khong hop le (400 INVALID_JOB_DRAFT_SAVE). Cau tieng
// Viet co dinh (UI.md muc 7); chi ghep so thu tu (1-based) va cau co dinh cua ScreeningQuestionViolation - khong bao gio
// noi du lieu nguoi dung vao message.
public class InvalidJobDraftSaveException extends RuntimeException {

    private InvalidJobDraftSaveException(String message) {
        super(message);
    }

    // Q16
    public static InvalidJobDraftSaveException tooManyCriteria() {
        return new InvalidJobDraftSaveException("Tối đa 10 tiêu chí.");
    }

    public static InvalidJobDraftSaveException criterionNameEmpty(int index) {
        return new InvalidJobDraftSaveException("Tiêu chí " + index + ": vui lòng nhập tên tiêu chí.");
    }

    public static InvalidJobDraftSaveException criterionNameTooLong(int index) {
        return new InvalidJobDraftSaveException("Tiêu chí " + index + ": tên tiêu chí tối đa 200 ký tự.");
    }

    // R-W2, Q1 - chan, KHONG tu bo tieu chi.
    public static InvalidJobDraftSaveException criterionWeightMissing(int index) {
        return new InvalidJobDraftSaveException("Tiêu chí " + index + ": vui lòng nhập trọng số.");
    }

    public static InvalidJobDraftSaveException criterionWeightOutOfRange(int index) {
        return new InvalidJobDraftSaveException("Tiêu chí " + index + ": trọng số phải lớn hơn 0 và tối đa 100.");
    }

    public static InvalidJobDraftSaveException criterionNameDuplicate(int index) {
        return new InvalidJobDraftSaveException("Tiêu chí " + index + ": tên tiêu chí bị trùng.");
    }

    // R-Q4
    public static InvalidJobDraftSaveException tooManyQuestions() {
        return new InvalidJobDraftSaveException("Tối đa 10 câu hỏi sàng lọc.");
    }

    public static InvalidJobDraftSaveException questionTypeMissing(int index) {
        return new InvalidJobDraftSaveException("Câu hỏi " + index + ": vui lòng chọn loại câu hỏi.");
    }

    // R-Q2 - quy tac FR-H10 qua ScreeningQuestionRules.
    public static InvalidJobDraftSaveException questionInvalid(int index, ScreeningQuestionViolation violation) {
        return new InvalidJobDraftSaveException("Câu hỏi " + index + ": " + violation.message());
    }
}
