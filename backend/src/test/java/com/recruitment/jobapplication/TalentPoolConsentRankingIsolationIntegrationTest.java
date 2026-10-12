package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U09 T14 (B1, R-N2) - co dong y luu ho so (ACTIVE / NOT_GIVEN / REVOKED) va cau tra loi sang loc KHONG doi tong diem,
// hang, trang thai don o danh sach theo Job lan o E3 (FR-H09); rut dong y roi goi lai -> JSON tung dong y het. Khuon
// ScreeningRankingIsolationIntegrationTest cua FR-H10: luot DONE + criterion_scores ghi thang bang repository (khong LLM),
// don tao bang entity (ba cot dong y insertable, chi khong updatable), ban chup ghi bang JdbcTemplate. @Transactional:
// moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TalentPoolConsentRankingIsolationIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant CONSENTED_AT = Instant.parse("2026-10-05T17:35:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EntityManager entityManager;

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String registerAndLoginHr() throws Exception {
        String email = "t14k-hr-" + UUID.randomUUID() + "@example.com";
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nha Tuyen Dung\",\"phone\":\"0900000000\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = body(login).get("accessToken").asString();
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
        return token;
    }

    private String createJob(String token) throws Exception {
        String body =
                """
                {
                  "job": {"title":"Nhan vien CSKH","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao ung vien.","senderName":"Phong Nhan Su"}
                }
                """;
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asString();
    }

    private record Created(UUID applicationId, UUID candidateId) {
    }

    private enum Consent {
        ACTIVE,
        NOT_GIVEN,
        REVOKED
    }

    private Created createApplicationEntity(String jobId, Consent consent) {
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();

        User candidate = new User();
        candidate.setEmail("t14k-cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        switch (consent) {
            case ACTIVE -> {
                application.setTalentPoolConsent(true);
                application.setTalentPoolConsentAt(CONSENTED_AT);
            }
            case REVOKED -> {
                application.setTalentPoolConsentAt(CONSENTED_AT);
                application.setTalentPoolConsentRevokedAt(CONSENTED_AT.plusSeconds(3600));
            }
            case NOT_GIVEN -> {
                // ba cot de mac dinh: FALSE, NULL, NULL
            }
        }
        return new Created(jobApplicationRepository.saveAndFlush(application).getId(), candidate.getId());
    }

    // Luot DONE voi hai tieu chi; cung tham so -> criterion_scores giong het giua cac don.
    private void createScoredRun(UUID applicationId, BigDecimal totalScore, BigDecimal javaScore) {
        RubricSnapshot snapshot = new RubricSnapshot("Rubric Test", List.of(
                new RubricSnapshot.CriterionSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Kinh nghiem Java", null,
                        new BigDecimal("60.00"), 5, null),
                new RubricSnapshot.CriterionSnapshot(
                        UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Giao tiep", null,
                        new BigDecimal("40.00"), 5, null)));
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(totalScore);
        UUID runId = scoringRunRepository.saveAndFlush(run).getId();
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("60.00"), javaScore);
        insertCriterionScore(runId, "Giao tiep", new BigDecimal("40.00"), new BigDecimal("4.00"));
    }

    private void insertCriterionScore(UUID runId, String name, BigDecimal weight, BigDecimal score) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(runId);
        criterionScore.setCriterionNameSnapshot(name);
        criterionScore.setWeightSnapshot(weight);
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(score);
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    private void captureYesNo(UUID applicationId, boolean answer) {
        jdbcTemplate.update(
                "UPDATE job_applications SET screening_captured_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC),
                applicationId);
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers
                    (application_id, display_order, question_type_snapshot, question_text_snapshot, required_snapshot,
                     answer_yes_no)
                VALUES (?, 1, 'YES_NO', 'San sang lam ca toi?', TRUE, ?)
                """,
                applicationId, answer);
    }

    // Anh chup diem/hang/trang thai cua moi don: dong o danh sach theo Job + body E3.
    private record Observed(Map<String, JsonNode> listItems, Map<String, String> scores) {
    }

    private Observed observe(String token, String jobId, List<UUID> applicationIds) throws Exception {
        MvcResult list = mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/applications").header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        Map<String, JsonNode> listItems = new HashMap<>();
        for (JsonNode item : body(list)) {
            listItems.put(item.get("id").asString(), item);
        }
        Map<String, String> scores = new HashMap<>();
        for (UUID applicationId : applicationIds) {
            MvcResult e3 = mockMvc
                    .perform(get("/api/hr/applications/" + applicationId + "/scores")
                            .header("Authorization", "Bearer " + token))
                    .andReturn();
            assertThat(e3.getResponse().getStatus()).isEqualTo(200);
            scores.put(applicationId.toString(), e3.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
        return new Observed(listItems, scores);
    }

    @Test
    void t14_talentPoolConsentAndScreeningAnswers_doNotChangeTotalScoreRankOrStatus() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createJob(token);
        Created active = createApplicationEntity(jobId, Consent.ACTIVE);
        Created notGiven = createApplicationEntity(jobId, Consent.NOT_GIVEN);
        Created revoked = createApplicationEntity(jobId, Consent.REVOKED);
        Created higher = createApplicationEntity(jobId, Consent.NOT_GIVEN);
        for (Created tied : List.of(active, notGiven, revoked)) {
            createScoredRun(tied.applicationId(), new BigDecimal("80.000"), new BigDecimal("4.00"));
        }
        createScoredRun(higher.applicationId(), new BigDecimal("90.000"), new BigDecimal("5.00"));
        // Hai don co ban chup khac nhau, mot don chua chup.
        captureYesNo(active.applicationId(), true);
        captureYesNo(notGiven.applicationId(), false);
        List<UUID> ids = List.of(active.applicationId(), notGiven.applicationId(), revoked.applicationId(),
                higher.applicationId());
        // L15 - entity dung du lieu (ScoringRun voi Instant.now() do phan giai 100 ns) con trong persistence context; cau
        // UPDATE rut dong y (clearAutomatically) se xoa context, lan quan sat sau doc lai tu DB (do phan giai micro giay).
        // Flush + clear TRUOC lan quan sat dau de ca hai lan cung doc tu DB - chi so sanh dung du lieu da luu.
        entityManager.flush();
        entityManager.clear();

        Observed before = observe(token, jobId, ids);

        JsonNode reference = before.listItems().get(notGiven.applicationId().toString());
        for (Created tied : List.of(active, revoked)) {
            JsonNode item = before.listItems().get(tied.applicationId().toString());
            assertThat(item.get("totalScore").decimalValue())
                    .isEqualByComparingTo(reference.get("totalScore").decimalValue());
            assertThat(item.get("criterionScores")).isEqualTo(reference.get("criterionScores"));
            assertThat(item.get("rank").asInt()).isEqualTo(2);
            assertThat(item.get("status").asString()).isEqualTo(reference.get("status").asString()).isEqualTo("PENDING");
            JsonNode e3 = JSON.readTree(before.scores().get(tied.applicationId().toString()));
            assertThat(e3.get("rank").asInt()).isEqualTo(2);
            assertThat(e3.get("totalScore").decimalValue())
                    .isEqualByComparingTo(reference.get("totalScore").decimalValue());
        }
        assertThat(reference.get("rank").asInt()).isEqualTo(2);
        assertThat(before.listItems().get(higher.applicationId().toString()).get("rank").asInt()).isEqualTo(1);

        // Rut dong y don ACTIVE bang chinh service F4 -> goi lai, moi dong va E3 y het.
        applicationService.revokeTalentPoolConsent(active.candidateId(), active.applicationId());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT talent_pool_consent FROM job_applications WHERE id = ?",
                        Boolean.class,
                        active.applicationId()))
                .isFalse();

        Observed after = observe(token, jobId, ids);

        for (UUID id : ids) {
            assertThat(after.listItems().get(id.toString()))
                    .as("dong danh sach cua don %s", id)
                    .isEqualTo(before.listItems().get(id.toString()));
            assertThat(after.scores().get(id.toString()))
                    .as("E3 cua don %s", id)
                    .isEqualTo(before.scores().get(id.toString()));
        }
    }
}
