package com.recruitment.messagedraft;

// FR-C07 muc 4.4 - ly do mot tinh huong chua dung duoc (A1). Cau hien thi o frontend (UI.md muc 7), khong o day.
public enum DraftUnavailableReason {
    // RESULT_NOTICE khi don chua HIRED/REJECTED.
    RESULT_NOT_FINAL,
    // INTERVIEW_REMINDER, REQUEST_RESCHEDULE khi don khong INTERVIEW_INVITED hoac chua co giay moi.
    NO_ACTIVE_INTERVIEW,
    // THANK_AFTER_INTERVIEW khi chua co giay moi.
    NO_INTERVIEW
}
