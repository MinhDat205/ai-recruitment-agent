package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
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

// Tich hop pipeline sinh embedding cho CV. Ba diem giong het JobEmbeddingPipelineIntegrationTest
// (Dot 3) - doc lai comment o do neu can chi tiet day du hon:
// 1. Test nay KHONG cham class ResumeEmbeddingScheduler - bean bi tat trong profile test qua
//    app.resume-embedding.enabled=false (application-test.yml, da them tu Dot 3).
// 2. pollOnce() (ben duoi) lap lai dung logic ResumeEmbeddingScheduler.pollResumesNeedingEmbedding():
//    goi findIdsNeedingEmbedding roi lap qua orchestrator.processOne().
// 3. Gioi han da biet, ap dung chung MOI scheduler trong du an (khong rieng F1): "@Scheduled chay
//    dung chu ky" va "@ConditionalOnProperty bat/tat dung" khong duoc test tu dong nao phu.
//
// Khac Dot 3: KHONG co kich ban "sua roi bi xoa de sinh lai" - job co (Plan Mode F1 muc B), CV thi
// khong can, vi moi lan upload CV moi la mot resume_parsed_data MOI, embedding luon bat dau NULL.
// Test cuoi file (them ngoai danh sach yeu cau, xac nhan dung ly do khong-can-hook-rieng nay): CV cu
// giu nguyen embedding khi candidate doi CV chinh, CV moi tu nhien duoc nhat qua dung flow poll binh
// thuong.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ResumeEmbeddingPipelineIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ResumeEmbeddingOrchestrator orchestrator;

    @Autowired
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void resetEmbeddingModelMock() {
        Mockito.reset(embeddingModel);
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());
    }

    // vector[0] = 0.1f (KHONG de vector toan so 0) - cung ly do voi ResumeEmbeddingOrchestratorTest/
    // JobEmbeddingOrchestratorTest: tranh vector suy bien gay NaN o cosine distance, ro ri that vao
    // Postgres Testcontainers dung chung (file nay khong @Transactional).
    private EmbeddingResponse fakeResponse(int dimensions, String model) {
        float[] vector = new float[dimensions];
        if (dimensions > 0) {
            vector[0] = 0.1f;
        }
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
                new ResumeParsedPayload.Contact("Nguyen Van Test", "test@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java"),
                List.of(),
                List.of(), null, null, null);
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

    // Thay cho resumeEmbeddingScheduler.pollResumesNeedingEmbedding() (bean bi tat trong test, xem
    // comment dau class) - lap lai dung logic do: query chon lo roi goi orchestrator cho tung CV.
    private void pollOnce() {
        List<UUID> ids = resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, 10));
        for (UUID id : ids) {
            orchestrator.processOne(id);
        }
    }

    @Test
    void pollOnce_primaryResumeWithoutEmbedding_createsEmbedding() {
        UUID candidateId = createCandidate();
        UUID resumeId = createResume(candidateId, true);
        UUID parsedDataId = createParsedData(resumeId);

        pollOnce();

        assertThat(resumeParsedDataRepository.hasEmbedding(parsedDataId)).isTrue();
    }

    @Test
    void pollOnce_nonPrimaryResume_neverGetsEmbedding() {
        UUID candidateId = createCandidate();
        UUID resumeId = createResume(candidateId, false);
        UUID parsedDataId = createParsedData(resumeId);

        pollOnce();

        assertThat(resumeParsedDataRepository.hasEmbedding(parsedDataId)).isFalse();
    }

    @Test
    void pollOnce_afterSwitchingPrimaryResume_oldEmbeddingUntouchedNewResumeEmbedded() {
        UUID candidateId = createCandidate();
        UUID oldResumeId = createResume(candidateId, true);
        UUID oldParsedDataId = createParsedData(oldResumeId);
        pollOnce();
        assertThat(resumeParsedDataRepository.hasEmbedding(oldParsedDataId)).isTrue();

        Resume oldResume = resumeRepository.findById(oldResumeId).orElseThrow();
        oldResume.setPrimary(false);
        resumeRepository.saveAndFlush(oldResume);
        UUID newResumeId = createResume(candidateId, true);
        UUID newParsedDataId = createParsedData(newResumeId);

        pollOnce();

        assertThat(resumeParsedDataRepository.hasEmbedding(oldParsedDataId)).isTrue();
        assertThat(resumeParsedDataRepository.hasEmbedding(newParsedDataId)).isTrue();
    }
}
