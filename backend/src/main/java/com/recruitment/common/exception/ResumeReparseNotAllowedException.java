package com.recruitment.common.exception;

// FR-C05 R-R2 - 409: CV khong o dieu kien trich xuat lai (chua DONE, hoac da la schema moi nhat).
public class ResumeReparseNotAllowedException extends RuntimeException {

    public ResumeReparseNotAllowedException() {
        super("CV này đã có dữ liệu trích xuất mới nhất.");
    }
}
