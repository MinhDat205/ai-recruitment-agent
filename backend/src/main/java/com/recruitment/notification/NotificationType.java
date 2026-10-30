package com.recruitment.notification;

public enum NotificationType {
    APPLICATION_STATUS_CHANGED,
    APPLICATION_SUBMITTED,
    APPLICATION_WITHDRAWN,
    SCORING_FINISHED,
    // FR-C06 R-N3 - tin nhan moi trong cuoc trao doi cua mot don. Cot notifications.type la VARCHAR(50) khong
    // CHECK nen them loai khong can migration.
    NEW_MESSAGE,
    // FR-H12 R-N1 - HR gui lai giay moi phong van; nguoi nhan la ung vien, email dung notifications.email_body.
    INTERVIEW_INVITATION_RESENT,
    // FR-U10 R-N2 - ung vien chon khung gio phong van; nguoi nhan la chu cong ty, email dung notifications.email_body.
    INTERVIEW_SLOT_CONFIRMED,
    // FR-C09 R-N2 - ung vien gui yeu cau xem xet lai; nguoi nhan la chu cong ty. Khong chua ly do (R-A3).
    RECONSIDERATION_REQUESTED,
    // FR-C09 R-N3 - HR giu quyet dinh tu choi; nguoi nhan la ung vien. Khong chua phan hoi (Q5).
    RECONSIDERATION_DECLINED
}
