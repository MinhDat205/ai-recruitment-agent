package com.recruitment.jobapplication;

import com.recruitment.common.exception.InvalidApplicationStatusTransitionException;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.jobapplication.dto.ApplicationStatusUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Quyen so huu (don ung
// tuyen phai thuoc job cua cong ty HR dang dang nhap) duoc kiem rieng o ApplicationStatusService,
// giong pattern ResumeHrController/ScoringRunHrController.
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/status")
public class ApplicationStatusController {

    private final ApplicationStatusService applicationStatusService;

    public ApplicationStatusController(ApplicationStatusService applicationStatusService) {
        this.applicationStatusService = applicationStatusService;
    }

    @PatchMapping
    public ApplicationResponse changeStatus(
            Authentication authentication,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        // INTERVIEW_INVITED (FR-H07 Dot 2, SRS: "Da moi phong van (co lich hen)") bat buoc phai
        // kem lich hen - khong duoc dat rieng qua PATCH nay, chan tai day de khong tao duoc don o
        // trang thai INTERVIEW_INVITED ma khong co dong interview_invitations nao di kem. Duong
        // dung: POST /api/hr/applications/{applicationId}/interview-invitation
        // (InterviewInvitationService.sendInvitation goi lai chinh applicationStatusService.changeStatus
        // ben duoi, khong bi chan boi guard nay vi guard chi nam o controller).
        if (request.status() == ApplicationStatus.INTERVIEW_INVITED) {
            throw new InvalidApplicationStatusTransitionException(
                    "Vui lòng dùng endpoint gửi lời mời phỏng vấn (POST /api/hr/applications/{id}/interview-invitation) "
                            + "để chuyển đơn sang INTERVIEW_INVITED.");
        }
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationStatusService.changeStatus(ownerId, applicationId, request.status());
    }
}
