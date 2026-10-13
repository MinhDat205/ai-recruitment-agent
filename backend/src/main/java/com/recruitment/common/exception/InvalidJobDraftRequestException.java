package com.recruitment.common.exception;

// FR-H11 R-N2, R-N3, R-A3 - yeu cau sinh ban nhap tin (J1) khong hop le (400 INVALID_JOB_DRAFT_REQUEST). Cung khuon
// InvalidDraftRequestException (FR-C07): mot class, factory method theo tung loai loi, cau tieng Viet co dinh (UI.md
// muc 7) - khong noi du lieu nguoi dung vao message.
public class InvalidJobDraftRequestException extends RuntimeException {

    private InvalidJobDraftRequestException(String message) {
        super(message);
    }

    // R-N2 - description null, rong, chi khoang trang.
    public static InvalidJobDraftRequestException emptyDescription() {
        return new InvalidJobDraftRequestException("Vui lòng nhập mô tả vị trí cần tuyển.");
    }

    // R-N2 - dai hon 3000 ky tu sau khi doi xuong dong.
    public static InvalidJobDraftRequestException descriptionTooLong() {
        return new InvalidJobDraftRequestException("Mô tả tối đa 3000 ký tự.");
    }

    // R-N3 - parts null hoac rong.
    public static InvalidJobDraftRequestException noParts() {
        return new InvalidJobDraftRequestException("Vui lòng chọn ít nhất một phần cần tạo.");
    }

    // R-N3 - parts co phan tu null hoac trung.
    public static InvalidJobDraftRequestException invalidParts() {
        return new InvalidJobDraftRequestException("Danh sách phần cần tạo không hợp lệ hoặc bị trùng.");
    }

    // R-A3 - body JSON hong hoac gia tri enum la. KHONG nem tu service: chi lay cau cho JobAiDraftExceptionAdvice.
    public static InvalidJobDraftRequestException unreadableBody() {
        return new InvalidJobDraftRequestException("Yêu cầu tạo tin bằng AI không hợp lệ.");
    }
}
