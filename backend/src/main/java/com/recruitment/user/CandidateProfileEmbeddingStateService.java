package com.recruitment.user;

import com.recruitment.ai.embedding.EmbeddingTextFormat;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng, tach khoi CandidateProfileEmbeddingOrchestrator - xem CLAUDE.md muc 3c. Method
// nay KHONG duoc goi qua self-invocation tu orchestrator.
@Service
public class CandidateProfileEmbeddingStateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileEmbeddingStateService.class);

    private final CandidateProfileRepository candidateProfileRepository;

    public CandidateProfileEmbeddingStateService(CandidateProfileRepository candidateProfileRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
    }

    // R-E5 - rowcount 0 la NO-OP AN TOAN (ho so da bi sua giua luc doc expectedUpdatedAt va luc ghi
    // o day), khong phai loi - khong throw, khong log.warn, chi debug roi thoi (lan poll ke tiep tu
    // doc lai gia tri moi nhat va tinh lai).
    @Transactional
    public void save(UUID id, float[] vector, String model, Instant expectedUpdatedAt) {
        int updated = candidateProfileRepository.updateEmbeddingIfUnchanged(
                id, EmbeddingTextFormat.toVectorText(vector), model, expectedUpdatedAt);
        if (updated == 0) {
            log.debug("Bo qua ghi embedding ho so: id={} da bi sua doi giua luc doc va luc ghi", id);
        }
    }
}
