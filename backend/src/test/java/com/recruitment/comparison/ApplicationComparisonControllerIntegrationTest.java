package com.recruitment.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoreExplanation;
import com.recruitment.scoring.ScoreExplanationAttemptRepository;
import com.recruitment.scoring.ScoreExplanationRepository;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H14 C1 (GET /api/hr/jobs/{jobId}/applications/compare) - T1-T11, T15-T27, T30-T35 (REQUIREMENT muc 7.1).
// @Import LlmTestConfiguration: dung CHINH mock ChatModel/EmbeddingModel default-answer throw cua suite (T27) - cung bo
// cau hinh voi CvQaIntegrationTestSupport/JobDraftIntegrationTestSupport nen dung lai Spring context da cache, khong tao
// context moi. @Transactional cap lop: moi @Test rollback rieng; khong khang dinh thu tu thoi gian giua cac ban ghi tao
// trong cung test - ca "luot moi hon" dung backdate tuong minh (CLAUDE.md muc 3c). T30 chay NGOAI transaction test de dem
// dung so cau lenh cua mot request that. Luot cham, diem, giai thich, ban trich xuat ghi thang xuong DB (khong LLM);
// ban chup sang loc ghi bang JdbcTemplate (entity @Immutable). Helper chep theo khuon ApplicationOwnerControllerIntegrationTest
// (du an khong tach tien ich test dung chung).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationComparisonControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String MALFORMED = "Danh sách đơn cần so sánh không hợp lệ.";
    private static final String DUPLICATE = "Mỗi đơn chỉ được chọn một lần.";
    private static final String WRONG_COUNT = "Chọn từ 2 đến 3 đơn để so sánh.";
    private static final String NOT_IN_JOB = "Có đơn không thuộc tin tuyển dụng này.";
    private static final String DIFFERENT_CYCLES = "Chỉ so sánh được các đơn cùng một chu kỳ tuyển dụng.";
    private static final String NOT_SCORED =
            "Có đơn chưa có lượt chấm điểm hoàn tất. Chỉ so sánh được các đơn đã chấm xong.";

    private static final UUID CRITERION = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final Instant CAPTURED_AT = Instant.parse("2026-10-05T17:35:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

    @Autowired
    private ScoreExplanationRepository scoreExplanationRepository;

    @Autowired
    private ScoreExplanationAttemptRepository scoreExplanationAttemptRepository;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    // ---- helper HTTP (khuon ApplicationOwnerControllerIntegrationTest) ----

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asString();
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung Test","phone":"0900000000"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Ung Vien Dang Nhap"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private void createCompany(String token) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cong ty %s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    private UUID createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van",
                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title);
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result).get("id").asString());
    }

    private record Fixture(String token, UUID jobId) {
    }

    private Fixture hrWithJob(String prefix) throws Exception {
        String token = registerAndLoginHr(prefix);
        createCompany(token);
        return new Fixture(token, createJob(token, "Kỹ sư DevOps"));
    }

    private MvcResult compare(String token, UUID jobId, String idsParam) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/hr/jobs/" + jobId + "/applications/compare");
        if (idsParam != null) {
            request = request.param("ids", idsParam);
        }
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String ids(UUID... ids) {
        return Arrays.stream(ids).map(UUID::toString).collect(Collectors.joining(","));
    }

    private JsonNode compareOk(Fixture fixture, UUID... ids) throws Exception {
        MvcResult result = compare(fixture.token(), fixture.jobId(), ids(ids));
        assertThat(result.getResponse().getStatus()).as(text(result)).isEqualTo(200);
        return json(result);
    }

    private static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).as(text(result)).isEqualTo(httpStatus);
        JsonNode body = json(result);
        assertThat(body.get("error").asString()).isEqualTo(code);
        assertThat(body.get("message").asString()).isEqualTo(message);
    }

    private static void assertSelectionError(MvcResult result, String message) throws Exception {
        assertError(result, 400, "INVALID_COMPARE_SELECTION", message);
    }

    // ---- helper du lieu ----

    private UUID createApplication(UUID jobId, String candidateName, ApplicationStatus applicationStatus) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        return createApplication(jobId, candidateName, applicationStatus, job.getRecruitmentCycle());
    }

    private UUID createApplication(UUID jobId, String candidateName) {
        return createApplication(jobId, candidateName, ApplicationStatus.PENDING);
    }

    private UUID createApplication(UUID jobId, String candidateName, ApplicationStatus applicationStatus, int cycle) {
        User candidate = new User();
        candidate.setEmail(uniqueEmail("cand"));
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName(candidateName);
        candidate = userRepository.saveAndFlush(candidate);
        Resume resume = createResume(candidate.getId());

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(cycle);
        application.setStatus(applicationStatus);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        return jobApplicationRepository.saveAndFlush(application).getId();
    }

    private Resume createResume(UUID candidateId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.saveAndFlush(resume);
    }

    private JobApplication application(UUID applicationId) {
        return jobApplicationRepository.findById(applicationId).orElseThrow();
    }

    private record Crit(UUID id, String name, String weight, int maxScore, String score) {
    }

    private static Crit crit(UUID id, String name, String weight, String score) {
        return new Crit(id, name, weight, 5, score);
    }

    // Luot DONE ghi thang (khong LLM). Diem 0 -> evidence rong (dung quy uoc FR-H04).
    private ScoringRun doneRun(UUID applicationId, String totalScore, List<Crit> criteria) {
        RubricSnapshot snapshot = new RubricSnapshot("Rubric Test", criteria.stream()
                .map(c -> new RubricSnapshot.CriterionSnapshot(
                        c.id(), c.name(), null, new BigDecimal(c.weight()), c.maxScore(), null))
                .toList());
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(new BigDecimal(totalScore));
        run = scoringRunRepository.saveAndFlush(run);
        for (Crit c : criteria) {
            CriterionScore score = new CriterionScore();
            score.setScoringRunId(run.getId());
            score.setCriterionNameSnapshot(c.name());
            score.setWeightSnapshot(new BigDecimal(c.weight()));
            score.setMaxScoreSnapshot(c.maxScore());
            score.setScore(new BigDecimal(c.score()));
            score.setReasoning("Ly do cua " + c.name());
            score.setEvidence(new BigDecimal(c.score()).signum() == 0
                    ? List.of()
                    : List.of(new EvidenceEntry("Trích dẫn cho " + c.name(), "experience")));
            criterionScoreRepository.saveAndFlush(score);
        }
        return run;
    }

    private ScoringRun doneRun(UUID applicationId, String totalScore) {
        return doneRun(applicationId, totalScore, List.of(crit(CRITERION, "Kinh nghiệm vận hành", "100.00", "4.00")));
    }

    private UUID run(UUID applicationId, ScoringRunStatus runStatus, boolean finished) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(runStatus);
        if (runStatus != ScoringRunStatus.PENDING) {
            run.setStartedAt(Instant.now());
        }
        if (finished) {
            run.setFinishedAt(Instant.now());
        }
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    // Ghi bang JdbcTemplate trong transaction test: flush TRUOC (cau JDBC thay ban ghi JPA dang cho, vd Job tao qua
    // HTTP - FK) va clear SAU (first-level cache chung cua transaction test khong giu entity cu - request C1 doc lai tu
    // DB nhu production).
    private void jdbcWrite(Runnable write) {
        entityManager.flush();
        write.run();
        entityManager.clear();
    }

    private void setRunCreatedAt(UUID runId, Instant createdAt) {
        jdbcWrite(() -> jdbcTemplate.update("UPDATE scoring_runs SET created_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC), runId));
    }

    private void createExplanation(UUID runId) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(runId);
        explanation.setSummary("Tóm tắt của lượt chấm");
        explanation.setStrengths(List.of(new ExplanationPoint("Kinh nghiệm vận hành", "Điểm mạnh giả lập")));
        explanation.setWeaknesses(List.of(new ExplanationPoint("Kinh nghiệm vận hành", "Điểm yếu giả lập")));
        explanation.setMetCriteria(List.of("Kinh nghiệm vận hành"));
        explanation.setMissingCriteria(List.of());
        explanation.setModel("claude-sonnet-4-6");
        explanation.setPromptVersion("score-explanation-v1");
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    private ResumeParsedData saveParsedData(UUID applicationId, List<String> skills,
            List<ResumeParsedPayload.Education> education) {
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(application(applicationId).getResumeId());
        data.setRawText("Noi dung CV gia lap");
        data.setData(new ResumeParsedPayload(null, education, List.of(), skills, List.of(), List.of(), null, null, null));
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v2");
        return resumeParsedDataRepository.saveAndFlush(data);
    }

    private void setExperience(UUID applicationId, Integer months, int counted) {
        UUID resumeId = application(applicationId).getResumeId();
        jdbcWrite(() -> jdbcTemplate.update(
                "UPDATE resume_parsed_data SET experience_months = ?, experience_entries_counted = ?, "
                        + "experience_entries_skipped = 0, experience_computed_at = now() WHERE resume_id = ?",
                months, counted, resumeId));
    }

    private void markCaptured(UUID applicationId) {
        jdbcWrite(() -> jdbcTemplate.update("UPDATE job_applications SET screening_captured_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(CAPTURED_AT, ZoneOffset.UTC), applicationId));
    }

    private UUID insertQuestion(UUID jobId, String type, String text, int displayOrder) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "INSERT INTO job_screening_questions (job_id, question_type, question_text, display_order) "
                        + "VALUES (?, ?, ?, ?) RETURNING id",
                UUID.class, jobId, type, text, displayOrder);
    }

    private void insertAnswer(UUID applicationId, UUID questionId, int displayOrder, String type, String questionText,
            String answerText, Boolean answerYesNo) {
        entityManager.flush();
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers
                    (application_id, question_id, display_order, question_type_snapshot, question_text_snapshot,
                     options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, ?, ?, ?, NULL, false, ?, ?)
                """,
                applicationId, questionId, displayOrder, type, questionText, answerText, answerYesNo);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.isNull() ? null : node.asString()));
        return values;
    }

    private static JsonNode criterionRowByKey(JsonNode body, String key) {
        for (JsonNode row : body.get("criteria")) {
            if (row.get("key").asString().equals(key)) {
                return row;
            }
        }
        throw new AssertionError("Khong co hang tieu chi key=" + key + " trong " + body.get("criteria"));
    }

    // ---- T1, T2: duong thanh cong, thu tu cot ----

    @Test
    void t1_twoScoredApplications_columnsFollowIdsOrderWithHeaderData() throws Exception {
        Fixture fixture = hrWithJob("t1");
        UUID first = createApplication(fixture.jobId(), "Lê Văn Đức");
        ScoringRun firstRun = doneRun(first, "59.500");
        UUID second = createApplication(fixture.jobId(), "Nguyễn Hải Sơn");
        ScoringRun secondRun = doneRun(second, "81.000");

        JsonNode body = compareOk(fixture, first, second);

        assertThat(body.get("jobId").asString()).isEqualTo(fixture.jobId().toString());
        assertThat(body.get("jobTitle").asString()).isEqualTo("Kỹ sư DevOps");
        assertThat(body.get("recruitmentCycle").asInt()).isEqualTo(1);
        assertThat(body.get("rubricVersionsDiffer").asBoolean()).isFalse();
        JsonNode columns = body.get("columns");
        assertThat(columns.size()).isEqualTo(2);
        assertThat(columns.get(0).get("applicationId").asString()).isEqualTo(first.toString());
        assertThat(columns.get(0).get("candidateName").asString()).isEqualTo("Lê Văn Đức");
        assertThat(columns.get(0).get("status").asString()).isEqualTo("PENDING");
        assertThat(columns.get(0).get("appliedAt").isNull()).isFalse();
        assertThat(columns.get(0).get("scoringRunId").asString()).isEqualTo(firstRun.getId().toString());
        assertThat(Instant.parse(columns.get(0).get("scoredAt").asString()))
                .isEqualTo(scoringRunRepository.findById(firstRun.getId()).orElseThrow().getFinishedAt());
        assertThat(columns.get(1).get("scoringRunId").asString()).isEqualTo(secondRun.getId().toString());

        // Dao thu tu ids -> cot dao theo (R-O1), khong sap theo diem.
        JsonNode reversed = compareOk(fixture, second, first);
        assertThat(reversed.get("columns").get(0).get("applicationId").asString()).isEqualTo(second.toString());
        assertThat(reversed.get("columns").get(1).get("applicationId").asString()).isEqualTo(first.toString());
        assertThat(reversed.get("totalScore").get("values").get(0).decimalValue()).isEqualByComparingTo("81.000");
        assertThat(reversed.get("totalScore").get("values").get(1).decimalValue()).isEqualByComparingTo("59.500");
    }

    @Test
    void t2_threeApplications_returnThreeColumns() throws Exception {
        Fixture fixture = hrWithJob("t2");
        UUID a = createApplication(fixture.jobId(), "Ứng viên A");
        doneRun(a, "70.000");
        UUID b = createApplication(fixture.jobId(), "Ứng viên B");
        doneRun(b, "60.000");
        UUID c = createApplication(fixture.jobId(), "Ứng viên C");
        doneRun(c, "50.000");

        JsonNode body = compareOk(fixture, a, b, c);

        assertThat(body.get("columns").size()).isEqualTo(3);
        assertThat(body.get("profiles").size()).isEqualTo(3);
        assertThat(body.get("explanations").size()).isEqualTo(3);
        assertThat(body.get("screening").get("states").size()).isEqualTo(3);
    }

    // ---- T3-T9: lua chon sai ----

    @Test
    void t3_countBoundaries_andMissingOrEmptyIds() throws Exception {
        Fixture fixture = hrWithJob("t3");
        UUID[] apps = new UUID[4];
        for (int i = 0; i < 4; i++) {
            apps[i] = createApplication(fixture.jobId(), "Ứng viên " + i);
            doneRun(apps[i], "50.000");
        }

        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(apps[0])), WRONG_COUNT);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(apps)), WRONG_COUNT);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), ""), MALFORMED);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), null), MALFORMED);
    }

    @Test
    void t4_malformedIds_returnProjectErrorResponse() throws Exception {
        Fixture fixture = hrWithJob("t4");
        UUID a = createApplication(fixture.jobId(), "A");
        UUID b = createApplication(fixture.jobId(), "B");

        assertSelectionError(compare(fixture.token(), fixture.jobId(), "abc," + a), MALFORMED);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), a + ",," + b), MALFORMED);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), a + ","), MALFORMED);
    }

    @Test
    void t5_duplicateIds_rejected() throws Exception {
        Fixture fixture = hrWithJob("t5");
        UUID a = createApplication(fixture.jobId(), "A");
        UUID b = createApplication(fixture.jobId(), "B");

        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(a, a)), DUPLICATE);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(a, b, a)), DUPLICATE);
    }

    // R-S3: khong ton tai / Job khac cung cong ty / cong ty khac -> CUNG mot body (so nguyen van).
    @Test
    void t6_applicationsOutsideJob_allReturnIdenticalBody() throws Exception {
        Fixture fixture = hrWithJob("t6");
        UUID own = createApplication(fixture.jobId(), "Ứng viên của tin");
        doneRun(own, "60.000");
        UUID otherJob = createJob(fixture.token(), "Tin khác cùng công ty");
        UUID sameCompanyOtherJob = createApplication(otherJob, "Ứng viên tin khác");
        doneRun(sameCompanyOtherJob, "60.000");
        Fixture otherCompany = hrWithJob("t6-other");
        UUID otherCompanyApp = createApplication(otherCompany.jobId(), "Ứng viên công ty khác");
        doneRun(otherCompanyApp, "60.000");

        MvcResult missing = compare(fixture.token(), fixture.jobId(), ids(own, UUID.randomUUID()));
        MvcResult otherJobResult = compare(fixture.token(), fixture.jobId(), ids(own, sameCompanyOtherJob));
        MvcResult otherCompanyResult = compare(fixture.token(), fixture.jobId(), ids(own, otherCompanyApp));

        assertSelectionError(missing, NOT_IN_JOB);
        assertThat(otherJobResult.getResponse().getStatus()).isEqualTo(400);
        assertThat(otherCompanyResult.getResponse().getStatus()).isEqualTo(400);
        assertThat(text(otherJobResult)).isEqualTo(text(missing));
        assertThat(text(otherCompanyResult)).isEqualTo(text(missing));
    }

    @Test
    void t7_differentRecruitmentCycles_rejected() throws Exception {
        Fixture fixture = hrWithJob("t7");
        UUID cycleOne = createApplication(fixture.jobId(), "Chu kỳ 1", ApplicationStatus.PENDING, 1);
        doneRun(cycleOne, "60.000");
        UUID cycleTwo = createApplication(fixture.jobId(), "Chu kỳ 2", ApplicationStatus.PENDING, 2);
        doneRun(cycleTwo, "70.000");

        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(cycleOne, cycleTwo)), DIFFERENT_CYCLES);
    }

    @Test
    void t8_applicationWithoutDoneRun_returns409() throws Exception {
        Fixture fixture = hrWithJob("t8");
        UUID scored = createApplication(fixture.jobId(), "Đã chấm");
        doneRun(scored, "60.000");
        UUID onlyFailed = createApplication(fixture.jobId(), "Chỉ FAILED");
        run(onlyFailed, ScoringRunStatus.FAILED, true);
        UUID onlyPending = createApplication(fixture.jobId(), "Chỉ PENDING");
        run(onlyPending, ScoringRunStatus.PENDING, false);
        UUID noRun = createApplication(fixture.jobId(), "Chưa chấm");
        UUID waitingAggregation = createApplication(fixture.jobId(), "Chờ D3");
        run(waitingAggregation, ScoringRunStatus.RUNNING, true);

        for (UUID unscored : List.of(onlyFailed, onlyPending, noRun, waitingAggregation)) {
            assertError(compare(fixture.token(), fixture.jobId(), ids(scored, unscored)),
                    409, "COMPARE_APPLICATION_NOT_SCORED", NOT_SCORED);
        }
    }

    // R-S7: loi dau tien theo thu tu kiem.
    @Test
    void t9_checkOrder_firstFailingRuleWins() throws Exception {
        Fixture fixture = hrWithJob("t9");
        UUID a = createApplication(fixture.jobId(), "A");
        doneRun(a, "60.000");
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "60.000");
        UUID c = createApplication(fixture.jobId(), "C");
        doneRun(c, "60.000");
        UUID otherJobApp = createApplication(createJob(fixture.token(), "Tin khác"), "Khác tin");
        UUID otherCycleUnscored = createApplication(fixture.jobId(), "Khác chu kỳ", ApplicationStatus.PENDING, 2);

        // 4 phan tu + mot don khac tin -> cau so luong (R-S1 truoc R-S3).
        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(a, b, c, otherJobApp)), WRONG_COUNT);
        // Khac chu ky + chua cham -> cau chu ky (R-S4 truoc R-S5).
        assertSelectionError(compare(fixture.token(), fixture.jobId(), ids(a, otherCycleUnscored)), DIFFERENT_CYCLES);
        // Dinh dang sai + khac tin -> cau dinh dang (R-S2 truoc R-S3).
        assertSelectionError(compare(fixture.token(), fixture.jobId(), "xyz," + otherJobApp), MALFORMED);
    }

    // ---- T10: quyen ----

    @Test
    void t10_permissions() throws Exception {
        Fixture fixture = hrWithJob("t10");
        UUID a = createApplication(fixture.jobId(), "A");
        doneRun(a, "60.000");
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "60.000");
        String validIds = ids(a, b);

        Fixture otherHr = hrWithJob("t10-other");
        assertThat(compare(otherHr.token(), fixture.jobId(), validIds).getResponse().getStatus()).isEqualTo(403);

        MvcResult missingJob = compare(fixture.token(), UUID.randomUUID(), validIds);
        assertThat(missingJob.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(missingJob).get("error").asString()).isEqualTo("JOB_NOT_FOUND");

        String hrWithoutCompany = registerAndLoginHr("t10-nocompany");
        MvcResult noCompany = compare(hrWithoutCompany, fixture.jobId(), validIds);
        assertThat(noCompany.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(noCompany).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
        MvcResult noCompanyMissingJob = compare(hrWithoutCompany, UUID.randomUUID(), validIds);
        assertThat(json(noCompanyMissingJob).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");

        String candidateToken = registerAndLoginCandidate("t10-cand");
        assertThat(compare(candidateToken, fixture.jobId(), validIds).getResponse().getStatus()).isEqualTo(403);
        assertThat(compare(null, fixture.jobId(), validIds).getResponse().getStatus()).isEqualTo(401);
        // Quyen kiem TRUOC lua chon: HR cong ty khac voi ids sai van 403, khong lo cau loi lua chon.
        assertThat(compare(otherHr.token(), fixture.jobId(), "abc").getResponse().getStatus()).isEqualTo(403);
    }

    // ---- T11: moi trang thai don deu so sanh duoc ----

    @Test
    void t11_allApplicationStatusesAreComparable() throws Exception {
        Fixture fixture = hrWithJob("t11");
        UUID pending = createApplication(fixture.jobId(), "Chờ duyệt");
        doneRun(pending, "60.000");
        for (ApplicationStatus other : List.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.REJECTED,
                ApplicationStatus.HIRED, ApplicationStatus.INTERVIEW_INVITED)) {
            UUID app = createApplication(fixture.jobId(), "Đơn " + other, other);
            doneRun(app, "50.000");

            JsonNode columns = compareOk(fixture, pending, app).get("columns");

            assertThat(columns.get(0).get("status").asString()).isEqualTo("PENDING");
            assertThat(columns.get(1).get("status").asString()).isEqualTo(other.name());
        }
    }

    // ---- T12-T14 (phan C1) ----

    @Test
    void t12_rankEqualsListRank_notRecomputedWithinGroup() throws Exception {
        Fixture fixture = hrWithJob("t12");
        UUID app80 = createApplication(fixture.jobId(), "80");
        doneRun(app80, "80.000");
        UUID app70a = createApplication(fixture.jobId(), "70a");
        doneRun(app70a, "70.000");
        UUID app70b = createApplication(fixture.jobId(), "70b");
        doneRun(app70b, "70.000");
        UUID app60 = createApplication(fixture.jobId(), "60");
        doneRun(app60, "60.000");
        createApplication(fixture.jobId(), "Chưa chấm");

        JsonNode body = compareOk(fixture, app70b, app60);
        MvcResult list = mockMvc.perform(get("/api/hr/jobs/" + fixture.jobId() + "/applications")
                        .header("Authorization", "Bearer " + fixture.token()))
                .andReturn();
        Map<String, Integer> listRank = new HashMap<>();
        json(list).forEach(item -> listRank.put(item.get("id").asString(), item.get("rank").asInt()));

        assertThat(body.get("rank").get("values").get(0).asInt()).isEqualTo(2).isEqualTo(listRank.get(app70b.toString()));
        assertThat(body.get("rank").get("values").get(1).asInt()).isEqualTo(4).isEqualTo(listRank.get(app60.toString()));
    }

    @Test
    void t13_rankComputedOverWholeJobIncludingOtherCycles() throws Exception {
        Fixture fixture = hrWithJob("t13");
        UUID cycleOne = createApplication(fixture.jobId(), "C1", ApplicationStatus.PENDING, 1);
        doneRun(cycleOne, "90.000");
        UUID cycleTwo80 = createApplication(fixture.jobId(), "C2-80", ApplicationStatus.PENDING, 2);
        doneRun(cycleTwo80, "80.000");
        UUID cycleTwo70 = createApplication(fixture.jobId(), "C2-70", ApplicationStatus.PENDING, 2);
        doneRun(cycleTwo70, "70.000");

        JsonNode body = compareOk(fixture, cycleTwo80, cycleTwo70);

        assertThat(body.get("rank").get("values").get(0).asInt()).isEqualTo(2);
        assertThat(body.get("rank").get("values").get(1).asInt()).isEqualTo(3);
        assertThat(body.get("recruitmentCycle").asInt()).isEqualTo(2);
    }

    @Test
    void t14_newerDoneRunUsedEverywhere_andNewerFailedRunIgnored() throws Exception {
        Fixture fixture = hrWithJob("t14");
        Instant now = Instant.now();
        UUID twoDone = createApplication(fixture.jobId(), "Hai lượt DONE");
        ScoringRun oldRun = doneRun(twoDone, "50.000", List.of(crit(CRITERION, "Tiêu chí", "100.00", "2.50")));
        ScoringRun newRun = doneRun(twoDone, "70.000", List.of(crit(CRITERION, "Tiêu chí", "100.00", "3.50")));
        createExplanation(newRun.getId());
        setRunCreatedAt(oldRun.getId(), now.minusSeconds(3600));
        setRunCreatedAt(newRun.getId(), now.minusSeconds(60));
        UUID doneThenFailed = createApplication(fixture.jobId(), "DONE rồi FAILED");
        ScoringRun done = doneRun(doneThenFailed, "60.000");
        UUID failed = run(doneThenFailed, ScoringRunStatus.FAILED, true);
        setRunCreatedAt(done.getId(), now.minusSeconds(3600));
        setRunCreatedAt(failed, now.minusSeconds(60));

        JsonNode body = compareOk(fixture, twoDone, doneThenFailed);

        assertThat(body.get("columns").get(0).get("scoringRunId").asString()).isEqualTo(newRun.getId().toString());
        assertThat(body.get("totalScore").get("values").get(0).decimalValue()).isEqualByComparingTo("70.000");
        assertThat(body.get("criteria").get(0).get("cells").get(0).get("score").decimalValue()).isEqualByComparingTo("3.50");
        assertThat(body.get("explanations").get(0).get("summary").asString()).isEqualTo("Tóm tắt của lượt chấm");
        assertThat(body.get("columns").get(1).get("scoringRunId").asString()).isEqualTo(done.getId().toString());
        assertThat(body.get("columns").get(1).get("newerRunInProgress").asBoolean()).isFalse();
    }

    // ---- T15: newerRunInProgress (R-D10) ----

    @Test
    void t15_newerRunInProgressFlag() throws Exception {
        Fixture fixture = hrWithJob("t15");
        Instant now = Instant.now();
        UUID noNewer = createApplication(fixture.jobId(), "Không lượt mới");
        doneRun(noNewer, "60.000");
        Map<String, UUID> cases = Map.of(
                "PENDING", createApplication(fixture.jobId(), "Mới PENDING"),
                "RUNNING", createApplication(fixture.jobId(), "Mới RUNNING"),
                "RUNNING_FINISHED", createApplication(fixture.jobId(), "Mới chờ D3"));
        for (Map.Entry<String, UUID> entry : cases.entrySet()) {
            ScoringRun done = doneRun(entry.getValue(), "60.000");
            UUID newer = switch (entry.getKey()) {
                case "PENDING" -> run(entry.getValue(), ScoringRunStatus.PENDING, false);
                case "RUNNING" -> run(entry.getValue(), ScoringRunStatus.RUNNING, false);
                default -> run(entry.getValue(), ScoringRunStatus.RUNNING, true);
            };
            setRunCreatedAt(done.getId(), now.minusSeconds(3600));
            setRunCreatedAt(newer, now.minusSeconds(60));

            JsonNode columns = compareOk(fixture, noNewer, entry.getValue()).get("columns");

            assertThat(columns.get(0).get("newerRunInProgress").asBoolean()).isFalse();
            assertThat(columns.get(1).get("newerRunInProgress").asBoolean()).as(entry.getKey()).isTrue();
            assertThat(columns.get(1).get("scoringRunId").asString()).isEqualTo(done.getId().toString());
        }
    }

    // ---- T16-T19: ghep tieu chi (R-C) ----

    @Test
    void t16_criteriaMatchedByCriterionIdNotPosition_orderFromFirstColumn() throws Exception {
        Fixture fixture = hrWithJob("t16");
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID first = createApplication(fixture.jobId(), "Cột 1");
        doneRun(first, "70.000", List.of(crit(idA, "A", "60.00", "4.00"), crit(idB, "B", "40.00", "2.00")));
        UUID second = createApplication(fixture.jobId(), "Cột 2");
        doneRun(second, "70.000", List.of(crit(idB, "B", "40.00", "5.00"), crit(idA, "A", "60.00", "1.00")));

        JsonNode criteria = compareOk(fixture, first, second).get("criteria");

        assertThat(criteria.size()).isEqualTo(2);
        assertThat(criteria.get(0).get("key").asString()).isEqualTo(idA.toString());
        assertThat(criteria.get(0).get("cells").get(0).get("score").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(criteria.get(0).get("cells").get(1).get("score").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(criteria.get(1).get("key").asString()).isEqualTo(idB.toString());
        assertThat(criteria.get(1).get("cells").get(1).get("score").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(criteria.get(0).get("rubricVersionsDiffer").asBoolean()).isFalse();
    }

    @Test
    void t17_missingAndAddedCriteria_keepRowsWithNullCells() throws Exception {
        Fixture fixture = hrWithJob("t17");
        UUID shared = UUID.randomUUID();
        UUID onlyFirst = UUID.randomUUID();
        UUID onlySecond = UUID.randomUUID();
        UUID first = createApplication(fixture.jobId(), "Cột 1");
        doneRun(first, "70.000", List.of(crit(shared, "Chung", "50.00", "3.00"), crit(onlyFirst, "Cũ", "50.00", "3.00")));
        UUID second = createApplication(fixture.jobId(), "Cột 2");
        doneRun(second, "70.000", List.of(crit(shared, "Chung", "50.00", "3.00"), crit(onlySecond, "Mới", "50.00", "3.00")));

        JsonNode body = compareOk(fixture, first, second);
        JsonNode criteria = body.get("criteria");

        assertThat(criteria.size()).isEqualTo(3);
        JsonNode missingInSecond = criterionRowByKey(body, onlyFirst.toString());
        assertThat(missingInSecond.get("cells").get(1).isNull()).isTrue();
        assertThat(missingInSecond.get("rubricVersionsDiffer").asBoolean()).isTrue();
        assertThat(criteria.get(2).get("key").asString()).isEqualTo(onlySecond.toString());   // nam cuoi (R-C3)
        assertThat(criteria.get(2).get("cells").get(0).isNull()).isTrue();
        assertThat(criteria.get(2).get("rubricVersionsDiffer").asBoolean()).isTrue();
        assertThat(criterionRowByKey(body, shared.toString()).get("rubricVersionsDiffer").asBoolean()).isFalse();
        assertThat(body.get("rubricVersionsDiffer").asBoolean()).isTrue();
    }

    @Test
    void t18_sameCriterionIdDifferentNameAndWeight_oneRowEachCellOwnValues() throws Exception {
        Fixture fixture = hrWithJob("t18");
        UUID id = UUID.randomUUID();
        UUID first = createApplication(fixture.jobId(), "Cột 1");
        doneRun(first, "70.000", List.of(crit(id, "Kỹ năng mềm & giao tiếp", "15.00", "4.00")));
        UUID second = createApplication(fixture.jobId(), "Cột 2");
        doneRun(second, "70.000", List.of(crit(id, "Kỹ năng giao tiếp & phối hợp", "10.00", "4.00")));

        JsonNode body = compareOk(fixture, first, second);
        JsonNode row = body.get("criteria").get(0);

        assertThat(body.get("criteria").size()).isEqualTo(1);
        assertThat(row.get("criterionName").asString()).isEqualTo("Kỹ năng mềm & giao tiếp");
        assertThat(row.get("cells").get(0).get("weight").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(row.get("cells").get(1).get("criterionName").asString()).isEqualTo("Kỹ năng giao tiếp & phối hợp");
        assertThat(row.get("cells").get(1).get("weight").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(row.get("rubricVersionsDiffer").asBoolean()).isTrue();
        assertThat(body.get("rubricVersionsDiffer").asBoolean()).isTrue();
    }

    @Test
    void t18_maxScoreDiffers_rowFlagged_andIdenticalCriteria_notFlagged() throws Exception {
        Fixture fixture = hrWithJob("t18b");
        UUID id = UUID.randomUUID();
        UUID first = createApplication(fixture.jobId(), "Cột 1");
        doneRun(first, "70.000", List.of(new Crit(id, "Tiêu chí", "100.00", 5, "4.00")));
        UUID second = createApplication(fixture.jobId(), "Cột 2");
        doneRun(second, "70.000", List.of(new Crit(id, "Tiêu chí", "100.00", 10, "4.00")));
        UUID third = createApplication(fixture.jobId(), "Cột 3");
        doneRun(third, "70.000", List.of(new Crit(id, "Tiêu chí", "100.00", 5, "4.00")));

        assertThat(compareOk(fixture, first, second).get("criteria").get(0).get("rubricVersionsDiffer").asBoolean())
                .isTrue();
        JsonNode same = compareOk(fixture, first, third);
        assertThat(same.get("criteria").get(0).get("rubricVersionsDiffer").asBoolean()).isFalse();
        assertThat(same.get("rubricVersionsDiffer").asBoolean()).isFalse();
    }

    @Test
    void t19_nullCriterionId_fallsBackToNameKey() throws Exception {
        Fixture fixture = hrWithJob("t19");
        UUID first = createApplication(fixture.jobId(), "Cột 1");
        doneRun(first, "70.000", List.of(crit(null, "Không id", "100.00", "3.00")));
        UUID second = createApplication(fixture.jobId(), "Cột 2");
        doneRun(second, "70.000", List.of(crit(null, "Không id", "100.00", "4.00")));
        UUID third = createApplication(fixture.jobId(), "Cột 3");
        UUID id = UUID.randomUUID();
        doneRun(third, "70.000", List.of(crit(id, "Không id", "100.00", "4.00")));

        JsonNode bothNull = compareOk(fixture, first, second).get("criteria");
        assertThat(bothNull.size()).isEqualTo(1);
        assertThat(bothNull.get(0).get("key").asString()).isEqualTo("name:Không id");

        JsonNode oneNull = compareOk(fixture, first, third).get("criteria");
        assertThat(oneNull.size()).isEqualTo(2);
        assertThat(oneNull.get(0).get("key").asString()).isEqualTo("name:Không id");
        assertThat(oneNull.get(1).get("key").asString()).isEqualTo(id.toString());
    }

    // ---- T20: valuesDiffer (R-W2), bien ----

    @Test
    void t20_valuesDifferBoundaries() throws Exception {
        Fixture fixture = hrWithJob("t20");
        UUID a = createApplication(fixture.jobId(), "A");
        doneRun(a, "70.000", List.of(crit(CRITERION, "T", "100.00", "3.00")));
        saveParsedData(a, List.of(), List.of());
        setExperience(a, 24, 1);
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "70.000", List.of(crit(CRITERION, "T", "100.00", "3.00")));
        saveParsedData(b, List.of(), List.of());
        setExperience(b, 24, 1);
        UUID c = createApplication(fixture.jobId(), "C");
        doneRun(c, "70.500", List.of(crit(CRITERION, "T", "100.00", "4.00")));
        saveParsedData(c, List.of(), List.of());
        setExperience(c, null, 0);

        JsonNode same = compareOk(fixture, a, b);
        assertThat(same.get("totalScore").get("valuesDiffer").asBoolean()).isFalse();
        assertThat(same.get("criteria").get(0).get("valuesDiffer").asBoolean()).isFalse();
        assertThat(same.get("experience").get("valuesDiffer").asBoolean()).isFalse();

        JsonNode differ = compareOk(fixture, a, c);
        assertThat(differ.get("totalScore").get("valuesDiffer").asBoolean()).isTrue();
        assertThat(differ.get("criteria").get(0).get("valuesDiffer").asBoolean()).isTrue();
        assertThat(differ.get("experience").get("valuesDiffer").asBoolean()).isTrue();   // 24 va null
    }

    // ---- T21-T22: ho so tu CV ----

    @Test
    void t21_experienceStates_andMissingParsedDataStillReturns200() throws Exception {
        Fixture fixture = hrWithJob("t21");
        UUID computed = createApplication(fixture.jobId(), "30 tháng");
        doneRun(computed, "60.000");
        saveParsedData(computed, List.of("Docker"), List.of());
        setExperience(computed, 30, 2);
        UUID noMonths = createApplication(fixture.jobId(), "Không mốc");
        doneRun(noMonths, "60.000");
        saveParsedData(noMonths, List.of(), List.of());
        setExperience(noMonths, null, 0);
        UUID notComputed = createApplication(fixture.jobId(), "Chưa tính");
        doneRun(notComputed, "60.000");
        saveParsedData(notComputed, List.of(), List.of());
        UUID noParsedData = createApplication(fixture.jobId(), "Không trích xuất");
        doneRun(noParsedData, "60.000");

        JsonNode first = compareOk(fixture, computed, noMonths, notComputed);
        JsonNode values = first.get("experience").get("values");
        assertThat(values.get(0).get("months").asInt()).isEqualTo(30);
        assertThat(values.get(0).get("years").decimalValue()).isEqualByComparingTo("2.5");
        for (int i = 1; i < 3; i++) {
            assertThat(values.get(i).get("months").isNull()).isTrue();
            assertThat(values.get(i).get("years").isNull()).isTrue();
        }

        JsonNode missing = compareOk(fixture, computed, noParsedData);
        assertThat(missing.get("experience").get("values").get(1).get("months").isNull()).isTrue();
        assertThat(missing.get("profiles").get(1).get("parsedDataAvailable").asBoolean()).isFalse();
        assertThat(missing.get("profiles").get(0).get("parsedDataAvailable").asBoolean()).isTrue();
    }

    @Test
    void t22_skillsAndEducationVerbatim_noGpa_fromSubmittedResume() throws Exception {
        Fixture fixture = hrWithJob("t22");
        UUID a = createApplication(fixture.jobId(), "A");
        doneRun(a, "60.000");
        saveParsedData(a, List.of("Kubernetes", "Quản trị hệ thống", "AWS"), List.of(
                new ResumeParsedPayload.Education("ĐH Bách khoa", "Kỹ sư", null, "2014", "2019", 3.6)));
        // Ung vien doi CV chinh sau khi nop: bang van doc CV DA NOP (resume_id cua don).
        JobApplication application = application(a);
        jdbcWrite(() -> jdbcTemplate.update("UPDATE resumes SET is_primary = false WHERE id = ?", application.getResumeId()));
        Resume newPrimary = createResume(application.getCandidateId());
        ResumeParsedData newData = new ResumeParsedData();
        newData.setResumeId(newPrimary.getId());
        newData.setRawText("CV moi");
        newData.setData(new ResumeParsedPayload(null, List.of(), List.of(), List.of("Kỹ năng CV mới"), List.of(),
                List.of(), null, null, null));
        newData.setModel("claude-sonnet-4-6");
        newData.setPromptVersion("resume-parse-v2");
        resumeParsedDataRepository.saveAndFlush(newData);
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "60.000");

        JsonNode body = compareOk(fixture, a, b);
        JsonNode profile = body.get("profiles").get(0);

        assertThat(textValues(profile.get("skills"))).containsExactly("Kubernetes", "Quản trị hệ thống", "AWS");
        JsonNode education = profile.get("education").get(0);
        assertThat(education.get("school").asString()).isEqualTo("ĐH Bách khoa");
        assertThat(education.get("degree").asString()).isEqualTo("Kỹ sư");
        assertThat(education.get("major").isNull()).isTrue();
        assertThat(education.has("gpa")).isFalse();
    }

    // ---- T23: giai thich (R-D8, R-W4), bien so lan thu ----

    @Test
    void t23_explanationAndPendingFailedBoundaries() throws Exception {
        Fixture fixture = hrWithJob("t23");
        UUID withExplanation = createApplication(fixture.jobId(), "Có giải thích");
        createExplanation(doneRun(withExplanation, "60.000").getId());
        Map<Integer, UUID> byAttempts = new LinkedHashMap<>();
        for (int attempts : new int[] {0, 2, 3, 4}) {
            UUID app = createApplication(fixture.jobId(), attempts + " lần thử");
            UUID runId = doneRun(app, "60.000").getId();
            for (int i = 0; i < attempts; i++) {
                scoreExplanationAttemptRepository.recordFailedAttempt(runId, "LLM_ERROR: loi gia lap trong test");
            }
            byAttempts.put(attempts, app);
        }

        JsonNode explained = compareOk(fixture, withExplanation, byAttempts.get(0)).get("explanations");
        assertThat(explained.get(0).get("summary").asString()).isEqualTo("Tóm tắt của lượt chấm");
        assertThat(explained.get(0).get("strengths").get(0).get("criterionName").asString())
                .isEqualTo("Kinh nghiệm vận hành");
        assertThat(explained.get(0).get("weaknesses").get(0).get("point").asString()).isEqualTo("Điểm yếu giả lập");
        assertThat(explained.get(0).get("status").isNull()).isTrue();
        assertThat(explained.get(0).has("metCriteria")).isFalse();
        assertThat(explained.get(0).has("missingCriteria")).isFalse();
        assertThat(explained.get(1).get("status").asString()).isEqualTo("PENDING");

        // Nguong max-attempts = 3: 2 (nguong-1) PENDING, 3 (dung nguong) FAILED, 4 (nguong+1) FAILED.
        JsonNode boundary = compareOk(fixture, byAttempts.get(2), byAttempts.get(3), byAttempts.get(4))
                .get("explanations");
        assertThat(boundary.get(0).get("status").asString()).isEqualTo("PENDING");
        assertThat(boundary.get(1).get("status").asString()).isEqualTo("FAILED");
        assertThat(boundary.get(2).get("status").asString()).isEqualTo("FAILED");
    }

    // ---- T24-T25: sang loc (R-Q) ----

    @Test
    void t24_screeningRowsMatchedByQuestionIdAndContent() throws Exception {
        Fixture fixture = hrWithJob("t24");
        UUID q1 = insertQuestion(fixture.jobId(), "YES_NO", "Bắt đầu trong 2 tuần?", 1);
        UUID q2 = insertQuestion(fixture.jobId(), "SHORT_TEXT", "Mức lương mong muốn?", 2);
        UUID first = createApplication(fixture.jobId(), "Nộp trước");
        doneRun(first, "60.000");
        markCaptured(first);
        insertAnswer(first, q1, 1, "YES_NO", "Bắt đầu trong 2 tuần?", null, true);
        insertAnswer(first, q2, 2, "SHORT_TEXT", "Mức lương mong muốn?", "25 triệu", null);
        insertAnswer(first, null, 3, "SHORT_TEXT", "Câu đã xoá", "Trả lời câu đã xoá", null);
        UUID second = createApplication(fixture.jobId(), "Nộp sau");
        doneRun(second, "60.000");
        markCaptured(second);
        // HR doi thu tu (q1 sang vi tri 2) va sua noi dung q2 giua hai lan nop.
        insertAnswer(second, q2, 1, "SHORT_TEXT", "Mức lương mong muốn (triệu đồng)?", "30", null);
        insertAnswer(second, q1, 2, "YES_NO", "Bắt đầu trong 2 tuần?", null, false);

        JsonNode rows = compareOk(fixture, first, second).get("screening").get("rows");

        assertThat(rows.size()).isEqualTo(4);
        List<String> questionTexts = new ArrayList<>();
        rows.forEach(row -> questionTexts.add(row.get("questionText").asString()));
        // Thu tu: display_order cot 1, roi hang chi co o cot 2 (R-Q3).
        assertThat(questionTexts)
                .containsExactly("Bắt đầu trong 2 tuần?", "Mức lương mong muốn?", "Câu đã xoá",
                        "Mức lương mong muốn (triệu đồng)?");
        // q1 ghep du hai cot du display_order khac nhau.
        assertThat(rows.get(0).get("cells").get(0).get("answerYesNo").asBoolean()).isTrue();
        assertThat(rows.get(0).get("cells").get(1).get("answerYesNo").asBoolean()).isFalse();
        // q2 noi dung khac -> hai hang rieng, o thieu NOT_ASKED.
        assertThat(rows.get(1).get("cells").get(1).get("state").asString()).isEqualTo("NOT_ASKED");
        assertThat(rows.get(3).get("cells").get(0).get("state").asString()).isEqualTo("NOT_ASKED");
        assertThat(rows.get(3).get("cells").get(1).get("answerText").asString()).isEqualTo("30");
        // question_id null -> hang rieng cua don do.
        assertThat(rows.get(2).get("cells").get(1).get("state").asString()).isEqualTo("NOT_ASKED");
    }

    @Test
    void t25_screeningCaptureStatesAndDiffFlags() throws Exception {
        Fixture fixture = hrWithJob("t25");
        UUID q1 = insertQuestion(fixture.jobId(), "YES_NO", "Đi công tác được không?", 1);
        UUID q2 = insertQuestion(fixture.jobId(), "SHORT_TEXT", "Giới thiệu ngắn", 2);
        UUID notCaptured = createApplication(fixture.jobId(), "Nộp trước khi chụp");
        doneRun(notCaptured, "60.000");
        UUID noQuestions = createApplication(fixture.jobId(), "Không câu hỏi");
        doneRun(noQuestions, "60.000");
        markCaptured(noQuestions);
        UUID yes = createApplication(fixture.jobId(), "Trả lời Có");
        doneRun(yes, "60.000");
        markCaptured(yes);
        insertAnswer(yes, q1, 1, "YES_NO", "Đi công tác được không?", null, true);
        insertAnswer(yes, q2, 2, "SHORT_TEXT", "Giới thiệu ngắn", "Xin chào", null);
        UUID no = createApplication(fixture.jobId(), "Trả lời Không");
        doneRun(no, "60.000");
        markCaptured(no);
        insertAnswer(no, q1, 1, "YES_NO", "Đi công tác được không?", null, false);
        insertAnswer(no, q2, 2, "SHORT_TEXT", "Giới thiệu ngắn", "Nội dung khác", null);
        UUID blank = createApplication(fixture.jobId(), "Bỏ trống");
        doneRun(blank, "60.000");
        markCaptured(blank);
        insertAnswer(blank, q1, 1, "YES_NO", "Đi công tác được không?", null, null);
        insertAnswer(blank, q2, 2, "SHORT_TEXT", "Giới thiệu ngắn", null, null);
        UUID notCaptured2 = createApplication(fixture.jobId(), "Nộp trước khi chụp 2");
        doneRun(notCaptured2, "60.000");

        JsonNode mixed = compareOk(fixture, notCaptured, noQuestions, yes).get("screening");
        assertThat(textValues(mixed.get("states"))).containsExactly("NOT_CAPTURED", "NO_QUESTIONS", "CAPTURED");
        assertThat(mixed.get("capturedAt").get(0).isNull()).isTrue();
        assertThat(Instant.parse(mixed.get("capturedAt").get(1).asString())).isEqualTo(CAPTURED_AT);
        JsonNode firstRow = mixed.get("rows").get(0);
        assertThat(firstRow.get("cells").get(0).get("state").asString()).isEqualTo("NOT_CAPTURED");
        assertThat(firstRow.get("cells").get(1).get("state").asString()).isEqualTo("NO_QUESTIONS");
        assertThat(firstRow.get("cells").get(2).get("state").asString()).isEqualTo("ANSWERED");

        JsonNode answered = compareOk(fixture, yes, no, blank).get("screening").get("rows");
        assertThat(answered.get(0).get("valuesDiffer").asBoolean()).isTrue();          // YES_NO Co/Khong
        assertThat(answered.get(1).get("valuesDiffer").asBoolean()).isFalse();         // SHORT_TEXT luon false
        assertThat(answered.get(0).get("cells").get(2).get("state").asString()).isEqualTo("NOT_ANSWERED");

        JsonNode allNotCaptured = compareOk(fixture, notCaptured, notCaptured2).get("screening");
        assertThat(allNotCaptured.get("rows").size()).isZero();                        // R-Q5
    }

    @Test
    void t25_yesNoSameAnswer_notFlagged_andAnsweredVersusNotAnswered_flagged() throws Exception {
        Fixture fixture = hrWithJob("t25b");
        UUID q1 = insertQuestion(fixture.jobId(), "YES_NO", "Làm ca đêm?", 1);
        UUID first = createApplication(fixture.jobId(), "Có 1");
        doneRun(first, "60.000");
        markCaptured(first);
        insertAnswer(first, q1, 1, "YES_NO", "Làm ca đêm?", null, true);
        UUID second = createApplication(fixture.jobId(), "Có 2");
        doneRun(second, "60.000");
        markCaptured(second);
        insertAnswer(second, q1, 1, "YES_NO", "Làm ca đêm?", null, true);
        UUID blank = createApplication(fixture.jobId(), "Trống");
        doneRun(blank, "60.000");
        markCaptured(blank);
        insertAnswer(blank, q1, 1, "YES_NO", "Làm ca đêm?", null, null);

        assertThat(compareOk(fixture, first, second).get("screening").get("rows").get(0).get("valuesDiffer")
                .asBoolean()).isFalse();
        assertThat(compareOk(fixture, first, blank).get("screening").get("rows").get(0).get("valuesDiffer")
                .asBoolean()).isTrue();
    }

    // ---- T26: khong khoa cam o bat ky muc nao cua cay JSON (R-W3) ----

    private static final Set<String> FORBIDDEN_KEYS = Set.of("verdict", "label", "isQualified", "passed",
            "recommendation", "winner", "best", "top", "highlight", "isHighest", "questionId", "rawText", "contact",
            "metCriteria", "missingCriteria", "gpa");

    private static void collectKeys(JsonNode node, Set<String> keys) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                keys.add(field.getKey());
                collectKeys(field.getValue(), keys);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, keys));
        }
    }

    @Test
    void t26_responseTreeHasNoForbiddenKeys() throws Exception {
        Fixture fixture = hrWithJob("t26");
        UUID q1 = insertQuestion(fixture.jobId(), "YES_NO", "Câu hỏi", 1);
        UUID a = createApplication(fixture.jobId(), "A");
        createExplanation(doneRun(a, "60.000").getId());
        saveParsedData(a, List.of("Java"), List.of(new ResumeParsedPayload.Education("Trường", "Cử nhân", "CNTT",
                "2016", "2020", 3.2)));
        setExperience(a, 24, 1);
        markCaptured(a);
        insertAnswer(a, q1, 1, "YES_NO", "Câu hỏi", null, true);
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "60.000");

        Set<String> keys = new HashSet<>();
        collectKeys(compareOk(fixture, a, b), keys);

        assertThat(keys).isNotEmpty().doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }

    // ---- T27: khong goi AI, khong ghi gi (R-A1, R-A2) ----

    @Test
    void t27_compareCallsNoAiAndWritesNothing() throws Exception {
        Fixture fixture = hrWithJob("t27");
        UUID a = createApplication(fixture.jobId(), "A");
        createExplanation(doneRun(a, "60.000").getId());
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "70.000");
        List<String> tables = List.of("scoring_runs", "criterion_scores", "score_explanations", "notifications");
        Map<String, Long> before = tables.stream().collect(Collectors.toMap(table -> table, this::count));
        clearInvocations(chatModel, embeddingModel);

        compareOk(fixture, a, b);

        verifyNoInteractions(chatModel, embeddingModel);
        tables.forEach(table -> assertThat(count(table)).as(table).isEqualTo(before.get(table)));
    }

    // ---- T30: khong N+1 (muc 4.6, S4) ----

    // NGOAI transaction test: request chay nhu production (moi repository mot transaction rieng, khong first-level
    // cache chung voi test), so cau lenh do duoc la so that. Du lieu tao o day duoc commit; moi bang ghi gan voi HR/cong
    // ty/Job rieng (UUID ngau nhien) nen khong anh huong test khac.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void t30_statementCountDoesNotGrowWithApplicationsInJob() throws Exception {
        Fixture fixture = hrWithJob("t30");
        UUID a = createApplication(fixture.jobId(), "A");
        createExplanation(doneRun(a, "60.000").getId());
        saveParsedData(a, List.of("Java"), List.of());
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "70.000");
        UUID c = createApplication(fixture.jobId(), "C");
        doneRun(c, "50.000");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        compareOk(fixture, a, b);
        long n3 = statistics.getPrepareStatementCount();

        for (int i = 0; i < 27; i++) {
            UUID extra = createApplication(fixture.jobId(), "Thêm " + i);
            createExplanation(doneRun(extra, "40.000").getId());
            saveParsedData(extra, List.of("Kỹ năng " + i), List.of());
        }
        statistics.clear();
        compareOk(fixture, a, b);
        long n30 = statistics.getPrepareStatementCount();

        assertThat(n30)
                .as("So cau lenh: Job 3 don = %d, Job 30 don = %d (ky vong thiet ke 13, REQUIREMENT muc 4.6)", n3, n30)
                .isEqualTo(n3);
        System.out.printf("FR-H14 T30: n3=%d, n30=%d%n", n3, n30);
    }

    // ---- T31: ids qua HTTP ----

    @Test
    void t31_idsWhitespaceAndCaseHandling() throws Exception {
        Fixture fixture = hrWithJob("t31");
        UUID a = createApplication(fixture.jobId(), "A");
        doneRun(a, "60.000");
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "70.000");

        MvcResult spaced = compare(fixture.token(), fixture.jobId(), " " + a + " , " + b + " ");
        assertThat(spaced.getResponse().getStatus()).as(text(spaced)).isEqualTo(200);

        String upper = a.toString().toUpperCase(Locale.ROOT);
        String mixed = b.toString().substring(0, 8).toUpperCase(Locale.ROOT) + b.toString().substring(8);
        MvcResult cased = compare(fixture.token(), fixture.jobId(), upper + "," + mixed);
        assertThat(cased.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(cased).get("columns").get(0).get("applicationId").asString()).isEqualTo(a.toString());
        assertThat(json(cased).get("columns").get(1).get("applicationId").asString()).isEqualTo(b.toString());

        assertSelectionError(compare(fixture.token(), fixture.jobId(), a + "," + b + ","), MALFORMED);
        assertSelectionError(compare(fixture.token(), fixture.jobId(), a + "," + upper), DUPLICATE);
    }

    // ---- T32: trang thai Job khong chan so sanh (R-S10) ----

    @Test
    void t32_jobStatusDoesNotBlockComparison_butOwnershipStillDoes() throws Exception {
        Fixture fixture = hrWithJob("t32");
        UUID a = createApplication(fixture.jobId(), "A");
        doneRun(a, "60.000");
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "70.000");
        Fixture otherHr = hrWithJob("t32-other");

        for (String jobStatus : List.of("DRAFT", "PAUSED", "CLOSED")) {
            jdbcWrite(() -> jdbcTemplate.update("UPDATE jobs SET status = ? WHERE id = ?", jobStatus, fixture.jobId()));
            assertThat(compare(fixture.token(), fixture.jobId(), ids(a, b)).getResponse().getStatus())
                    .as(jobStatus).isEqualTo(200);
        }
        assertThat(compare(otherHr.token(), fixture.jobId(), ids(a, b)).getResponse().getStatus()).isEqualTo(403);

        jdbcWrite(() -> jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE id = ?", fixture.jobId()));
        assertThat(compare(fixture.token(), fixture.jobId(), ids(a, b)).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- T33: hoa diem ----

    @Test
    void t33_tiedScores_shareRankAndRowsNotFlagged() throws Exception {
        Fixture fixture = hrWithJob("t33");
        UUID app80 = createApplication(fixture.jobId(), "80");
        doneRun(app80, "80.000");
        UUID app70a = createApplication(fixture.jobId(), "70a");
        doneRun(app70a, "70.000");
        UUID app70b = createApplication(fixture.jobId(), "70b");
        doneRun(app70b, "70.000");
        UUID app60 = createApplication(fixture.jobId(), "60");
        doneRun(app60, "60.000");

        JsonNode tied = compareOk(fixture, app70a, app70b);
        assertThat(tied.get("rank").get("values").get(0).asInt()).isEqualTo(2);
        assertThat(tied.get("rank").get("values").get(1).asInt()).isEqualTo(2);
        assertThat(tied.get("rank").get("valuesDiffer").asBoolean()).isFalse();
        assertThat(tied.get("totalScore").get("valuesDiffer").asBoolean()).isFalse();

        JsonNode differ = compareOk(fixture, app80, app70a);
        assertThat(differ.get("rank").get("values").get(0).asInt()).isEqualTo(1);
        assertThat(differ.get("rank").get("values").get(1).asInt()).isEqualTo(2);
        assertThat(differ.get("rank").get("valuesDiffer").asBoolean()).isTrue();
    }

    // ---- T34: diem 0 ----

    @Test
    void t34_zeroScore_keptWithEmptyEvidence() throws Exception {
        Fixture fixture = hrWithJob("t34");
        UUID zeroA = createApplication(fixture.jobId(), "0a");
        doneRun(zeroA, "0.000", List.of(crit(CRITERION, "T", "100.00", "0.00")));
        UUID zeroB = createApplication(fixture.jobId(), "0b");
        doneRun(zeroB, "0.000", List.of(crit(CRITERION, "T", "100.00", "0.00")));
        UUID one = createApplication(fixture.jobId(), "1");
        doneRun(one, "20.000", List.of(crit(CRITERION, "T", "100.00", "1.00")));

        MvcResult result = compare(fixture.token(), fixture.jobId(), ids(zeroA, zeroB));
        JsonNode row = json(result).get("criteria").get(0);
        assertThat(row.get("cells").get(0).get("score").isNull()).isFalse();
        assertThat(row.get("cells").get(0).get("score").decimalValue()).isEqualByComparingTo("0");
        assertThat(text(result)).contains("\"evidence\":[]");
        assertThat(row.get("valuesDiffer").asBoolean()).isFalse();

        assertThat(compareOk(fixture, zeroA, one).get("criteria").get(0).get("valuesDiffer").asBoolean()).isTrue();
    }

    // ---- T35: trich xuat lai sau khi cham (R-D12) ----

    @Test
    void t35_reparsedDataShownWhileScoresKeepScoringTime() throws Exception {
        Fixture fixture = hrWithJob("t35");
        UUID a = createApplication(fixture.jobId(), "A");
        ScoringRun run = doneRun(a, "60.000");
        ResumeParsedData parsed = saveParsedData(a, List.of("Kỹ năng cũ"), List.of());
        setExperience(a, 12, 1);
        UUID b = createApplication(fixture.jobId(), "B");
        doneRun(b, "70.000");
        JsonNode before = compareOk(fixture, a, b);

        parsed.setData(new ResumeParsedPayload(null, List.of(), List.of(), List.of("Kỹ năng mới"), List.of(), List.of(),
                null, null, null));
        resumeParsedDataRepository.saveAndFlush(parsed);
        setExperience(a, 36, 2);
        JsonNode after = compareOk(fixture, a, b);

        assertThat(textValues(after.get("profiles").get(0).get("skills"))).containsExactly("Kỹ năng mới");
        assertThat(after.get("experience").get("values").get(0).get("months").asInt()).isEqualTo(36);
        assertThat(after.get("criteria").get(0).get("cells").get(0).get("evidence"))
                .isEqualTo(before.get("criteria").get(0).get("cells").get(0).get("evidence"));
        assertThat(after.get("totalScore")).isEqualTo(before.get("totalScore"));
        assertThat(after.get("columns").get(0).get("scoredAt")).isEqualTo(before.get("columns").get(0).get("scoredAt"));
        assertThat(after.get("columns").get(0).get("scoringRunId").asString()).isEqualTo(run.getId().toString());
    }
}
