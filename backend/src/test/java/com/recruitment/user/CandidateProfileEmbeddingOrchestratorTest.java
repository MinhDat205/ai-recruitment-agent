package com.recruitment.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
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

// FR-U14 dot 4 (nhom 11, 12 cua muc 7 REQUIREMENT.md). Mau ResumeEmbeddingOrchestratorTest/
// JobEmbeddingOrchestratorTest: mock o tang EmbeddingModel (khong mock EmbeddingService truc tiep),
// KHONG @Transactional cap class - cac ban ghi phai COMMIT THAT de kiem dung trang thai DB sau khi
// orchestrator chay xong (khac CandidateProfileControllerIntegrationTest dung @Transactional vi
// khong can phan biet thoi diem giua cac ban ghi).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class CandidateProfileEmbeddingOrchestratorTest {

    @Autowired
    private CandidateProfileEmbeddingOrchestrator orchestrator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetEmbeddingModelMock() {
        Mockito.reset(embeddingModel);
    }

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

    private UUID createProfileWithContent(UUID candidateId, String headline, String[] skills, String bio) {
        CandidateProfile profile = new CandidateProfile(candidateId);
        profile.setHeadline(headline);
        profile.setSkills(skills);
        profile.setBio(bio);
        return candidateProfileRepository.saveAndFlush(profile).getId();
    }

    private boolean hasEmbedding(UUID profileId) {
        Object result = entityManager
                .createNativeQuery("SELECT (embedding IS NOT NULL) FROM candidate_profiles WHERE id = :id")
                .setParameter("id", profileId)
                .getSingleResult();
        return (Boolean) result;
    }

    private String embeddingModelColumn(UUID profileId) {
        return (String) entityManager
                .createNativeQuery("SELECT embedding_model FROM candidate_profiles WHERE id = :id")
                .setParameter("id", profileId)
                .getSingleResult();
    }

    // Nhom 11 - ca thanh cong: ho so KHONG bi sua giua luc doc va luc ghi -> UPDATE co dieu kien ghi
    // duoc, embedding khac NULL va embedding_model dung ten model that tra ve tu EmbeddingModel.
    // expectedUpdatedAt KHONG duoc truyen tay trong test nay - orchestrator tu doc truc tiep
    // profile.getUpdatedAt() (xem CandidateProfileEmbeddingOrchestrator.processOne), day chinh la
    // bang chung phep so khong bi lech do chinh xac vi ca hai ve (doc va ghi) deu lay tu CUNG mot
    // gia tri cot updated_at that trong Postgres, khong qua trung gian dong ho Java nao.
    @Test
    void processOne_profileNotModified_writesEmbeddingAndModel() {
        UUID candidateId = createCandidate();
        UUID profileId = createProfileWithContent(candidateId, "Backend Developer", new String[] {"Java", "SQL"}, "Bio test");
        doReturn(fakeResponse(1536, "text-embedding-3-small")).when(embeddingModel).embedForResponse(anyList());

        orchestrator.processOne(profileId);

        assertThat(hasEmbedding(profileId)).isTrue();
        assertThat(embeddingModelColumn(profileId)).isEqualTo("text-embedding-3-small");
    }

    // Nhom 12 - ca race: mo phong "luong khac" sua ho so (UPDATE that, kich hoat trigger
    // set_updated_at doi updated_at) NGAY TRONG LUC goi EmbeddingModel (doAnswer chay truoc khi tra
    // ket qua gia lap) - dung dung thoi diem GIUA luc orchestrator da doc expectedUpdatedAt (truoc
    // khi goi embed) va luc stateService ghi ket qua (sau khi embed tra ve). Ket qua mong doi: UPDATE
    // co dieu kien rowcount 0, KHONG ghi embedding (gia tri cu/gia lap khong bi ghi de boi vector).
    @Test
    void processOne_profileModifiedDuringEmbedCall_doesNotWriteStaleEmbedding() {
        UUID candidateId = createCandidate();
        UUID profileId =
                createProfileWithContent(candidateId, "Backend Developer", new String[] {"Java"}, "Bio truoc khi sua");

        doAnswer(invocation -> {
                    CandidateProfile profile = candidateProfileRepository.findById(profileId).orElseThrow();
                    profile.setBio("Bio da bi sua boi luong khac giua luc cho embedding");
                    candidateProfileRepository.saveAndFlush(profile);
                    return fakeResponse(1536, "text-embedding-3-small");
                })
                .when(embeddingModel)
                .embedForResponse(anyList());

        orchestrator.processOne(profileId);

        assertThat(hasEmbedding(profileId)).isFalse();
    }

    // Nhom 10 (phan quet) - ho so rong (headline/skills/bio deu rong) khong nam trong tap quet;
    // ho so co noi dung thi co.
    @Test
    void findIdsNeedingEmbedding_emptyProfileExcluded_nonEmptyProfileIncluded() {
        UUID emptyCandidateId = createCandidate();
        UUID emptyProfileId = createProfileWithContent(emptyCandidateId, null, new String[0], null);

        UUID filledCandidateId = createCandidate();
        UUID filledProfileId =
                createProfileWithContent(filledCandidateId, "Co noi dung", new String[0], null);

        List<UUID> needingEmbedding = candidateProfileRepository.findIdsNeedingEmbedding(PageRequest.of(0, 50));

        assertThat(needingEmbedding).doesNotContain(emptyProfileId);
        assertThat(needingEmbedding).contains(filledProfileId);
    }
}
