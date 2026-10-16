package com.recruitment.common.exception;

// FR-H15 R-P2 buoc 1-2 - danh sach kho cua P7 thieu, co phan tu null/khong phai UUID, hoac co id trung
// (400 INVALID_TALENT_POOL_SELECTION).
public class InvalidTalentPoolSelectionException extends RuntimeException {

    public InvalidTalentPoolSelectionException() {
        super("Danh sách kho không hợp lệ. Mỗi kho chỉ được chọn một lần.");
    }
}
