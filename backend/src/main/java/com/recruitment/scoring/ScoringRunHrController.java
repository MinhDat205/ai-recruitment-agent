package com.recruitment.scoring;

import com.recruitment.scoring.dto.ScoringRunResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain (da doc lai xac nhan,
// khong doan). Quyen so huu (don ung tuyen phai thuoc job cua cong ty HR dang dang nhap) duoc kiem
// rieng o ScoringRunService, giong pattern RubricOwnerController/JobOwnerController.
//
// 201 CREATED, khong phai 202: lot cham (scoring_runs row voi id, status=PENDING) duoc tao THAT va
// NGAY LAP TUC trong request nay - dung tra ve duoc trong response. Viec LLM cham tung tieu chi
// dien ra sau o tang nen (Dot 4) khong lam thay doi that "mot tai nguyen da duoc tao xong" - dung
// tien le da co: POST /api/candidates/resumes cung tra 201 du parse that dien ra nen sau do.
@RestController
@RequestMapping("/api/hr/applications/{applicationId}/scoring-runs")
public class ScoringRunHrController {

    private final ScoringRunService scoringRunService;

    public ScoringRunHrController(ScoringRunService scoringRunService) {
        this.scoringRunService = scoringRunService;
    }

    @PostMapping
    public ResponseEntity<ScoringRunResponse> create(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scoringRunService.createScoringRun(ownerId, applicationId));
    }

    // Lich su cac luot cham cua MOT don, moi nhat truoc - dung de FE poll tien do (Dot 5).
    @GetMapping
    public List<ScoringRunResponse> list(Authentication authentication, @PathVariable UUID applicationId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return scoringRunService.listScoringRuns(ownerId, applicationId);
    }
}
