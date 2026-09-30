package com.recruitment.resume;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Job nen tinh bu kinh nghiem (FR-C05 R-E9b), quet theo lo. Tat trong test qua
// app.resume-experience.enabled=false; test goi thang stateService/scheduler tu new.
@Component
@ConditionalOnProperty(name = "app.resume-experience.enabled", havingValue = "true", matchIfMissing = true)
public class ResumeExperienceScheduler {

    private static final Logger log = LoggerFactory.getLogger(ResumeExperienceScheduler.class);

    private final ResumeExperienceStateService stateService;
    private final int batchSize;

    public ResumeExperienceScheduler(
            ResumeExperienceStateService stateService,
            @Value("${app.resume-experience.batch-size:50}") int batchSize) {
        this.stateService = stateService;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - moi ban ghi mot transaction ngan rieng (computeOne), loi mot ban ghi khong
    // chan ca lo.
    @Scheduled(fixedDelayString = "${app.resume-experience.poll-interval-ms:30000}")
    public void computePending() {
        for (UUID id : stateService.findIdsNeedingExperience(batchSize)) {
            try {
                stateService.computeOne(id);
            } catch (RuntimeException e) {
                log.debug("Loi khi tinh so thang kinh nghiem: parsedDataId={}", id, e);
            }
        }
    }
}
