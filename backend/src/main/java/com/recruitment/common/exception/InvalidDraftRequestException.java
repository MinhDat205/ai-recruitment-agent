package com.recruitment.common.exception;

// FR-C07 - yeu cau soan nhap khong hop le (400 INVALID_DRAFT_REQUEST). Cung khuon InvalidJobFilterException: mot class,
// nhieu factory method theo tung loai loi, cau tieng Viet co dinh (UI.md muc 7).
public class InvalidDraftRequestException extends RuntimeException {

    private InvalidDraftRequestException(String message) {
        super(message);
    }

    // R-S1 - thieu tinh huong, hoac tinh huong cua phia kia.
    public static InvalidDraftRequestException invalidScenario() {
        return new InvalidDraftRequestException("Tình huống không hợp lệ.");
    }

    // R-S5
    public static InvalidDraftRequestException missingTone() {
        return new InvalidDraftRequestException("Vui lòng chọn giọng văn.");
    }

    // R-S4
    public static InvalidDraftRequestException emptyPurpose() {
        return new InvalidDraftRequestException("Vui lòng mô tả mục đích tin nhắn.");
    }

    public static InvalidDraftRequestException purposeTooLong() {
        return new InvalidDraftRequestException("Mô tả mục đích tối đa 500 ký tự.");
    }

    // R-Q3b - body JSON hong hoac gia tri enum la. KHONG nem tu service: chi lay cau cho MessageDraftExceptionAdvice.
    public static InvalidDraftRequestException unreadableBody() {
        return new InvalidDraftRequestException("Yêu cầu soạn nháp không hợp lệ.");
    }
}
