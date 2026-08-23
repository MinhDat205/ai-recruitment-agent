package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.embedding.EmbeddingTextFormat;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ResumeParsedDataRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createCandidateAndResume() {
        return createCandidateAndResume(true);
    }

    // Them tham so isPrimary (Dot 2, F1) - can ca hai truong hop de kiem
    // findIdsNeedingEmbedding chi lay CV chinh (xem Plan Mode F1 muc J: CV khong phai CV chinh
    // khong bao gio duoc doc lai, embed no la goi API thua).
    private UUID createCandidateAndResume(boolean isPrimary) {
        User user = new User();
        user.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien Test");
        user = userRepository.save(user);

        Resume resume = new Resume();
        resume.setCandidateId(user.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(isPrimary);
        resume.setParseStatus(ParseStatus.PROCESSING);
        resume = resumeRepository.save(resume);
        return resume.getId();
    }

    private ResumeParsedPayload samplePayload() {
        return new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", "0900000000", "Ha Noi", null),
                List.of(new ResumeParsedPayload.Education(
                        "DHBK Ha Noi", "Ky su", "Cong nghe thong tin", "09/2018", "06/2022", 3.2)),
                List.of(new ResumeParsedPayload.Experience(
                        "Cong ty ABC", "Backend Developer", "07/2022", "Hien tai", "Phat trien API noi bo")),
                List.of("Java", "Spring Boot"),
                List.of(new ResumeParsedPayload.Certification("AWS Certified Developer", "Amazon", "2023")),
                List.of(new ResumeParsedPayload.Project(
                        "He thong tuyen dung", "Xay dung backend cham diem CV", List.of("Java", "PostgreSQL"))));
    }

    private ResumeParsedData newRow(UUID resumeId, String rawText) {
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText(rawText);
        data.setData(samplePayload());
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        data.setTokenUsage(150);
        return data;
    }

    @Test
    void saveAndReload_roundTripsNestedJsonbFieldsExactly() {
        UUID resumeId = createCandidateAndResume();

        resumeParsedDataRepository.saveAndFlush(newRow(resumeId, "Noi dung CV gia lap cho test"));
        entityManager.clear();

        ResumeParsedData reloaded = resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow();
        assertThat(reloaded.getRawText()).isEqualTo("Noi dung CV gia lap cho test");
        assertThat(reloaded.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(reloaded.getPromptVersion()).isEqualTo("resume-parse-v1");
        assertThat(reloaded.getTokenUsage()).isEqualTo(150);
        assertThat(reloaded.getParsedAt()).isNotNull();

        ResumeParsedPayload data = reloaded.getData();
        assertThat(data.contact().fullName()).isEqualTo("Nguyen Van A");
        assertThat(data.contact().linkedin()).isNull();
        assertThat(data.education()).hasSize(1);
        assertThat(data.education().get(0).school()).isEqualTo("DHBK Ha Noi");
        assertThat(data.education().get(0).gpa()).isEqualTo(3.2);
        assertThat(data.experience()).hasSize(1);
        assertThat(data.experience().get(0).company()).isEqualTo("Cong ty ABC");
        assertThat(data.skills()).containsExactly("Java", "Spring Boot");
        assertThat(data.certifications()).hasSize(1);
        assertThat(data.certifications().get(0).name()).isEqualTo("AWS Certified Developer");
        assertThat(data.projects()).hasSize(1);
        assertThat(data.projects().get(0).technologies()).containsExactly("Java", "PostgreSQL");
    }

    @Test
    void payloadWithNullLists_normalizesToEmptyListsAtConstructionAndAfterRoundTrip() {
        ResumeParsedPayload payloadWithNulls = new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van B", "b@example.com", null, null, null),
                List.of(new ResumeParsedPayload.Education("DHQG Ha Noi", "Cu nhan", "Kinh te", "2015", "2019", null)),
                List.of(),
                List.of("Excel"),
                null, // certifications - LLM khong tim thay muc nay trong CV
                null); // projects - LLM khong tim thay muc nay trong CV

        // Compact constructor phai chan null NGAY luc khoi tao, truoc khi cham vao DB.
        assertThat(payloadWithNulls.certifications()).isNotNull().isEmpty();
        assertThat(payloadWithNulls.projects()).isNotNull().isEmpty();

        UUID resumeId = createCandidateAndResume();
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("CV khong co muc chung chi va du an");
        data.setData(payloadWithNulls);
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.saveAndFlush(data);
        entityManager.clear();

        ResumeParsedPayload reloaded =
                resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow().getData();
        assertThat(reloaded.certifications()).isNotNull().isEmpty();
        assertThat(reloaded.projects()).isNotNull().isEmpty();
    }

    @Test
    void payloadWithNullContact_staysNullAfterRoundTrip() {
        ResumeParsedPayload payloadWithNullContact = new ResumeParsedPayload(
                null, // contact - LLM khong trich duoc thong tin lien he nao tu CV
                List.of(new ResumeParsedPayload.Education("DHQG Ha Noi", "Cu nhan", "Kinh te", "2015", "2019", null)),
                List.of(),
                List.of("Excel"),
                List.of(),
                List.of());

        assertThat(payloadWithNullContact.contact()).isNull();

        UUID resumeId = createCandidateAndResume();
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("CV khong co thong tin lien he ro rang");
        data.setData(payloadWithNullContact);
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.saveAndFlush(data);
        entityManager.clear();

        ResumeParsedPayload reloaded =
                resumeParsedDataRepository.findByResumeId(resumeId).orElseThrow().getData();
        // contact PHAI van la null - khong bi Jackson bien thanh object rong voi moi field null.
        assertThat(reloaded.contact()).isNull();
    }

    @Test
    void save_withNonExistentResumeId_violatesForeignKey() {
        ResumeParsedData data = newRow(UUID.randomUUID(), "text");

        assertThrows(DataIntegrityViolationException.class, () -> resumeParsedDataRepository.saveAndFlush(data));
    }

    @Test
    void save_secondRowForSameResume_violatesUniqueConstraint() {
        UUID resumeId = createCandidateAndResume();
        resumeParsedDataRepository.saveAndFlush(newRow(resumeId, "text 1"));

        ResumeParsedData second = newRow(resumeId, "text 2");

        assertThrows(DataIntegrityViolationException.class, () -> resumeParsedDataRepository.saveAndFlush(second));
    }

    // --- Test moi cho F1 (Dot 2): findIdsNeedingEmbedding / updateEmbedding ---

    @Test
    void findIdsNeedingEmbedding_primaryResumeWithoutEmbedding_isIncluded() {
        UUID resumeId = createCandidateAndResume(true);
        ResumeParsedData data =
                resumeParsedDataRepository.saveAndFlush(newRow(resumeId, "CV chinh chua co embedding"));

        List<UUID> ids = resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, 10));

        assertThat(ids).contains(data.getId());
    }

    // @Transactional o MUC METHOD - can thiet cho updateEmbedding (@Modifying can transaction dang
    // mo de thuc thi, xem tien le CvImprovementRequestRepositoryTest.claimForProcessing). Cac test
    // khac trong file nay khong doi vi khong goi @Modifying nao.
    @Test
    @Transactional
    void findIdsNeedingEmbedding_primaryResumeWithEmbedding_isExcluded() {
        UUID resumeId = createCandidateAndResume(true);
        ResumeParsedData data = resumeParsedDataRepository.saveAndFlush(newRow(resumeId, "CV chinh da co embedding"));
        resumeParsedDataRepository.updateEmbedding(data.getId(), EmbeddingTextFormat.toVectorText(new float[1536]));

        List<UUID> ids = resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, 10));

        assertThat(ids).doesNotContain(data.getId());
    }

    @Test
    void findIdsNeedingEmbedding_nonPrimaryResumeWithoutEmbedding_isExcluded() {
        UUID resumeId = createCandidateAndResume(false);
        ResumeParsedData data =
                resumeParsedDataRepository.saveAndFlush(newRow(resumeId, "CV khong phai CV chinh"));

        List<UUID> ids = resumeParsedDataRepository.findIdsNeedingEmbedding(PageRequest.of(0, 10));

        assertThat(ids).doesNotContain(data.getId());
    }

    // So sanh bang toan tu "=" cua pgvector (gia tri so), KHONG so sanh chuoi embedding::text: da
    // kiem thuc nghiem (Dot 2) Postgres tu chuan hoa "0.0" (Java Float.toString) thanh "0" khi cast
    // sang text - gia tri van dung, chi khac bieu dien van ban. Dieu can kiem la GIA TRI da luu dung,
    // khong phai van ban y het.
    @Test
    @Transactional
    void updateEmbedding_roundTripsVectorValueExactly() {
        UUID resumeId = createCandidateAndResume(true);
        ResumeParsedData data = resumeParsedDataRepository.saveAndFlush(newRow(resumeId, "CV mau"));
        float[] vector = new float[1536];
        vector[0] = 0.1f;
        vector[1] = -0.2f;
        String vectorText = EmbeddingTextFormat.toVectorText(vector);

        resumeParsedDataRepository.updateEmbedding(data.getId(), vectorText);
        entityManager.clear();

        Boolean equal = (Boolean) entityManager
                .createNativeQuery("SELECT embedding = CAST(:v AS vector) FROM resume_parsed_data WHERE id = :id")
                .setParameter("v", vectorText)
                .setParameter("id", data.getId())
                .getSingleResult();
        assertThat(equal).isTrue();
    }
}
