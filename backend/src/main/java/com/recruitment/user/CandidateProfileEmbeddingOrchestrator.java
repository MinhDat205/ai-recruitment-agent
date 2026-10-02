package com.recruitment.user;

import com.recruitment.ai.embedding.EmbeddingResult;
import com.recruitment.ai.embedding.EmbeddingService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// KHONG @Transactional - xem CLAUDE.md muc 3c, cung cau truc JobEmbeddingOrchestrator/
// ResumeEmbeddingOrchestrator: doc -> dung text -> goi embedding NGOAI transaction -> ghi qua
// StateService (transaction ngan rieng). Khong co buoc "claim": candidate_profiles khong co cot
// trang thai rieng cho embedding, @Scheduled(fixedDelay) da dam bao khong chay chong lot trong mot
// instance (R-E4).
@Component
public class CandidateProfileEmbeddingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CandidateProfileEmbeddingOrchestrator.class);

    private final CandidateProfileRepository candidateProfileRepository;
    private final EmbeddingService embeddingService;
    private final CandidateProfileEmbeddingStateService stateService;

    public CandidateProfileEmbeddingOrchestrator(
            CandidateProfileRepository candidateProfileRepository,
            EmbeddingService embeddingService,
            CandidateProfileEmbeddingStateService stateService) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.embeddingService = embeddingService;
        this.stateService = stateService;
    }

    public void processOne(UUID id) {
        CandidateProfile profile = candidateProfileRepository.findById(id).orElse(null);
        if (profile == null) {
            return;
        }

        String text = buildEmbeddingText(profile.getHeadline(), profile.getSkills(), profile.getBio());
        if (text.isEmpty()) {
            // Phong thu chieu sau - dieu kien quet (findIdsNeedingEmbedding) da loc van ban rong,
            // nhung ho so co the da doi thanh rong GIUA luc chon vao lo va luc xu ly.
            return;
        }

        // Doc NGAY TRUOC khi goi EmbeddingService (ngoai transaction) de dung lam dieu kien ghi R-E5
        // - KHONG dung Instant.now() (xem CandidateProfileEmbeddingStateService.save). getUpdatedAt()
        // phan anh dung gia tri cot updated_at thuc te trong DB (field @Generated, Hibernate tu doc
        // lai sau INSERT/UPDATE), cung cach doc expectedCount lam dieu kien optimistic-lock da co o
        // ResumeParsingStateService.markTemporaryFailure.
        Instant expectedUpdatedAt = profile.getUpdatedAt();

        EmbeddingResult result;
        try {
            result = embeddingService.embed(text);
        } catch (RuntimeException e) {
            // Khong ghi gi ca - embedding van NULL, dieu kien IS NULL cua scheduler van dung, lan
            // poll ke tiep tu nhien thu lai (R-E6).
            log.debug("Loi khi sinh embedding cho ho so, se thu lai o lot poll ke tiep: id={}", id, e);
            return;
        }

        stateService.save(id, result.vector(), result.model(), expectedUpdatedAt);
    }

    // R-E1 - ghep headline -> ky nang (noi dau phay) -> bio, bo qua phan trong, KHONG chen chuoi
    // rong/nhan "chua co". KHONG gom dateOfBirth/desiredSalaryMin/currentTitle/location/
    // yearsExperience/ten hay email ung vien (R-G4, R-E1). Goi tu CandidateProfileService.update()
    // (R-E3, cung package) de so van ban dai dien cu/moi - dung package-private static, khong tach
    // rieng mot class chi co ham nay (khac khong co tien le nao trong du an lam vay cho mot ham
    // dung text duy nhat).
    static String buildEmbeddingText(String headline, String[] skills, String bio) {
        List<String> parts = new ArrayList<>();

        String trimmedHeadline = nullToEmpty(headline).trim();
        if (!trimmedHeadline.isEmpty()) {
            parts.add(trimmedHeadline);
        }

        if (skills != null && skills.length > 0) {
            parts.add(String.join(", ", skills));
        }

        String trimmedBio = nullToEmpty(bio).trim();
        if (!trimmedBio.isEmpty()) {
            parts.add(trimmedBio);
        }

        return String.join("\n", parts);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
