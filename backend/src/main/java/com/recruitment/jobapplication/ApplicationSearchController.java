package com.recruitment.jobapplication;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.jobapplication.dto.ApplicationSearchItemResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Khong co jobId bat buoc
// (khac ApplicationOwnerController cua D3, /api/hr/jobs/{jobId}/applications) - pham vi la TOAN
// CONG TY cua HR dang dang nhap, quyen so huu kiem rieng o ApplicationSearchService
// (requireOwnCompany), cung mau DashboardController.
@RestController
@RequestMapping("/api/hr/candidates")
public class ApplicationSearchController {

    private final ApplicationSearchService applicationSearchService;

    public ApplicationSearchController(ApplicationSearchService applicationSearchService) {
        this.applicationSearchService = applicationSearchService;
    }

    @GetMapping
    public PageResponse<ApplicationSearchItemResponse> search(
            Authentication authentication,
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) BigDecimal minTotalScore,
            @RequestParam(required = false) BigDecimal maxTotalScore,
            @RequestParam(required = false) String criterionName,
            @RequestParam(required = false) BigDecimal minCriterionScore,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationSearchService.search(
                ownerId, jobId, status, minTotalScore, maxTotalScore, criterionName, minCriterionScore, page, size);
    }

    // Dropdown chon tieu chi o frontend (F3) - danh sach ten tieu chi PHAN BIET trong pham vi cong
    // ty cua HR dang dang nhap.
    @GetMapping("/criteria")
    public List<String> criteria(Authentication authentication) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return applicationSearchService.listCriterionNames(ownerId);
    }
}
