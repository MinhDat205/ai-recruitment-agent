package com.recruitment.notification;

public enum NotificationType {
    APPLICATION_STATUS_CHANGED,
    APPLICATION_SUBMITTED,
    APPLICATION_WITHDRAWN,
    SCORING_FINISHED,
    // FR-C06 R-N3 - tin nhan moi trong cuoc trao doi cua mot don. Cot notifications.type la VARCHAR(50) khong
    // CHECK nen them loai khong can migration.
    NEW_MESSAGE
}
