package com.recruitment.rubric;

import com.recruitment.rubric.dto.RubricCriterionRequest;
import com.recruitment.rubric.dto.RubricCriterionResponse;
import com.recruitment.rubric.dto.RubricResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Quyen so huu (job phai
// thuoc cong ty cua HR dang dang nhap) duoc kiem tra rieng o RubricOwnerService, giong JobOwnerController.
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/rubric")
public class RubricOwnerController {

    private final RubricOwnerService rubricOwnerService;

    public RubricOwnerController(RubricOwnerService rubricOwnerService) {
        this.rubricOwnerService = rubricOwnerService;
    }

    @GetMapping
    public RubricResponse get(Authentication authentication, @PathVariable UUID jobId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return rubricOwnerService.getRubric(ownerId, jobId);
    }

    @PostMapping("/criteria")
    public ResponseEntity<RubricCriterionResponse> addCriterion(
            Authentication authentication, @PathVariable UUID jobId, @Valid @RequestBody RubricCriterionRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(rubricOwnerService.addCriterion(ownerId, jobId, request));
    }

    @PutMapping("/criteria/{criterionId}")
    public RubricCriterionResponse updateCriterion(
            Authentication authentication,
            @PathVariable UUID jobId,
            @PathVariable UUID criterionId,
            @Valid @RequestBody RubricCriterionRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return rubricOwnerService.updateCriterion(ownerId, jobId, criterionId, request);
    }

    @DeleteMapping("/criteria/{criterionId}")
    public ResponseEntity<Void> deleteCriterion(
            Authentication authentication, @PathVariable UUID jobId, @PathVariable UUID criterionId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        rubricOwnerService.deleteCriterion(ownerId, jobId, criterionId);
        return ResponseEntity.noContent().build();
    }
}
