package com.recruitment.common.exception;

// FR-H10 R-Q4b - 7 truong hop loi dau vao cua cau hoi sang loc, thu tu khai bao = thu tu kiem. Cau tieng Viet
// co dinh, dung tung chu voi bang R-Q4b va UI.md muc 7 - khong noi them du lieu nguoi dung vao message.
public enum ScreeningQuestionViolation {
    QUESTION_TEXT_EMPTY("Vui lòng nhập nội dung câu hỏi."),
    QUESTION_TEXT_TOO_LONG("Nội dung câu hỏi tối đa 300 ký tự."),
    OPTION_COUNT("Câu hỏi một lựa chọn cần từ 2 đến 10 phương án."),
    OPTION_EMPTY("Phương án không được để trống."),
    OPTION_TOO_LONG("Mỗi phương án tối đa 100 ký tự."),
    OPTION_DUPLICATE("Các phương án không được trùng nhau."),
    OPTIONS_NOT_ALLOWED("Chỉ câu hỏi một lựa chọn mới có phương án.");

    private final String message;

    ScreeningQuestionViolation(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
