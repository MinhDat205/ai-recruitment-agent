package com.recruitment.interviewinvitation.dto;

import java.util.List;
import java.util.UUID;

// FR-H12 I1 (R-A2) - moi giay moi cua don: giay moi hieu luc truoc, roi giay moi cu theo superseded_at giam dan.
// maxSlots tra ve de frontend khong tu hardcode gioi han khung (R-S1).
public record InterviewInvitationListResponse(
        UUID applicationId, int maxSlots, List<InterviewInvitationResponse> invitations) {

    public InterviewInvitationListResponse {
        invitations = invitations == null ? List.of() : List.copyOf(invitations);
    }
}
