package com.recruitment.ai.jobdraft;

import java.math.BigDecimal;
import java.time.LocalDate;

// FR-H11 R-P1 - schema JSON co dinh cua output AI phan JOB (BeanOutputConverter, constructor 1 tham so). KHONG truong
// trong so, diem, "truong con thieu", nhan danh gia nao: khoa JSON ngoai schema bi bo qua khi parse (Q10). industryCode
// la MA trong danh sach dong cua prompt; locationText la dia danh CHEP NGUYEN VAN tu mo ta (R-P5) - facade kiem va
// anh xa (R-P4), khong phai o day.
public record JobDraftPayload(
        String title,
        String description,
        String requirements,
        String benefits,
        String industryCode,
        String locationText,
        DraftEmploymentType employmentType,
        DraftWorkMode workMode,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        LocalDate deadline) {
}
