package com.recruitment.resume.dto;

import com.recruitment.resume.ResumeReparseRequestStatus;

// FR-C05 R-R4 - trang thai yeu cau trich xuat lai GAN NHAT cua mot CV. errorMessage la ma loi chuan hoa
// da luu (ResumeParsingErrorCode.formatted()), khong phai loi tho.
public record ResumeReparseStatusResponse(ResumeReparseRequestStatus status, String errorMessage) {
}
