package com.recruitment.jobapplication;

import com.recruitment.jobapplication.dto.ApplicationHistoryEntryResponse;
import com.recruitment.jobapplication.dto.ApplicationHrDetailResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-H09 - trang ho so don phia HR. /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang
// filter chain; quyen so huu (don thuoc Job cua cong ty HR dang dang nhap) kiem o
// HrApplicationAccess: don khong ton tai 404, don cua cong ty khac 403 (R-Q2).
@RestController
@RequestMapping("/api/hr/applications/{applicationId}")
public class ApplicationHrDetailController {

    private final ApplicationHrDetailService applicationHrDetailService;

    public ApplicationHrDetailController(ApplicationHrDetailService applicationHrDetailService) {
        this.applicationHrDetailService = applicationHrDetailService;
    }

    // E1 - dau trang.
    @GetMapping
    public ApplicationHrDetailResponse getDetail(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationHrDetailService.getDetail(ownerId, applicationId);
    }

    // E5 - tab Lich su.
    @GetMapping("/history")
    public List<ApplicationHistoryEntryResponse> getHistory(
            Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationHrDetailService.getHistory(ownerId, applicationId);
    }
}
