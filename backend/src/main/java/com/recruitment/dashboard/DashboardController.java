package com.recruitment.dashboard;

import com.recruitment.dashboard.dto.DashboardStatsResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Khong co tham so jobId -
// pham vi la TOAN CONG TY cua HR dang dang nhap, quyen so huu kiem rieng o DashboardService
// (requireOwnCompany), cung mau JobOwnerController/ApplicationOwnerController.
@RestController
@RequestMapping("/api/hr/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public DashboardStatsResponse stats(Authentication authentication) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return dashboardService.getStats(ownerId);
    }
}
