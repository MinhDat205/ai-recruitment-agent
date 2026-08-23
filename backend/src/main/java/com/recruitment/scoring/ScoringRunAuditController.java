package com.recruitment.scoring;

import com.recruitment.scoring.dto.ScoringRunAuditItemResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Path dat duoi
// /api/hr/candidates/ (namespace F3 da lap tu Dot 3) thay vi /api/hr/applications/{id}/scoring-runs
// (da bi ScoringRunHrController cua D2/FR-H04 chiem, hai muc dich khac nhau - xem
// ScoringRunAuditItemResponse). Doan /audit/ phan biet ro voi /api/hr/candidates/criteria (khong
// de nguoi doc route nham {applicationId} voi literal "criteria"). Quyen so huu kiem rieng o
// ScoringRunAuditService (requireOwnCompany).
@RestController
@RequestMapping("/api/hr/candidates/{applicationId}/audit/scoring-runs")
public class ScoringRunAuditController {

    private final ScoringRunAuditService scoringRunAuditService;

    public ScoringRunAuditController(ScoringRunAuditService scoringRunAuditService) {
        this.scoringRunAuditService = scoringRunAuditService;
    }

    @GetMapping
    public List<ScoringRunAuditItemResponse> list(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return scoringRunAuditService.listAudit(ownerId, applicationId);
    }
}
