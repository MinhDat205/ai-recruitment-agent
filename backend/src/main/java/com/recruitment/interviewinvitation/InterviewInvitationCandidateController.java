package com.recruitment.interviewinvitation;

import com.recruitment.interviewinvitation.dto.CandidateInterviewInvitationResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain, khong can
// @PreAuthorize them - cung mau ApplicationCandidateController. Quyen so huu (don phai thuoc ve
// candidate dang dang nhap) kiem rieng o InterviewInvitationService.getLatestInvitationForCandidate.
@RestController
@RequestMapping("/api/candidates/applications/{applicationId}/interview-invitation")
public class InterviewInvitationCandidateController {

    private final InterviewInvitationService interviewInvitationService;

    public InterviewInvitationCandidateController(InterviewInvitationService interviewInvitationService) {
        this.interviewInvitationService = interviewInvitationService;
    }

    @GetMapping
    public CandidateInterviewInvitationResponse get(Authentication authentication, @PathVariable UUID applicationId) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return interviewInvitationService.getLatestInvitationForCandidate(candidateId, applicationId);
    }
}
