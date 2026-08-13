package com.recruitment.interviewtemplate;

import com.recruitment.interviewtemplate.dto.InterviewTemplateRequest;
import com.recruitment.interviewtemplate.dto.InterviewTemplateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/hr/** da bi SecurityConfig chan hasRole("HR") o tang filter chain. Quyen so huu (job phai
// thuoc cong ty cua HR dang dang nhap) duoc kiem tra rieng o InterviewTemplateOwnerService.
@RestController
@RequestMapping("/api/hr/jobs/{jobId}/interview-template")
public class InterviewTemplateOwnerController {

    private final InterviewTemplateOwnerService interviewTemplateOwnerService;

    public InterviewTemplateOwnerController(InterviewTemplateOwnerService interviewTemplateOwnerService) {
        this.interviewTemplateOwnerService = interviewTemplateOwnerService;
    }

    @GetMapping
    public InterviewTemplateResponse get(Authentication authentication, @PathVariable UUID jobId) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return interviewTemplateOwnerService.getMine(ownerId, jobId);
    }

    @PutMapping
    public InterviewTemplateResponse update(
            Authentication authentication, @PathVariable UUID jobId, @Valid @RequestBody InterviewTemplateRequest request) {
        UUID ownerId = UUID.fromString(authentication.getName());
        return interviewTemplateOwnerService.update(ownerId, jobId, request);
    }
}
