package com.recruitment.reconsideration.dto;

// FR-C09 R-C1 - dung 6 gia tri cua khoi xem xet lai phia ung vien. Ba gia tri dau: chua co yeu cau; ba gia tri sau:
// trang thai cua yeu cau (ReconsiderationStatus).
public enum CandidateReconsiderationState {
    AVAILABLE,
    EXPIRED,
    JOB_REMOVED,
    OPEN,
    ACCEPTED,
    DECLINED
}
