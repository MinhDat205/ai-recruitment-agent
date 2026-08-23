package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.embedding.EmbeddingTextFormat;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Mock o tang EmbeddingModel (khong mock EmbeddingService truc tiep) - dung tien le Dot 3
// (JobEmbeddingOrchestratorTest): mock model that thap nhat, de EmbeddingService that chay nguyen.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ResumeEmbeddingOrchestratorTest {

    @Autowired
    private ResumeEmbeddingOrchestrator orchestrator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetEmbeddingModelMock() {
        Mockito.reset(embeddingModel);
    }

    // vector[0] = 0.1f (KHONG de vector toan so 0) - vector 0 co norm bang 0, cosine distance voi
    // bat ky vector nao khac la NaN; Postgres coi NaN LON HON moi so khac khi so sanh thu tu
    // (>= 0.4 tra ve true), khien mot CV suy bien vuot moi nguong similarity. File nay co test
    // KHONG @Transactional nen vector 0 co the ton tai THAT trong Postgres Testcontainers dung
    // chung - da gay loi NaN o JobRecommendationCacheServiceTest chay sau trong cung full suite
    // (xem yeu cau review sau Dot 5, phat hien o phia job_embeddings, ap dung cung ly do o day).
    private EmbeddingResponse fakeResponse(int dimensions, String model) {
        float[] vector = new float[dimensions];
        if (dimensions > 0) {
            vector[0] = 0.1f;
        }
        Embedding embedding = new Embedding(vector, 0);
        return new EmbeddingResponse(List.of(embedding), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

    private EmbeddingResponse fakeResponse(float[] vector, String model) {
        Embedding embedding = new Embedding(vector, 0);
        return new EmbeddingResponse(List.of(embedding), new EmbeddingResponseMetadata(model, new DefaultUsage(10, 0)));
    }

    private UUID createCandidate() {
        User user = new User();
        user.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien Test");
        return userRepository.save(user).getId();
    }

    private UUID createResume(UUID candidateId, boolean isPrimary) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(isPrimary);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    private ResumeParsedPayload samplePayload() {
        return new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van Test", "test@example.com", "0900000000", "Ha Noi", null),
                List.of(new ResumeParsedPayload.Education(
                        "DHBK Ha Noi", "Ky su", "Cong nghe thong tin", "09/2018", "06/2022", 3.2)),
                List.of(new ResumeParsedPayload.Experience(
                        "Cong ty ABC", "Backend Developer", "07/2022", "Hien tai", "Phat trien API noi bo")),
                List.of("Java", "Spring Boot"),
                List.of(),
                List.of());
    }

    private UUID createParsedData(UUID resumeId) {
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("Noi dung CV test");
        data.setData(samplePayload());
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        return resumeParsedDataRepository.saveAndFlush(data).getId();
    }

    @Test
    void processOne_primaryResumeWithoutEmbedding_savesEmbedding() {
        UUID candidateId = createCandidate();
        UUID resumeId = createResume(candidateId, true);
        UUID parsedDataId = createParsedData(resumeId);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(parsedDataId);

        assertThat(resumeParsedDataRepository.hasEmbedding(parsedDataId)).isTrue();
    }

    @Test
    void processOne_embeddingApiThrows_writesNothingAndCanRetryNextPoll() {
        UUID candidateId = createCandidate();
        UUID resumeId = createResume(candidateId, true);
        UUID parsedDataId = createParsedData(resumeId);
        doThrow(new RuntimeException("loi mang gia lap")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(parsedDataId);

        assertThat(resumeParsedDataRepository.hasEmbedding(parsedDataId)).isFalse();
        List<UUID> stillNeeding = resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, 10));
        assertThat(stillNeeding).contains(parsedDataId);
    }

    @Test
    void processOne_nonPrimaryResume_doesNotSaveEmbedding() {
        UUID candidateId = createCandidate();
        UUID resumeId = createResume(candidateId, false);
        UUID parsedDataId = createParsedData(resumeId);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(parsedDataId);

        assertThat(resumeParsedDataRepository.hasEmbedding(parsedDataId)).isFalse();
    }

    // Hai resume KHAC NHAU cua CUNG mot candidate, tung la CV chinh o hai THOI DIEM khac nhau
    // (uq_resume_primary_per_candidate - V3 - chi cho phep MOT resume la primary tai mot thoi diem,
    // khong the seed ca hai cung true). Muc dich: xac nhan updateEmbedding scope dung theo
    // resume_parsed_data.id - embedding cua A KHONG bi doi/mat khi B duoc xu ly sau do. Dung
    // EntityManager doc truc tiep gia tri vector (khong phai chi kiem "co hay khong") de chung minh
    // dung vector, khong bi ghi de/lech hang - can @Transactional o day (goi truc tiep EntityManager,
    // khac cac test tren chi goi qua repository method).
    @Test
    @Transactional
    void processOne_twoResumesOfSameCandidateProcessedAtDifferentTimes_eachKeepsOwnEmbedding() {
        UUID candidateId = createCandidate();
        UUID resumeAId = createResume(candidateId, true);
        UUID parsedDataAId = createParsedData(resumeAId);

        float[] vectorA = new float[1536];
        vectorA[0] = 0.1f;
        float[] vectorB = new float[1536];
        vectorB[0] = 0.2f;
        doReturn(fakeResponse(vectorA, "text-embedding-3-small"))
                .doReturn(fakeResponse(vectorB, "text-embedding-3-small"))
                .when(embeddingModel)
                .embedForResponse(anyList());

        orchestrator.processOne(parsedDataAId);
        assertThat(resumeParsedDataRepository.hasEmbedding(parsedDataAId)).isTrue();

        // Tat A truoc roi moi bat B (UNIQUE index chi ap dung khi is_primary=true - lam nguoc se vi
        // pham ngay lap tuc vi ca hai cung true cung luc).
        Resume resumeA = resumeRepository.findById(resumeAId).orElseThrow();
        resumeA.setPrimary(false);
        resumeRepository.saveAndFlush(resumeA);
        UUID resumeBId = createResume(candidateId, true);
        UUID parsedDataBId = createParsedData(resumeBId);

        orchestrator.processOne(parsedDataBId);

        assertVectorEquals(parsedDataAId, vectorA);
        assertVectorEquals(parsedDataBId, vectorB);
    }

    private void assertVectorEquals(UUID parsedDataId, float[] vector) {
        String vectorText = EmbeddingTextFormat.toVectorText(vector);
        Boolean equal = (Boolean) entityManager
                .createNativeQuery("SELECT embedding = CAST(:v AS vector) FROM resume_parsed_data WHERE id = :id")
                .setParameter("v", vectorText)
                .setParameter("id", parsedDataId)
                .getSingleResult();
        assertThat(equal).isTrue();
    }
}
