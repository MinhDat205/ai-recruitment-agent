package com.recruitment.jobapplication;

import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationHistoryEntryResponse;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.jobapplication.dto.ApplicationSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain, khong
// can @PreAuthorize them.
@RestController
@RequestMapping("/api/candidates/applications")
public class ApplicationCandidateController {

    private final ApplicationService applicationService;

    public ApplicationCandidateController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> apply(
            Authentication authentication, @Valid @RequestBody ApplicationCreateRequest request) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.apply(candidateId, request));
    }

    @GetMapping
    public ResponseEntity<List<ApplicationSummaryResponse>> getMyApplications(Authentication authentication) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(applicationService.getMyApplications(candidateId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApplicationHistoryEntryResponse>> getMyApplicationHistory(
            Authentication authentication, @PathVariable UUID id) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(applicationService.getMyApplicationHistory(candidateId, id));
    }

    @PatchMapping("/{id}/withdraw")
    public ResponseEntity<ApplicationResponse> withdraw(Authentication authentication, @PathVariable UUID id) {
        UUID candidateId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(applicationService.withdraw(candidateId, id));
    }
}
