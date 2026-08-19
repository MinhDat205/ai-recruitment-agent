package com.recruitment.jobapplication;

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
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationStatusService.changeStatus(ownerId, applicationId, request.status());
    }
}
