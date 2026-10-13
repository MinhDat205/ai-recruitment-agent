package com.recruitment.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H12 R-N1 - HR gui lai giay moi (InterviewInvitationService.resendInvitation) - nguoi nhan la ung vien (candidateId).
// Publish TRONG transaction gui lai, NotificationEventListener xu ly sau AFTER_COMMIT. slots mang GIA TRI KHUNG VUA LUU
// (da cat phut, da strip) theo display_order - listener dung email tu day, KHONG truy van bang giay moi (phu thuoc goi
// mot chieu: goi giay moi phat su kien, goi thong bao khong import nguoc lai - kiem tinh muc 7.1).
public record InterviewInvitationResentEvent(UUID applicationId, UUID jobId, UUID candidateId, List<SlotInfo> slots) {

    public InterviewInvitationResentEvent {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    // location null chi co o du lieu cu (R-S6) - giu cho builder khong nem loi.
    public record SlotInfo(Instant scheduledAt, String location) {
    }
}
