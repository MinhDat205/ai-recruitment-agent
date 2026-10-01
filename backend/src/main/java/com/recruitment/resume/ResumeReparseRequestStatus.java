package com.recruitment.resume;

// Trang thai yeu cau trich xuat lai (FR-C05 R-R4) - dung 4 gia tri cua CHECK o resume_reparse_requests
// (V8). resumes.parse_status KHONG doi trong suot qua trinh (giu DONE, R-R5).
public enum ResumeReparseRequestStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}
