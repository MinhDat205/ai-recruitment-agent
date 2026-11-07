package com.recruitment.scoring;

import com.recruitment.scoring.dto.BulkScoringRunResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-H04 mo rong B1 (REQUIREMENT muc 4.2). /api/hr/** da hasRole("HR") o SecurityConfig; quyen so huu tin kiem o
// ScoringRunService (R-P2). 200 chu khong 201 (Q11): mot request tao 0..50 luot, tra ban tom tat chu khong tra tai
// nguyen vua tao.
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/scoring-runs")
public class JobScoringRunBulkHrController {

    private final ScoringRunService scoringRunService;

    public JobScoringRunBulkHrController(ScoringRunService scoringRunService) {
        this.scoringRunService = scoringRunService;
    }

    @PostMapping("/bulk")
    public BulkScoringRunResponse createForJob(Authentication authentication, @PathVariable UUID jobId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return scoringRunService.createScoringRunsForJob(ownerId, jobId);
    }
}
