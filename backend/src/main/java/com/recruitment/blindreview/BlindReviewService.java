package com.recruitment.blindreview;

import com.recruitment.blindreview.dto.BlindReviewRequest;
import com.recruitment.blindreview.dto.BlindReviewResponse;
import com.recruitment.common.exception.InvalidBlindReviewRequestException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.job.Job;
import com.recruitment.job.JobOwnerService;
import com.recruitment.job.JobRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H16 B1 (R-B2, R-B3, R-B4). Moi JobStatus deu doi duoc; bat lai sau khi tat duoc phep, khong gioi han so lan.
@Service
public class BlindReviewService {

    private final JobOwnerService jobOwnerService;
    private final JobRepository jobRepository;
    private final JobBlindReviewChangeRepository changeRepository;

    public BlindReviewService(
            JobOwnerService jobOwnerService,
            JobRepository jobRepository,
            JobBlindReviewChangeRepository changeRepository) {
        this.jobOwnerService = jobOwnerService;
        this.jobRepository = jobRepository;
        this.changeRepository = changeRepository;
    }

    @Transactional
    public BlindReviewResponse update(UUID ownerId, UUID jobId, BlindReviewRequest request) {
        if (request == null || request.enabled() == null) {
            throw new InvalidBlindReviewRequestException();
        }
        boolean enabled = request.enabled();
        // R-B3: khong ton tai -> 404, cong ty khac -> 403 (loadOwned); da xoa mem -> 404, kiem tuong minh.
        Job job = jobOwnerService.loadOwned(jobId, ownerId);
        if (job.getDeletedAt() != null) {
            throw new JobNotFoundException(jobId);
        }
        // UPDATE co dieu kien thay cho kiem-roi-ghi: hai request cung doi mot gia tri thi chi mot request ghi luu vet.
        if (jobRepository.updateBlindReview(jobId, enabled) == 1) {
            changeRepository.saveAndFlush(new JobBlindReviewChange(jobId, enabled, ownerId));
        }
        return new BlindReviewResponse(jobId, enabled, changeRepository.findLatestChangedAt(jobId));
    }
}
