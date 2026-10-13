package com.recruitment.notification;

public enum NotificationType {
    APPLICATION_STATUS_CHANGED,
    APPLICATION_SUBMITTED,
    APPLICATION_WITHDRAWN,
    SCORING_FINISHED,
    // FR-C06 R-N3 - tin nhan moi trong cuoc trao doi cua mot don. Cot notifications.type la VARCHAR(50) khong
    // CHECK nen them loai khong can migration.
    NEW_MESSAGE,
    // FR-H12 R-N1 - HR gui lai giay moi phong van. Dot 2 chi them hang enum de doc duoc dong seed ec..17; logic dung
    // thong bao o dot 3.
    INTERVIEW_INVITATION_RESENT
}
