package com.recruitment.job;

import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.ai.embedding.EmbeddingService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// KHONG @Transactional o day - xem CLAUDE.md muc 3c va Plan Mode F1 muc A: goi EmbeddingService co
// the mat vai giay, giu transaction suot luc do se can pool khi nhieu job cho xu ly cung luc. Khong
// co buoc "claim" (khac ResumeParsingOrchestrator/CvImprovementOrchestrator): job_embeddings khong
// co cot trang thai nao de claim - @Scheduled(fixedDelay) da dam bao khong chay chong lot trong mot
// instance (xem Plan Mode F1 muc A), va upsertEmbedding la thao tac idempotent.
@Component
public class JobEmbeddingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(JobEmbeddingOrchestrator.class);

    // ~2000-2600 token o ty le trung binh 3-4 ky tu/token (tieng Viet co dau nang hon tieng Anh mot
    // chut) - an toan duoi xa nguong 8191 token cua text-embedding-3-small. Mo ta job thuc te hau
    // het ngan hon nhieu, gioi han nay chi la luoi an toan cho truong hop bat thuong.
    static final int MAX_EMBEDDING_TEXT_CHARS = 8_000;
    private static final String TRUNCATION_MARKER = "\n[...da cat bot do dai...]";

    private final JobRepository jobRepository;
    private final EmbeddingService embeddingService;
    private final JobEmbeddingStateService stateService;

    public JobEmbeddingOrchestrator(
            JobRepository jobRepository, EmbeddingService embeddingService, JobEmbeddingStateService stateService) {
        this.jobRepository = jobRepository;
        this.embeddingService = embeddingService;
        this.stateService = stateService;
    }

    // Kiem tra lai status == OPEN NGAY TRUOC khi embed (khong chi dua vao dieu kien SELECT cua
    // JobEmbeddingRepository.findOpenJobIdsNeedingEmbedding): job co the doi trang thai GIUA luc bi
    // chon vao lo va luc thuc su duoc xu ly (lo co the mat vai giay neu nhieu job cho, moi job goi
    // EmbeddingModel rieng) - "khong bao gio sinh embedding cho job DRAFT/PAUSED/CLOSED" phai dung
    // TUYET DOI, khong chi "dung tai thoi diem chon".
    public void processOne(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.OPEN) {
            return;
        }

        String text = buildEmbeddingText(job);
        EmbeddingResult result;
        try {
            result = embeddingService.embed(text);
        } catch (RuntimeException e) {
            // Khong ghi gi ca - job_embeddings van chua co hang cho jobId nay, dieu kien NOT EXISTS
            // cua scheduler van dung, lan poll ke tiep tu nhien thu lai (xem Plan Mode F1 muc A).
            log.debug("Loi khi sinh embedding cho job, se thu lai o lot poll ke tiep: jobId={}", jobId, e);
            return;
        }

        stateService.save(jobId, result.vector(), result.model());
    }

    // Ghep title + category + description theo thu tu tin hieu ngan tu MANH nhat den noi dung day
    // du nhat: title la tin hieu co dong nhat, category la tin hieu ngu nghia THEM (khong dung de LOC
    // SQL - xem Plan Mode F1 rang buoc 1 - nhung van hop le trong VAN BAN de chinh embedding model tu
    // can nhac, khac ban chat voi code tu dong tin cay no). description mang phan lon noi dung cu the.
    //
    // Bai hoc F2 (CvImprovementOrchestrator.buildMarketTrendText): title/description la NOT NULL +
    // @NotBlank o JobRequest (khong the rong qua API binh thuong), nhung van PHONG THU CHIEU SAU o
    // day - khong tin tuyet doi rang buoc tang API, tranh NPE neu co duong ghi du lieu nao khac bo
    // qua validation. category KHONG @NotBlank, nullable that su o DB - bo qua hoan toan khoi van
    // ban neu null/blank, khong chen dong rong vo nghia.
    static String buildEmbeddingText(Job job) {
        StringBuilder text = new StringBuilder();

        String title = nullToEmpty(job.getTitle()).trim();
        if (!title.isEmpty()) {
            text.append(title).append('\n');
        }

        String category = nullToEmpty(job.getCategory()).trim();
        if (!category.isEmpty()) {
            text.append(category).append('\n');
        }

        String description = nullToEmpty(job.getDescription()).trim();
        if (!description.isEmpty()) {
            text.append(description);
        }

        return truncate(text.toString());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_EMBEDDING_TEXT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_EMBEDDING_TEXT_CHARS) + TRUNCATION_MARKER;
    }
}
