package com.recruitment.job;

import com.recruitment.common.dto.PageResponse;
import com.recruitment.job.dto.JobCreateRequest;
import com.recruitment.job.dto.JobOwnerResponse;
import com.recruitment.job.dto.JobRequest;
import com.recruitment.job.dto.JobStatusUpdateRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Quyen so huu tung Job
// (companyId phai khop cong ty cua HR dang dang nhap) duoc kiem tra rieng o JobOwnerService.
@RestController
@RequestMapping("/api/hr/jobs")
public class JobOwnerController {

    private final JobOwnerService jobOwnerService;

    public JobOwnerController(JobOwnerService jobOwnerService) {
        this.jobOwnerService = jobOwnerService;
    }

    @PostMapping
    public ResponseEntity<JobOwnerResponse> create(
            Authentication authentication, @Valid @RequestBody JobCreateRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(jobOwnerService.create(ownerId, request));
    }

    @GetMapping
    public PageResponse<JobOwnerResponse> list(
            Authentication authentication,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return jobOwnerService.listMine(ownerId, status, page, size);
    }

    @GetMapping("/{id}")
    public JobOwnerResponse get(Authentication authentication, @PathVariable UUID id) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return jobOwnerService.getMine(ownerId, id);
    }

    @PutMapping("/{id}")
    public JobOwnerResponse update(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody JobRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return jobOwnerService.update(ownerId, id, request);
    }

    @PatchMapping("/{id}/status")
    public JobOwnerResponse changeStatus(
            Authentication authentication, @PathVariable UUID id, @Valid @RequestBody JobStatusUpdateRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return jobOwnerService.changeStatus(ownerId, id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        UUID ownerId = UUID.fromString(authentication.getName());
        jobOwnerService.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }
}
