package com.recruitment.resume;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Tinh bu so thang kinh nghiem cho ban ghi chua tinh (FR-C05 R-E9b - toan bo CV v1 co san). Khong goi
// LLM, khong goi embedding: chi doc data.experience va ghi bon cot kinh nghiem. Bean rieng (khong
// self-invocation tu scheduler) de @Transactional di qua proxy.
@Service
public class ResumeExperienceStateService {

    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final Clock clock;

    public ResumeExperienceStateService(ResumeParsedDataRepository resumeParsedDataRepository, Clock clock) {
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UUID> findIdsNeedingExperience(int batchSize) {
        return resumeParsedDataRepository.findIdsNeedingExperience(PageRequest.of(0, batchSize));
    }

    // Transaction ngan cho MOT ban ghi. Ghi bang UPDATE co dieu kien "experience_computed_at IS NULL"
    // (writeExperienceIfPending) - khong save entity: neu trich xuat lai vua ghi du lieu moi kem kinh
    // nghiem moi, cau nay tra 0 thay vi de len bang ket qua tinh tu data cu. Tra true khi da ghi.
    @Transactional
    public boolean computeOne(UUID parsedDataId) {
        ResumeParsedData data = resumeParsedDataRepository.findById(parsedDataId).orElse(null);
        if (data == null || data.getExperienceComputedAt() != null) {
            return false;
        }
        Instant computedAt = clock.instant();
        ExperienceCalculator.Result result = ExperienceCalculator.compute(
                data.getData().experience(), ExperienceCalculator.referenceMonth(computedAt));
        return resumeParsedDataRepository.writeExperienceIfPending(
                        parsedDataId, result.months(), result.countedEntries(), result.skippedEntries(), computedAt)
                == 1;
    }
}
