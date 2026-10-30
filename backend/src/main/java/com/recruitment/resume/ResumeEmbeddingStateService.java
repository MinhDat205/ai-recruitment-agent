package com.recruitment.resume;

import com.recruitment.ai.embedding.EmbeddingTextFormat;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng, tach khoi ResumeEmbeddingOrchestrator - xem CLAUDE.md muc 3c. Method nay KHONG
// duoc goi qua self-invocation tu orchestrator. KHONG nhan tham so model (khac
// JobEmbeddingStateService cua Dot 3): resume_parsed_data.model/prompt_version da bi D1 chiem dung
// cho metadata buoc parse, khong co cot rieng cho model embedding - chap nhan mat provenance nay,
// ghi vao no ky thuat o ROADMAP dot cuoi (khong co tieu chi nghiem thu nao cua F1 can den no).
@Service
public class ResumeEmbeddingStateService {

    private static final Logger log = LoggerFactory.getLogger(ResumeEmbeddingStateService.class);

    private final ResumeParsedDataRepository resumeParsedDataRepository;

    public ResumeEmbeddingStateService(ResumeParsedDataRepository resumeParsedDataRepository) {
        this.resumeParsedDataRepository = resumeParsedDataRepository;
    }

    // perf/scoring-latency - rowcount 0 la NO-OP AN TOAN (CV vua duoc trich xuat lai giua luc doc
    // expectedParsedAt va luc ghi o day), khong phai loi - khong throw, chi debug (mau
    // CandidateProfileEmbeddingStateService.save). Lan poll ke tiep doc lai van ban moi va tinh lai.
    @Transactional
    public void save(UUID parsedDataId, float[] vector, Instant expectedParsedAt) {
        int updated = resumeParsedDataRepository.updateEmbeddingIfUnchanged(
                parsedDataId, EmbeddingTextFormat.toVectorText(vector), expectedParsedAt);
        if (updated == 0) {
            log.debug("Bo qua ghi embedding CV: parsedDataId={} da duoc trich xuat lai giua luc doc va luc ghi",
                    parsedDataId);
        }
    }
}
