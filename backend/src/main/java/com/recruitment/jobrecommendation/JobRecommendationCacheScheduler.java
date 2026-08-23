package com.recruitment.jobrecommendation;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.job-recommendation.enabled=false (application-test.yml, da them tu Dot 3).
// KHONG co loi goi AI nao trong refreshOne, nhung van tat trong test theo dung quy uoc chung cua
// moi scheduler trong du an (tranh scheduler tu tick gay nhieu cac @SpringBootTest khac dung chung
// context cache). Test goi thang cacheService.refreshOne(id), khong cho @Scheduled tick.
@Component
@ConditionalOnProperty(name = "app.job-recommendation.enabled", havingValue = "true", matchIfMissing = true)
public class JobRecommendationCacheScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobRecommendationCacheScheduler.class);

    private final JobRecommendationRepository jobRecommendationRepository;
    private final JobRecommendationCacheService cacheService;
    private final int batchSize;

    public JobRecommendationCacheScheduler(
            JobRecommendationRepository jobRecommendationRepository,
            JobRecommendationCacheService cacheService,
            @Value("${app.job-recommendation.batch-size:10}") int batchSize) {
        this.jobRecommendationRepository = jobRecommendationRepository;
        this.cacheService = cacheService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.job-recommendation.poll-interval-ms:5000}")
    public void pollCandidatesNeedingRefresh() {
        List<UUID> candidateIds =
                jobRecommendationRepository.findCandidateIdsNeedingRefresh(PageRequest.of(0, batchSize));
        for (UUID candidateId : candidateIds) {
            try {
                cacheService.refreshOne(candidateId);
            } catch (RuntimeException e) {
                log.debug(
                        "Loi khong bat duoc trong vong quet lam moi goi y viec lam: candidateId={}", candidateId, e);
            }
        }
    }
}
