package com.recruitment.jobrecommendation;

import com.recruitment.job.dto.JobSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain, khong can
// @PreAuthorize them - dung quy uoc cua ResumeCandidateController/ApplicationCandidateController.
// Khong co URL nao trung voi bang route da khao sat o dau F1 (/api/candidates/job-recommendations).
@RestController
@RequestMapping("/api/candidates/job-recommendations")
public class JobRecommendationCandidateController {

    private final JobRecommendationCandidateService jobRecommendationCandidateService;

    public JobRecommendationCandidateController(
            JobRecommendationCandidateService jobRecommendationCandidateService) {
        this.jobRecommendationCandidateService = jobRecommendationCandidateService;
    }

    @GetMapping
    public List<JobSummaryResponse> getRecommendations(Authentication authentication) {
        return jobRecommendationCandidateService.getRecommendationsForCandidate(
                UUID.fromString(authentication.getName()));
    }
}
