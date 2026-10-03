package com.recruitment.jobrecommendation;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/candidates/** da bi SecurityConfig chan hasRole("CANDIDATE") o tang filter chain, khong can
// @PreAuthorize them - dung quy uoc cua ResumeCandidateController/ApplicationCandidateController.
// FR-U15 - route va phan quyen GIU NGUYEN, chi noi dung response doi (R-G3): tu List<JobSummaryResponse>
// (doc bo dem cu) sang JobRecommendationResponse (status/source/items, tinh truc tiep).
@RestController
@RequestMapping("/api/candidates/job-recommendations")
public class JobRecommendationCandidateController {

    private final JobRecommendationCandidateService jobRecommendationCandidateService;

    public JobRecommendationCandidateController(
            JobRecommendationCandidateService jobRecommendationCandidateService) {
        this.jobRecommendationCandidateService = jobRecommendationCandidateService;
    }

    @GetMapping
    public JobRecommendationResponse getRecommendations(Authentication authentication) {
        return jobRecommendationCandidateService.getRecommendationsForCandidate(
                UUID.fromString(authentication.getName()));
    }
}
