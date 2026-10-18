package com.recruitment.common.exception;

// FR-H16 B1 - body thieu, enabled null, sai kieu hoac JSON hong (400 INVALID_BLIND_REVIEW_REQUEST).
public class InvalidBlindReviewRequestException extends RuntimeException {

    public InvalidBlindReviewRequestException() {
        super("Yêu cầu bật/tắt chế độ ẩn danh không hợp lệ.");
    }
}
