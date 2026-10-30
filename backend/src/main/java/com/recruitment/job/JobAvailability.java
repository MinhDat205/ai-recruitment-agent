package com.recruitment.job;

// FR-U08 R-D4 - tinh trang tin nhin tu phia ung vien, dung 5 gia tri. Tin da xoa mem va tin dua ve nhap GOP chung
// UNAVAILABLE: ung vien khong can biet HR da xoa tin hay dua ve nhap. FR-U16 R-A1 - chuyen tu enum long trong
// ApplicationCandidateDetailResponse ra day de trang "Tin da luu" dung chung; ten va thu tu giu nguyen (JSON khong doi).
public enum JobAvailability {
    OPEN,
    EXPIRED,
    PAUSED,
    CLOSED,
    UNAVAILABLE
}
