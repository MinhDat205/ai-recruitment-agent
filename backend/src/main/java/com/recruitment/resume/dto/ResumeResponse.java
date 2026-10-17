package com.recruitment.resume.dto;

import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.ResumeFileType;
import java.time.Instant;
import java.util.UUID;

// KHONG co fileUrl: do la key noi bo (vd "resumes/<uuid>.pdf"), khong dung truc tiep duoc o
// frontend - tai file luon phai di qua endpoint download co kiem quyen so huu.
// FR-C05: schemaVersion (1|2, null khi chua co du lieu trich xuat) va reparse (yeu cau trich xuat lai
// gan nhat, null khi chua tung yeu cau).
// FR-U12 R-S2: fromCvBuilder (thanh phan CUOI) = DONE va prompt_version la cv-builder-v1.
public record ResumeResponse(
        UUID id,
        String fileName,
        ResumeFileType fileType,
        Long fileSize,
        String versionLabel,
        boolean isPrimary,
        ParseStatus parseStatus,
        String parseError,
        Instant uploadedAt,
        Integer schemaVersion,
        ResumeReparseStatusResponse reparse,
        boolean fromCvBuilder) {
}
