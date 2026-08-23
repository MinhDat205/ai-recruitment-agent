package com.recruitment.resume;

import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.ai.embedding.EmbeddingService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// KHONG @Transactional - xem CLAUDE.md muc 3c va Plan Mode F1 muc A, cung cau truc voi
// JobEmbeddingOrchestrator (Dot 3): doc -> dung text -> goi embedding NGOAI transaction -> ghi qua
// StateService (transaction ngan rieng). Khong co buoc "claim": resume_parsed_data khong co cot
// trang thai rieng cho embedding, @Scheduled(fixedDelay) da dam bao khong chay chong lot trong mot
// instance.
//
// Khac Dot 3 o HAI cho (xem yeu cau review Dot 4):
// 1. Dieu kien quet (ResumeParsedDataRepository.findIdsNeedingEmbedding, da co tu Dot 2) la
//    "embedding IS NULL" tren bang co san, KHONG phai NOT EXISTS giua hai bang nhu job_embeddings -
//    resume_parsed_data.embedding la cot nullable ngay tren chinh bang chua du lieu can doc.
// 2. Ghi bang UPDATE thuong (ResumeParsedDataRepository.updateEmbedding, da co tu Dot 2), KHONG
//    UPSERT - khong co UNIQUE constraint nao lien quan can xu ly xung dot (khac job_id UNIQUE cua
//    job_embeddings), moi resume_parsed_data.id da chac chan ton tai truoc do (D1 tao ra).
@Component
public class ResumeEmbeddingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResumeEmbeddingOrchestrator.class);

    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ResumeRepository resumeRepository;
    private final EmbeddingService embeddingService;
    private final ResumeEmbeddingStateService stateService;

    public ResumeEmbeddingOrchestrator(
            ResumeParsedDataRepository resumeParsedDataRepository,
            ResumeRepository resumeRepository,
            EmbeddingService embeddingService,
            ResumeEmbeddingStateService stateService) {
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.resumeRepository = resumeRepository;
        this.embeddingService = embeddingService;
        this.stateService = stateService;
    }

    // Kiem tra lai is_primary NGAY TRUOC khi embed (cung ly do TOCTOU voi Dot 3 kiem status ==
    // OPEN): candidate co the doi CV chinh GIUA luc resume_parsed_data nay bi chon vao lo va luc
    // thuc su duoc xu ly. "CV khong phai is_primary khong bao gio duoc sinh embedding" phai dung
    // TUYET DOI (xem test Dot 4), dong thoi tranh goi API thua cho CV vua mat quyen "CV chinh"
    // (Plan Mode F1 muc J).
    public void processOne(UUID parsedDataId) {
        ResumeParsedData data = resumeParsedDataRepository.findById(parsedDataId).orElse(null);
        if (data == null) {
            return;
        }
        Resume resume = resumeRepository.findById(data.getResumeId()).orElse(null);
        if (resume == null || !resume.isPrimary()) {
            return;
        }

        // Tai dung NGUYEN VAN CvImprovementOrchestrator.buildResumeText (F2, cung package resume,
        // package-private) - khong viet ham dung text moi. Ham nay render day du 22 truong cua
        // ResumeParsedPayload theo dung thu tu uu tien (contact -> hoc van -> kinh nghiem -> ky nang
        // -> chung chi -> du an) va tu cat o 10_000 ky tu (MAX_RESUME_TEXT_CHARS, da co san).
        String text = CvImprovementOrchestrator.buildResumeText(data.getData());

        EmbeddingResult result;
        try {
            result = embeddingService.embed(text);
        } catch (RuntimeException e) {
            // Khong ghi gi ca - resume_parsed_data.embedding van NULL, dieu kien IS NULL cua
            // scheduler van dung, lan poll ke tiep tu nhien thu lai (xem Plan Mode F1 muc A).
            log.debug(
                    "Loi khi sinh embedding cho CV, se thu lai o lot poll ke tiep: parsedDataId={}", parsedDataId, e);
            return;
        }

        stateService.save(parsedDataId, result.vector());
    }
}
