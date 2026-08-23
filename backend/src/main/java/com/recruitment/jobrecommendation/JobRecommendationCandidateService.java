package com.recruitment.jobrecommendation;

import com.recruitment.job.JobPublicService;
import com.recruitment.job.dto.JobSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

// Doc CACHE co san (job_recommendations), KHONG BAO GIO chay truy van similarity hay goi
// EmbeddingModel trong luc phuc vu request - dung "AI hay lam sai" PHASES.md canh bao ("sinh
// embedding moi lan load trang", Plan Mode F1 muc E/J).
@Service
public class JobRecommendationCandidateService {

    private final JobRecommendationRepository jobRecommendationRepository;
    private final JobPublicService jobPublicService;

    public JobRecommendationCandidateService(
            JobRecommendationRepository jobRecommendationRepository, JobPublicService jobPublicService) {
        this.jobRecommendationRepository = jobRecommendationRepository;
        this.jobPublicService = jobPublicService;
    }

    public List<JobSummaryResponse> getRecommendationsForCandidate(UUID candidateId) {
        List<UUID> jobIds = jobRecommendationRepository
                .findByCandidateIdOrderBySimilarityScoreDescJobIdAsc(candidateId)
                .stream()
                .map(JobRecommendation::getJobId)
                .toList();
        return jobPublicService.getByIds(jobIds);
    }
}
