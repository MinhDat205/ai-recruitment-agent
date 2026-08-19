package com.recruitment.interviewinvitation;

import com.recruitment.interviewinvitation.dto.InterviewInvitationPreviewResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationResponse;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Quyen so huu (don ung
// tuyen phai thuoc job cua cong ty HR dang dang nhap) duoc kiem rieng o InterviewInvitationService,
// giong pattern ResumeHrController/ScoringRunHrController.
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/interview-invitation")
public class InterviewInvitationController {

    private final InterviewInvitationService interviewInvitationService;

    public InterviewInvitationController(InterviewInvitationService interviewInvitationService) {
        this.interviewInvitationService = interviewInvitationService;
    }

    @GetMapping("/preview")
    public InterviewInvitationPreviewResponse preview(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return interviewInvitationService.previewInvitation(ownerId, applicationId);
    }

    // 201 CREATED: dong interview_invitations duoc tao that trong request nay (dung tien le POST
    // /api/hr/applications/{id}/scoring-runs). Chuyen trang thai don sang INTERVIEW_INVITED la
    // hieu ung phu cua hanh dong gui loi moi, xu ly trong InterviewInvitationService.sendInvitation.
    @PostMapping
    public ResponseEntity<InterviewInvitationResponse> send(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody InterviewInvitationSendRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewInvitationService.sendInvitation(ownerId, applicationId, request));
    }
}
