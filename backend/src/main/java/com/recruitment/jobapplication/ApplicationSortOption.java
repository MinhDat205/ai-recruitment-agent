package com.recruitment.jobapplication;

// Tham so sort cho GET /api/hr/jobs/{jobId}/applications (Dot 4, FR-H05). TOTAL_SCORE_DESC la mac
// dinh (khong truyen tham so, hoac truyen gia tri khong nhan dien duoc) - thu tu hien thi TRUNG
// voi thu tu xep hang (rank). APPLIED_AT_DESC giu lai hanh vi cu cua D2 cho HR muon xem theo ngay
// nop thay vi theo diem - thu tu hien thi KHONG doi theo rank trong che do nay, nhung totalScore/
// rank van duoc tinh va tra ve day du cho moi dong.
//
// Gia tri khong nhan dien duoc roi ve mac dinh TOTAL_SCORE_DESC thay vi nem 400 - day la tham so
// hien thi (khac voi ai_consent hay status chuyen doi, nhung dieu kien nghiep vu can chan cung).
public enum ApplicationSortOption {
    TOTAL_SCORE_DESC,
    APPLIED_AT_DESC;

    private static final String APPLIED_AT_DESC_PARAM = "applied_at,desc";

    public static ApplicationSortOption fromParam(String raw) {
        if (APPLIED_AT_DESC_PARAM.equalsIgnoreCase(raw)) {
            return APPLIED_AT_DESC;
        }
        return TOTAL_SCORE_DESC;
    }
}
