package com.recruitment.job;

import com.recruitment.ai.embedding.EmbeddingTextFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng, tach khoi JobEmbeddingOrchestrator - xem CLAUDE.md muc 3c. Method nay KHONG duoc
// goi qua self-invocation tu orchestrator (self-invocation khong di qua proxy Spring, @Transactional
// se khong mo transaction nao).
@Service
public class JobEmbeddingStateService {

    private final JobEmbeddingRepository jobEmbeddingRepository;

    public JobEmbeddingStateService(JobEmbeddingRepository jobEmbeddingRepository) {
        this.jobEmbeddingRepository = jobEmbeddingRepository;
    }

    @Transactional
    public void save(UUID jobId, float[] vector, String model) {
        jobEmbeddingRepository.upsertEmbedding(jobId, EmbeddingTextFormat.toVectorText(vector), model);
    }
}
