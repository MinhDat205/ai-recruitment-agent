package com.recruitment.common.exception;

// FR-U12 R-A2 - yeu cau goi y dien dat khong hop le (400 INVALID_CV_REPHRASE_REQUEST). Khuon InvalidCvQaQuestionException:
// mot class, moi loai loi mot factory, cau tieng Viet co dinh (muc 4.4) - KHONG nhan String tu do, khong chua gia tri
// nguoi dung da go.
public class InvalidCvRephraseRequestException extends RuntimeException {

    private InvalidCvRephraseRequestException(String message) {
        super(message);
    }

    // Body khong doc duoc (JSON hong, kind la) - CvBuilderExceptionAdvice dung cau nay cho duong dan B4.
    public static InvalidCvRephraseRequestException unreadableBody() {
        return new InvalidCvRephraseRequestException("Yêu cầu gợi ý diễn đạt không hợp lệ.");
    }

    // text null / rong sau chuan hoa R-F3
    public static InvalidCvRephraseRequestException emptyText() {
        return new InvalidCvRephraseRequestException("Vui lòng nhập đoạn mô tả trước khi xin gợi ý.");
    }

    // String.length() sau R-F3 < 20
    public static InvalidCvRephraseRequestException textTooShort() {
        return new InvalidCvRephraseRequestException("Đoạn mô tả cần ít nhất 20 ký tự để gợi ý diễn đạt.");
    }

    // String.length() sau R-F3 > 800 (O3)
    public static InvalidCvRephraseRequestException textTooLong() {
        return new InvalidCvRephraseRequestException("Gợi ý diễn đạt nhận đoạn mô tả tối đa 800 ký tự.");
    }

    // Ky tu loai R-F4 (dieu khien tru \n, dinh dang) - khong kiem glyph (R-A2)
    public static InvalidCvRephraseRequestException disallowedCharacter() {
        return new InvalidCvRephraseRequestException("Đoạn mô tả có ký tự không hỗ trợ.");
    }

    // kind null / thieu khoa
    public static InvalidCvRephraseRequestException missingKind() {
        return new InvalidCvRephraseRequestException("Loại đoạn mô tả không hợp lệ.");
    }
}
