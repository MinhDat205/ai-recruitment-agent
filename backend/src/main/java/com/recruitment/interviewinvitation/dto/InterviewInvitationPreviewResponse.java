package com.recruitment.interviewinvitation.dto;

public record InterviewInvitationPreviewResponse(
        String subject,
        String content,
        String candidateName,
        boolean companyNameMismatch,
        String templateCompanyName,
        String currentCompanyName) {
}
