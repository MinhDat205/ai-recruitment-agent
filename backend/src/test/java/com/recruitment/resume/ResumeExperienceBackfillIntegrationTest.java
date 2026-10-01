package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// FR-C05 R-E9b - job nen tinh bu kinh nghiem cho ban ghi v1. KHONG @Transactional o class: moi
// computeOne tu mo/commit transaction rieng nhu luc chay that. Ban ghi v1 chen bang SQL voi JSON dung
// hinh v1 (khong co currentTitle/industryCode/locationText, khong co cot kinh nghiem) - dong thoi kiem
// "ban ghi v1 doc duoc qua Hibernate, truong moi null" (REQUIREMENT muc 7.5).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class ResumeExperienceBackfillIntegrationTest {

    private static final String V1_JSON_TEMPLATE =
            """
            {"contact": {"fullName": "Nguyen Van A", "email": "a@example.com"},
             "education": [],
             "experience": [%s],
             "skills": ["Java"],
             "certifications": [],
             "projects": []}
            """;

    @Autowired
    private ResumeExperienceStateService stateService;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetAiMocks() {
        // Chi reset de verifyNoInteractions chi nhin tuong tac cua CHINH test nay (mock dung chung context).
        Mockito.reset(chatModel, embeddingModel);
    }

    private UUID createDoneResume() {
        User user = new User();
        user.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(Role.CANDIDATE);
        user.setFullName("Ung Vien Test");
        UUID candidateId = userRepository.save(user).getId();

        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.save(resume).getId();
    }

    // experienceJson: noi dung mang experience, vd {"startDate":"01/2020","endDate":"06/2020"}.
    private UUID insertV1Row(String experienceJson) {
        UUID resumeId = createDoneResume();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO resume_parsed_data (id, resume_id, raw_text, data, model, prompt_version) "
                        + "VALUES (?, ?, 'raw v1', CAST(? AS jsonb), 'claude-sonnet-4-6', 'resume-parse-v1')",
                id,
                resumeId,
                V1_JSON_TEMPLATE.formatted(experienceJson));
        return id;
    }

    private ResumeParsedData reload(UUID id) {
        entityManager.clear();
        return resumeParsedDataRepository.findById(id).orElseThrow();
    }

    @Test
    void v1Row_readsThroughHibernateWithNewFieldsNull() {
        UUID id = insertV1Row("{\"company\":\"ABC\",\"startDate\":\"01/2020\",\"endDate\":\"06/2020\"}");

        ResumeParsedData data = reload(id);

        assertThat(data.getData().currentTitle()).isNull();
        assertThat(data.getData().industryCode()).isNull();
        assertThat(data.getData().locationText()).isNull();
        assertThat(data.getIndustryCode()).isNull();
        assertThat(data.getRegionCode()).isNull();
        assertThat(data.getExperienceComputedAt()).isNull();
        assertThat(data.getData().experience()).hasSize(1);
    }

    @Test
    void computeOne_v1Row_writesExperienceWithoutCallingAi() {
        UUID id = insertV1Row("{\"startDate\":\"01/2020\",\"endDate\":\"06/2020\"},"
                + "{\"startDate\":\"2019\",\"endDate\":\"2021\"}");

        boolean written = stateService.computeOne(id);

        assertThat(written).isTrue();
        ResumeParsedData data = reload(id);
        assertThat(data.getExperienceMonths()).isEqualTo(6);
        assertThat(data.getExperienceEntriesCounted()).isEqualTo(1);
        assertThat(data.getExperienceEntriesSkipped()).isEqualTo(1);
        assertThat(data.getExperienceComputedAt()).isNotNull();
        // Ban ghi v1 giu nguyen phien ban va data - job nen chi ghi bon cot kinh nghiem.
        assertThat(data.getPromptVersion()).isEqualTo("resume-parse-v1");
        verifyNoInteractions(chatModel, embeddingModel);
    }

    // R-E7: khong co muc nao doc duoc -> months NULL, KHONG 0; van danh dau da tinh.
    @Test
    void computeOne_noReadableEntry_monthsNullNotZero() {
        UUID id = insertV1Row("{\"startDate\":\"2019\",\"endDate\":\"2021\"},{\"startDate\":\"abc\",\"endDate\":null}");

        stateService.computeOne(id);

        ResumeParsedData data = reload(id);
        assertThat(data.getExperienceMonths()).isNull();
        assertThat(data.getExperienceEntriesCounted()).isZero();
        assertThat(data.getExperienceEntriesSkipped()).isEqualTo(2);
        assertThat(data.getExperienceComputedAt()).isNotNull();
        Integer rawMonths = jdbcTemplate.queryForObject(
                "SELECT experience_months FROM resume_parsed_data WHERE id = ?", Integer.class, id);
        assertThat(rawMonths).isNull();
    }

    @Test
    void computeOne_emptyExperienceList_monthsNullCountersZero() {
        UUID id = insertV1Row("");

        stateService.computeOne(id);

        ResumeParsedData data = reload(id);
        assertThat(data.getExperienceMonths()).isNull();
        assertThat(data.getExperienceEntriesCounted()).isZero();
        assertThat(data.getExperienceEntriesSkipped()).isZero();
        assertThat(data.getExperienceComputedAt()).isNotNull();
    }

    // Ban ghi da tinh (vd trich xuat lai vua ghi) -> khong tinh lai, khong ghi de.
    @Test
    void computeOne_alreadyComputed_returnsFalseAndKeepsValues() {
        UUID id = insertV1Row("{\"startDate\":\"01/2020\",\"endDate\":\"06/2020\"}");
        Instant earlier = Instant.parse("2025-01-15T00:00:00Z");
        jdbcTemplate.update(
                "UPDATE resume_parsed_data SET experience_months = 99, experience_entries_counted = 1, "
                        + "experience_entries_skipped = 0, experience_computed_at = ? WHERE id = ?",
                java.sql.Timestamp.from(earlier),
                id);

        boolean written = stateService.computeOne(id);

        assertThat(written).isFalse();
        ResumeParsedData data = reload(id);
        assertThat(data.getExperienceMonths()).isEqualTo(99);
        assertThat(data.getExperienceComputedAt()).isEqualTo(earlier);
    }

    // Chot chan cua claim: UPDATE co dieu kien khong ghi de khi ban ghi da duoc tinh giua luc doc va ghi.
    @Test
    void writeExperienceIfPending_rowAlreadyComputed_returnsZero() {
        UUID id = insertV1Row("{\"startDate\":\"01/2020\",\"endDate\":\"06/2020\"}");
        Integer first = transactionTemplate.execute(
                status -> resumeParsedDataRepository.writeExperienceIfPending(id, 6, 1, 0, Instant.now()));
        Integer second = transactionTemplate.execute(
                status -> resumeParsedDataRepository.writeExperienceIfPending(id, 42, 1, 0, Instant.now()));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(reload(id).getExperienceMonths()).isEqualTo(6);
    }

    // Vong quet cua scheduler (tu new, khong @Scheduled tick) nhat ban ghi chua tinh va tinh xong.
    @Test
    void scheduler_computePending_processesV1Rows() {
        UUID id = insertV1Row("{\"startDate\":\"03/2021\",\"endDate\":\"02/2022\"}");
        ResumeExperienceScheduler scheduler = new ResumeExperienceScheduler(stateService, 10_000);

        scheduler.computePending();

        ResumeParsedData data = reload(id);
        assertThat(data.getExperienceMonths()).isEqualTo(12);
        assertThat(data.getExperienceComputedAt()).isNotNull();
        verifyNoInteractions(chatModel, embeddingModel);
    }
}
