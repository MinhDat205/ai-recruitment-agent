package com.recruitment.comparison;

import com.recruitment.comparison.dto.ApplicationComparisonResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// FR-H14 C1. /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain; quyen so huu Job kiem o
// ApplicationComparisonService (qua ApplicationOwnerService.loadOwnedJob). Duong dan tinh /compare nam duoi
// /api/hr/jobs/{jobId}/applications nhung o controller rieng - khong trung mapping nao (REQUIREMENT muc 4.3).
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/applications/compare")
public class ApplicationComparisonController {

    private final ApplicationComparisonService applicationComparisonService;

    public ApplicationComparisonController(ApplicationComparisonService applicationComparisonService) {
        this.applicationComparisonService = applicationComparisonService;
    }

    // ids nhan la String (khong List<UUID>): tu parse de tra dung ma/cau R-S8 thay vi 400 mac dinh cua Spring (a31).
    @GetMapping
    public ApplicationComparisonResponse compare(
            Authentication authentication,
            @PathVariable UUID jobId,
            @RequestParam(name = "ids", required = false) String ids) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationComparisonService.compare(ownerId, jobId, ids);
    }
}
