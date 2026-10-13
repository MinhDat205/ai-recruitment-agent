package com.recruitment.jobdraft.dto;

import com.recruitment.jobdraft.FieldNeed;
import com.recruitment.jobdraft.JobDraftField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// FR-H11 phan JOB sau R-P4 (muc 4.4). Ma danh muc, khong nhan (frontend lay nhan tu /api/public/catalogs).
// missingFields do backend tinh (R-M1), khong phai AI.
public record JobDraft(
        String title,
        String description,
        String requirements,
        String benefits,
        String categoryCode,
        String locationCode,
        String employmentType,
        String workMode,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String salaryCurrency,
        LocalDate deadline,
        List<MissingField> missingFields) {

    public record MissingField(JobDraftField field, FieldNeed need) {
    }
}
