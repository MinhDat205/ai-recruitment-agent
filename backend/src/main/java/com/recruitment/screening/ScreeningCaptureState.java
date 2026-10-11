package com.recruitment.screening;

// FR-H10 R-A3 - trang thai ban chup cua mot don, do backend tinh (frontend KHONG tu suy tu items.length).
public enum ScreeningCaptureState {
    // job_applications.screening_captured_at IS NULL - don nop truoc khi co buoc chup (R-S4).
    NOT_CAPTURED,
    // Da chup, 0 dong ban chup - Job khong co cau hoi luc ung vien nop.
    NO_QUESTIONS,
    // Da chup, >= 1 dong ban chup.
    CAPTURED
}
