package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H04 mo rong - du lieu dung chung cho JobScoringRunBulkIntegrationTest va JobScoringRunBulkRaceIntegrationTest
// (REQUIREMENT muc 7.1, L7). KHONG @Transactional cap lop (CLAUDE.md muc 3c): B1 goi create nhieu lan, moi lan mot
// transaction rieng; test phai thay du lieu da commit. Khong don sau test (L6, khuon SavedJobIntegrationTestSupport) -
// moi khang dinh loc theo id cua chinh test.
//
// HR dang ky qua API (co token that); cong ty, tin qua repository; rubric, tieu chi, ung vien, CV, don, luot cham chen
// thang bang SQL de dat trang thai/applied_at/created_at tuy y. LlmTestConfiguration: ChatModel/EmbeddingModel mac dinh
// nem loi - moi test kiem 0 lan goi (B1 khong goi AI, R-T2).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class JobScoringRunBulkIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final Instant BASE_APPLIED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ChatModel chatModel;

    @Autowired
    EmbeddingModel embeddingModel;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    ScoringRunRepository scoringRunRepository;

    record Hr(String token, UUID userId, UUID companyId) {
    }

    record JobFixture(Hr hr, UUID jobId, UUID rubricId) {
    }

    // Mock singleton dung chung moi lop cung context - reset (giu default answer "nem loi") de dem rieng test nay.
    @BeforeEach
    void resetAiMocks() {
        Mockito.reset(chatModel, embeddingModel);
    }

    @AfterEach
    void assertNoAiCall() {
        assertThat(Mockito.mockingDetails(chatModel).getInvocations()).as("ChatModel").isEmpty();
        assertThat(Mockito.mockingDetails(embeddingModel).getInvocations()).as("EmbeddingModel").isEmpty();
    }

    // ---- Tai khoan, cong ty, tin, rubric ----

    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    String register(String role, String email) throws Exception {
        String phone = role.equals("hr") ? ",\"phone\":\"0900000000\"" : "";
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nguoi Dung Test\"%s}"
                                .formatted(email, phone)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(text(login)).get("accessToken").asString();
    }

    Hr hr() throws Exception {
        String email = uniqueEmail("h04b-hr");
        String token = register("hr", email);
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        Company company = new Company();
        company.setOwnerId(userId);
        company.setName("Cong ty " + UUID.randomUUID());
        return new Hr(token, userId, companyRepository.saveAndFlush(company).getId());
    }

    String hrWithoutCompanyToken() throws Exception {
        return register("hr", uniqueEmail("h04b-hr-nocomp"));
    }

    String candidateToken() throws Exception {
        return register("candidate", uniqueEmail("h04b-uv-api"));
    }

    UUID job(Hr hr, JobStatus status) {
        Job job = new Job();
        job.setCompanyId(hr.companyId());
        job.setCreatedBy(hr.userId());
        job.setTitle("Tin H04 bulk " + UUID.randomUUID().toString().substring(0, 8));
        job.setDescription("Mo ta cong viec");
        job.setStatus(status);
        job.setRecruitmentCycle(1);
        return jobRepository.saveAndFlush(job).getId();
    }

    UUID rubric(UUID jobId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO rubrics (id, job_id, name) VALUES (?, ?, 'Rubric H04 bulk')", id, jobId);
        return id;
    }

    void criterion(UUID rubricId, String name, String weight, int displayOrder) {
        jdbcTemplate.update(
                "INSERT INTO rubric_criteria (rubric_id, name, weight, max_score, display_order) VALUES (?, ?, ?, 5, ?)",
                rubricId, name, new BigDecimal(weight), displayOrder);
    }

    // Tin OPEN, chu ky 1, rubric hai tieu chi 60 + 40 = 100.
    JobFixture readyJob() throws Exception {
        return readyJob(hr(), JobStatus.OPEN);
    }

    JobFixture readyJob(Hr hr, JobStatus status) {
        UUID jobId = job(hr, status);
        UUID rubricId = rubric(jobId);
        criterion(rubricId, "Kinh nghiem Java", "60", 1);
        criterion(rubricId, "Ky nang giao tiep", "40", 2);
        return new JobFixture(hr, jobId, rubricId);
    }

    // ---- Ung vien, CV, don, luot cham ----

    UUID candidateUser(String fullName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, full_name) VALUES (?, ?, 'x', 'CANDIDATE', ?)",
                id, "h04b-uv-" + id + "@example.com", fullName);
        return id;
    }

    String emailOf(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    // Moi don mot ung vien rieng (uq_application_per_cycle). parseStatus/hasParsedData dat hai dieu kien R-E2.2.
    UUID application(UUID jobId, String status, int cycle, Instant appliedAt, String parseStatus, boolean hasParsedData) {
        return application(UUID.randomUUID(), jobId, status, cycle, appliedAt, parseStatus, hasParsedData);
    }

    UUID application(
            UUID applicationId,
            UUID jobId,
            String status,
            int cycle,
            Instant appliedAt,
            String parseStatus,
            boolean hasParsedData) {
        UUID candidateId = candidateUser("Ung Vien " + applicationId.toString().substring(0, 8));
        UUID resumeId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO resumes (id, candidate_id, file_url, file_name, file_type, is_primary, parse_status)
                VALUES (?, ?, 'resumes/h04b.pdf', 'cv.pdf', 'PDF', TRUE, ?)
                """,
                resumeId, candidateId, parseStatus);
        if (hasParsedData) {
            jdbcTemplate.update(
                    """
                    INSERT INTO resume_parsed_data (resume_id, raw_text, data, model, prompt_version)
                    VALUES (?, 'Noi dung CV gia lap', CAST('{}' AS jsonb), 'claude-test', 'resume-parse-v2')
                    """,
                    resumeId);
        }
        jdbcTemplate.update(
                """
                INSERT INTO job_applications (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent,
                                              ai_consent_at, applied_at)
                VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                applicationId, jobId, candidateId, resumeId, cycle, status, Timestamp.from(appliedAt),
                Timestamp.from(appliedAt));
        return applicationId;
    }

    // Don du dieu kien: PENDING, CV DONE co resume_parsed_data, chu ky 1, nop luc BASE + order phut.
    UUID eligible(UUID jobId, int order) {
        return application(jobId, "PENDING", 1, appliedAt(order), "DONE", true);
    }

    static Instant appliedAt(int order) {
        return BASE_APPLIED_AT.plusSeconds(60L * order);
    }

    // created_at tuong minh de dinh "luot moi nhat" (findLatestByApplicationIdIn sap created_at DESC).
    void run(UUID applicationId, String status, Instant createdAt, boolean finished) {
        jdbcTemplate.update(
                """
                INSERT INTO scoring_runs (application_id, status, total_score, started_at, finished_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                applicationId,
                status,
                status.equals("DONE") ? new BigDecimal("70.000") : null,
                status.equals("PENDING") ? null : Timestamp.from(createdAt),
                finished ? Timestamp.from(createdAt.plusSeconds(30)) : null,
                Timestamp.from(createdAt));
    }

    // ---- B1 va doc ket qua ----

    MvcResult bulk(String token, UUID jobId) throws Exception {
        var request = post("/api/hr/jobs/" + jobId + "/scoring-runs/bulk");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    JsonNode bulkOk(String token, UUID jobId) throws Exception {
        MvcResult result = bulk(token, jobId);
        assertThat(result.getResponse().getStatus()).as(text(result)).isEqualTo(200);
        return JSON.readTree(text(result));
    }

    static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // skipped -> danh sach "applicationId:reason" theo dung thu tu response.
    static List<String> skipped(JsonNode body) {
        List<String> rows = new ArrayList<>();
        body.get("skipped").forEach(row -> rows.add(row.get("applicationId").asString() + ":" + row.get("reason").asString()));
        return rows;
    }

    static String row(UUID applicationId, BulkScoringSkipReason reason) {
        return applicationId + ":" + reason.name();
    }

    long runCount(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM scoring_runs WHERE application_id = ?", Long.class, applicationId);
    }

    long pendingRunCount(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM scoring_runs WHERE application_id = ? AND status = 'PENDING'", Long.class,
                applicationId);
    }

    boolean rubricLocked(UUID rubricId) {
        return jdbcTemplate.queryForObject("SELECT is_locked FROM rubrics WHERE id = ?", Boolean.class, rubricId);
    }
}
