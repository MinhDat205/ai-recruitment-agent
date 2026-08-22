package com.recruitment.resume;

import com.recruitment.ai.embedding.EmbeddingTextFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng, tach khoi ResumeEmbeddingOrchestrator - xem CLAUDE.md muc 3c. Method nay KHONG
// duoc goi qua self-invocation tu orchestrator. KHONG nhan tham so model (khac
// JobEmbeddingStateService cua Dot 3): resume_parsed_data.model/prompt_version da bi D1 chiem dung
// cho metadata buoc parse, khong co cot rieng cho model embedding - chap nhan mat provenance nay,
// ghi vao no ky thuat o ROADMAP dot cuoi (khong co tieu chi nghiem thu nao cua F1 can den no).
@Service
public class ResumeEmbeddingStateService {

    private final ResumeParsedDataRepository resumeParsedDataRepository;

    public ResumeEmbeddingStateService(ResumeParsedDataRepository resumeParsedDataRepository) {
        this.resumeParsedDataRepository = resumeParsedDataRepository;
    }

    @Transactional
    public void save(UUID parsedDataId, float[] vector) {
        resumeParsedDataRepository.updateEmbedding(parsedDataId, EmbeddingTextFormat.toVectorText(vector));
    }
}
