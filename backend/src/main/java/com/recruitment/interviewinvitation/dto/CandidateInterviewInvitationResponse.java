package com.recruitment.interviewinvitation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H12 I4 (R-A3, muc 4.4) - CHI giay moi hieu luc. KHONG id giay moi, applicationId, sentBy, supersededAt (khong can
// cho phia doc cua ung vien); khung CO id (Q4 - FR-U10 dung de gui lua chon). confirmedAt null = chua chot.
// TUYET DOI khong kem diem so/thu hang - dung ranh gioi CLAUDE.md muc 2/7.
public record CandidateInterviewInvitationResponse(
        String subject, String renderedContent, Instant sentAt, Instant confirmedAt, List<Slot> slots) {

    public CandidateInterviewInvitationResponse {
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public record Slot(UUID id, int displayOrder, Instant scheduledAt, String location, boolean confirmed) {
    }
}
