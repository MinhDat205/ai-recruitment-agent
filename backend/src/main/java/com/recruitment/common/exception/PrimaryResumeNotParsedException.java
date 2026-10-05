package com.recruitment.common.exception;

// FR-U14 R-A1 - chua co CV chinh da phan tich xong (parse_status = DONE) de dien tu dong. Khac
// ResumeNotParsedException (thong diep/ngu canh rieng cho D2 cham diem, ma loi RESUME_NOT_PARSED) -
// day la ngu canh "Dien tu CV" rieng, ma loi NO_PRIMARY_RESUME_PARSED theo dung muc 4 REQUIREMENT.
public class PrimaryResumeNotParsedException extends RuntimeException {

    public PrimaryResumeNotParsedException() {
        super("Chưa có CV chính đã phân tích xong để điền tự động.");
    }
}
