package com.recruitment.jobapplication;

import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Quyen so huu (job phai
// thuoc cong ty cua HR dang dang nhap) duoc kiem rieng o ApplicationOwnerService, giong pattern
// RubricOwnerController/JobOwnerController.
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/applications")
public class ApplicationOwnerController {

    private final ApplicationOwnerService applicationOwnerService;

    public ApplicationOwnerController(ApplicationOwnerService applicationOwnerService) {
        this.applicationOwnerService = applicationOwnerService;
    }

    // sort: "total_score,desc" (mac dinh, khong truyen cung vay) hoac "applied_at,desc" - xem
    // ApplicationSortOption.
    @GetMapping
    public List<ApplicationHrListItemResponse> list(
            Authentication authentication,
            @PathVariable UUID jobId,
            @RequestParam(name = "sort", required = false) String sort) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationOwnerService.listApplications(ownerId, jobId, ApplicationSortOption.fromParam(sort));
    }
}
