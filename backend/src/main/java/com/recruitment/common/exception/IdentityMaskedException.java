package com.recruitment.common.exception;

// FR-H16 R-V1, R-T2, R-K2 - thao tac bi chan khi don dang an danh (409 IDENTITY_MASKED). Cau co dinh, khong mang du lieu
// cua don.
public class IdentityMaskedException extends RuntimeException {

    public IdentityMaskedException() {
        super("Hồ sơ đang ở chế độ ẩn danh. Thao tác này mở lại khi gửi giấy mời phỏng vấn hoặc khi tắt chế độ ẩn danh"
                + " của tin.");
    }
}
