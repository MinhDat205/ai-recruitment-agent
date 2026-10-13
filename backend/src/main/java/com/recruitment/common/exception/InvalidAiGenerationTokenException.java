package com.recruitment.common.exception;

// K4 (FR-H11 R-K4-4) - token sieu du lieu AI sai chu ky, het han, khong cua nguoi goi hoac sai muc dich. Cau co dinh, KHONG
// chua ly do cu the (khong giup ai do thu doan token) va khong chua noi dung token.
public class InvalidAiGenerationTokenException extends RuntimeException {

    public InvalidAiGenerationTokenException() {
        super("Thông tin phiên tạo bằng AI không hợp lệ hoặc đã hết hạn. Vui lòng tạo lại gợi ý.");
    }
}
