package com.recruitment.reconsideration;

import java.time.Instant;

// FR-C09 - projection cua ReconsiderationRequestRepository.findCandidateRow: du lieu de dung khoi ung vien (R-C1) va de
// tra dung ma loi theo thu tu R-E5 cho C1. Moi so sanh thoi gian da lam trong SQL bang now() (R-T2, R-T4): Java chi doc
// withinDeadline, khong tu so. Trang thai la chuoi, service doi sang enum. Alias phai khop ten getter.
public interface ReconsiderationCandidateRow {

    String getApplicationStatus();

    // from_status cua dong lich su vao REJECTED MOI NHAT (R-E1 c); null khi don chua tung vao REJECTED.
    String getLastRejectedFromStatus();

    // changed_at cua dong PENDING -> REJECTED (duy nhat moi don: PENDING chi vao duoc luc tao don); null khi khong co.
    Instant getRejectedAt();

    // rejectedAt + 168 gio (R-T1); null khi rejectedAt null.
    Instant getDeadline();

    // now() <= deadline (R-T2); null khi deadline null.
    Boolean getWithinDeadline();

    Boolean getJobRemoved();

    // null khi don chua co yeu cau.
    String getRequestStatus();

    String getReason();

    Instant getRequestedAt();

    Instant getDecidedAt();

    String getHrResponse();
}
