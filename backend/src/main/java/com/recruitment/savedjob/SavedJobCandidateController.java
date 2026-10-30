package com.recruitment.savedjob;

import com.recruitment.savedjob.dto.SavedJobResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// FR-U16 E1-E4. /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain (R-S1).
// candidateId lay tu JWT, khong nhan tu path/body; khong endpoint nao nhan id cua dong saved_jobs.
@RestController
@RequestMapping("/api/candidates/saved-jobs")
public class SavedJobCandidateController {

    private final SavedJobService savedJobService;

    public SavedJobCandidateController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    // E1 - 204 cho tao moi, luu lai va da luu san; 409 JOB_NOT_SAVEABLE / SAVED_JOB_LIMIT_REACHED.
    @PutMapping("/{jobId}")
    public ResponseEntity<Void> save(Authentication authentication, @PathVariable UUID jobId) {
        savedJobService.save(UUID.fromString(authentication.getName()), jobId);
        return ResponseEntity.noContent().build();
    }

    // E2 - 204 moi truong hop (R-S5).
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> unsave(Authentication authentication, @PathVariable UUID jobId) {
        savedJobService.unsave(UUID.fromString(authentication.getName()), jobId);
        return ResponseEntity.noContent().build();
    }

    // E3
    @GetMapping
    public ResponseEntity<List<SavedJobResponse>> listMine(Authentication authentication) {
        return ResponseEntity.ok(savedJobService.listMine(UUID.fromString(authentication.getName())));
    }

    // E4
    @GetMapping("/ids")
    public ResponseEntity<List<UUID>> listMyJobIds(Authentication authentication) {
        return ResponseEntity.ok(savedJobService.listMyJobIds(UUID.fromString(authentication.getName())));
    }
}
