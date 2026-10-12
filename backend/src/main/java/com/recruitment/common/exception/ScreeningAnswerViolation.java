package com.recruitment.common.exception;

// FR-U09 R-V1 - 6 truong hop loi cau tra loi sang loc, thu tu khai bao = thu tu uu tien (R-V2). Cau tieng Viet co dinh,
// dung tung chu voi bang R-V1 - khong noi so thu tu cau hay noi dung nguoi dung vao message.
public enum ScreeningAnswerViolation {
    UNKNOWN_QUESTION("Câu trả lời không khớp với câu hỏi sàng lọc của tin tuyển dụng."),
    DUPLICATE_QUESTION("Mỗi câu hỏi sàng lọc chỉ được trả lời một lần."),
    TYPE_MISMATCH("Câu trả lời không đúng loại câu hỏi sàng lọc."),
    REQUIRED_MISSING("Vui lòng trả lời đầy đủ các câu hỏi sàng lọc bắt buộc."),
    ANSWER_TOO_LONG("Câu trả lời sàng lọc tối đa 500 ký tự."),
    OPTION_NOT_FOUND("Phương án đã chọn không có trong câu hỏi sàng lọc.");

    private final String message;

    ScreeningAnswerViolation(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
