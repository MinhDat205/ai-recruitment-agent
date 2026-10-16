package com.recruitment.common.exception;

// FR-H15 R-K5 - kho khong ton tai hoac da xoa mem - CUNG mot cau, khong noi id (404 TALENT_POOL_NOT_FOUND).
// Kho cua cong ty khac -> 403 (AccessDeniedException), khong dung exception nay.
public class TalentPoolNotFoundException extends RuntimeException {

    public TalentPoolNotFoundException() {
        super("Không tìm thấy kho ứng viên.");
    }
}
