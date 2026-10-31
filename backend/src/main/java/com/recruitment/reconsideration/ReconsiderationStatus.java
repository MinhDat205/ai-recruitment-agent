package com.recruitment.reconsideration;

// FR-C09 - trang thai cua YEU CAU xem xet lai (bang rieng, V21), KHONG phai trang thai don: don van dung 5 gia tri
// cua ApplicationStatus. OPEN -> ACCEPTED (HR moi phong van) | DECLINED (HR giu quyet dinh tu choi); quyet roi la cuoi
// (R-D4).
public enum ReconsiderationStatus {
    OPEN,
    ACCEPTED,
    DECLINED
}
