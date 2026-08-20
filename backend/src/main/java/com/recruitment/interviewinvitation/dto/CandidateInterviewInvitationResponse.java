package com.recruitment.interviewinvitation.dto;

import java.time.Instant;

// Chi 5 field ung vien can de xem lai lich hen - KHONG co id/applicationId/sentBy (khong can thiet
// cho phia doc cua ung vien). TUYET DOI khong kem diem so/thu hang - dung ranh gioi CLAUDE.md muc 2/7.
public record CandidateInterviewInvitationResponse(
        Instant scheduledAt, String location, String subject, String renderedContent, Instant sentAt) {
}
