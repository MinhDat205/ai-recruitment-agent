package com.recruitment.notification;

import java.util.UUID;

// FR-C06 R-R3 (muc 12 L2) - M3 publish de danh dau da doc moi thong bao NEW_MESSAGE chua doc cua nguoi goi
// (userId) cho don nay. Xu ly DONG BO trong CUNG transaction cua M3 - messaging/ chi import notification/ de
// publish su kien, khong goi thang NotificationRepository.
public record ConversationReadEvent(UUID userId, UUID applicationId) {}
