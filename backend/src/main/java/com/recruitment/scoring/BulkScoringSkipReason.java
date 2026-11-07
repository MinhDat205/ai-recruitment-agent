package com.recruitment.scoring;

// FR-H04 mo rong R-E3 - ly do mot don trong tap xet (R-E1) khong duoc tao luot. Thu tu khai bao = thu tu uu tien khi
// mot don trung nhieu ly do (Q2). Chi la ma trong response, khong ghi xuong cot loi nao nen khong implement
// FormattedErrorCode; cau hien thi do frontend dung (UI.md muc 7).
public enum BulkScoringSkipReason {
    NOT_PENDING,
    RESUME_NOT_PARSED,
    RUN_IN_PROGRESS,
    ALREADY_SCORED
}
