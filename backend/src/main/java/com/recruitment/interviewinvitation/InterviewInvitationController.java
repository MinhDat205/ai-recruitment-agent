package com.recruitment.interviewinvitation;

import com.recruitment.interviewinvitation.dto.InterviewInvitationListResponse;
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
//
// FR-H12 - goc mapping la /api/hr/applications/{applicationId} vi I1 dung duong dan SO NHIEU
// (/interview-invitations, muc 4.3); ba duong dan cu giu nguyen. KHONG co PUT/PATCH/DELETE cho giay moi hay khung (R-C2).
@RestController
@RequestMapping("/api/hr/applications/{applicationId}")
public class InterviewInvitationController {

    private final InterviewInvitationService interviewInvitationService;

    public InterviewInvitationController(InterviewInvitationService interviewInvitationService) {
        this.interviewInvitationService = interviewInvitationService;
    }

    @GetMapping("/interview-invitation/preview")
    public InterviewInvitationPreviewResponse preview(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return interviewInvitationService.previewInvitation(ownerId, applicationId);
    }

    // 201 CREATED: dong interview_invitations duoc tao that trong request nay (dung tien le POST
    // /api/hr/applications/{id}/scoring-runs). Chuyen trang thai don sang INTERVIEW_INVITED la
    // hieu ung phu cua hanh dong gui loi moi, xu ly trong InterviewInvitationService.sendInvitation.
    @PostMapping("/interview-invitation")
    public ResponseEntity<InterviewInvitationResponse> send(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody InterviewInvitationSendRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewInvitationService.sendInvitation(ownerId, applicationId, request));
    }

    // FR-H12 I3 (R-G) - gui lai giay moi; 201 vi tao giay moi moi. Trang thai don KHONG doi.
    @PostMapping("/interview-invitation/resend")
    public ResponseEntity<InterviewInvitationResponse> resend(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody InterviewInvitationSendRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interviewInvitationService.resendInvitation(ownerId, applicationId, request));
    }

    // FR-H12 I1 (R-A2) - moi giay moi cua don (hieu luc + cu).
    @GetMapping("/interview-invitations")
    public InterviewInvitationListResponse list(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return interviewInvitationService.listInvitations(ownerId, applicationId);
    }
}
