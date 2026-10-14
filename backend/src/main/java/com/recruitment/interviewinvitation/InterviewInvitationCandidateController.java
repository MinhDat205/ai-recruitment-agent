package com.recruitment.interviewinvitation;

import com.recruitment.interviewinvitation.dto.CandidateInterviewInvitationResponse;
import com.recruitment.interviewinvitation.dto.ConfirmInterviewSlotRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain, khong can
// @PreAuthorize them - cung mau ApplicationCandidateController. Quyen so huu (don phai thuoc ve
// candidate dang dang nhap) kiem rieng o InterviewInvitationService.getActiveInvitationForCandidate.
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
        return interviewInvitationService.getActiveInvitationForCandidate(candidateId, applicationId);
    }

    // FR-U10 C1 - ung vien chon MOT khung cua giay moi hieu luc; 200 kem DTO I4 (Q1, R-W7). slotId kiem bang Bean
    // Validation (400 map truong, O3); quyen so huu, trang thai don, giay moi kiem trong InterviewInvitationService.confirmSlot.
    @PostMapping("/confirm-slot")
    public CandidateInterviewInvitationResponse confirmSlot(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ConfirmInterviewSlotRequest request) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return interviewInvitationService.confirmSlot(candidateId, applicationId, request.slotId());
    }
}
