package com.recruitment.notification;

import java.time.Instant;
import java.util.UUID;

// FR-U10 R-N1 - ung vien chon khung gio (InterviewInvitationService.confirmSlot) - nguoi nhan la chu cong ty cua tin.
// Publish TRONG transaction chon khung, CHI khi cau UPDATE ghi khung chot tra 1 dong; NotificationEventListener xu ly sau
// AFTER_COMMIT. scheduledAt/location la GIA TRI KHUNG VUA CHOT doc lai sau khi ghi - listener KHONG truy van bang giay moi
// (phu thuoc goi mot chieu: goi giay moi phat su kien, goi thong bao khong import nguoc lai - kiem tinh muc 7.1, khuon
// InterviewInvitationResentEvent).
public record InterviewSlotConfirmedEvent(
        UUID applicationId, UUID jobId, UUID candidateId, Instant scheduledAt, String location) {
}
