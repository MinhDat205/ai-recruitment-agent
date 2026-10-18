package com.recruitment.common.exception;

// FR-U13 R-I1, R-I3 - yeu cau tim viec khong hop le (400 INVALID_JOB_SEARCH_REQUEST). Khuon
// InvalidCvRephraseRequestException: mot class, moi loai loi mot factory, cau tieng Viet co dinh (muc 4.4) - KHONG nhan
// String tu do, khong chua gia tri nguoi dung da go.
public class InvalidJobSearchRequestException extends RuntimeException {

    private InvalidJobSearchRequestException(String message) {
        super(message);
    }

    // R-I1 - body khong doc duoc (JSON hong, sai kieu) - JobSearchExceptionAdvice dung cau nay.
    public static InvalidJobSearchRequestException unreadableBody() {
        return new InvalidJobSearchRequestException("Yêu cầu tìm việc không hợp lệ.");
    }

    // query null hoac q rong sau R-I2
    public static InvalidJobSearchRequestException emptyQuery() {
        return new InvalidJobSearchRequestException("Vui lòng nhập mô tả công việc bạn muốn tìm.");
    }

    // q.length() < 2
    public static InvalidJobSearchRequestException queryTooShort() {
        return new InvalidJobSearchRequestException("Mô tả cần ít nhất 2 ký tự.");
    }

    // q.length() > 300
    public static InvalidJobSearchRequestException queryTooLong() {
        return new InvalidJobSearchRequestException("Mô tả tìm việc tối đa 300 ký tự.");
    }

    // Ky tu Character.getType = CONTROL hoac FORMAT (U+200B, U+FEFF...)
    public static InvalidJobSearchRequestException disallowedCharacter() {
        return new InvalidJobSearchRequestException("Mô tả có ký tự không hỗ trợ.");
    }
}
