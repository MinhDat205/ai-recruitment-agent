package com.recruitment.common.exception;

// FR-C05 R-R2 - 409: CV khong o dieu kien trich xuat lai. Hai ly do, cung ma loi
// RESUME_REPARSE_NOT_ALLOWED, khac thong diep: CV chua phan tich xong (parse_status khac DONE), hoac CV
// da la schema moi nhat.
public class ResumeReparseNotAllowedException extends RuntimeException {

    private ResumeReparseNotAllowedException(String message) {
        super(message);
    }

    public static ResumeReparseNotAllowedException notParsedYet() {
        return new ResumeReparseNotAllowedException(
                "CV chưa phân tích xong, chưa thể cập nhật dữ liệu trích xuất.");
    }

    public static ResumeReparseNotAllowedException alreadyLatest() {
        return new ResumeReparseNotAllowedException("CV này đã có dữ liệu trích xuất mới nhất.");
    }
}
