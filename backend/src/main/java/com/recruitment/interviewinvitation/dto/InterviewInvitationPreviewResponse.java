package com.recruitment.interviewinvitation.dto;

// FR-H12 (muc 4.4) - them maxSlots (R-S1); cac thanh phan cu giu nguyen.
public record InterviewInvitationPreviewResponse(
        String subject,
        String content,
        String candidateName,
        boolean companyNameMismatch,
        String templateCompanyName,
        String currentCompanyName,
        int maxSlots) {
}
