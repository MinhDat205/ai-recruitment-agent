package com.recruitment.common.exception;

// FR-C08 R-Q3 - CV chua co van ban trich xuat (khong co hang resume_parsed_data: CV PENDING/PROCESSING/FAILED) -> 409
// CV_TEXT_NOT_AVAILABLE, khong goi AI. Cau co dinh (muc 4.4), KHONG nhan chuoi tu do va khong noi id CV.
public class CvTextNotAvailableException extends RuntimeException {

    public CvTextNotAvailableException() {
        super("CV chưa được trích xuất xong nên chưa hỏi đáp được.");
    }
}
