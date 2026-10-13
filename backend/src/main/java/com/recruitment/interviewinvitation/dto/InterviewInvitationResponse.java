package com.recruitment.interviewinvitation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H12 (muc 4.4) - response cua I2, I3 va phan tu cua I1 (chi phia HR). active = superseded_at IS NULL (R-A1);
// confirmedSlotId/confirmedAt null = chua chot (R-K2); slots theo display_order.
public record InterviewInvitationResponse(
        UUID id,
        UUID applicationId,
        boolean active,
        String subject,
        String renderedContent,
        Instant sentAt,
        UUID sentBy,
        Instant supersededAt,
        UUID confirmedSlotId,
        Instant confirmedAt,
        List<Slot> slots) {

    public InterviewInvitationResponse {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    // location null chi o du lieu cu chuyen tu V1 (R-S6).
    public record Slot(UUID id, int displayOrder, Instant scheduledAt, String location) {
    }
}
