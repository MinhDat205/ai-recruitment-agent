package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoreExplanation;
import com.recruitment.scoring.ScoreExplanationRepository;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.storage.StorageService;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H16 - du lieu dung chung cho cac test "diem che" (T5..T17). Lop con tu khai annotation Spring (@SpringBootTest,
// @Transactional...) vi mot so test can lop KHONG boc @Transactional (CLAUDE.md muc 3c).
//
// Ung vien dang ky qua API (dang nhap duoc de goi endpoint phia ung vien), so dien thoai ghi bang JDBC. CV cua don co
// tep that trong StorageService (tai CV phai 200 khi khong an danh) va ten tep chua ho ten (kiem resumeFileName). Moi gia
// tri danh tinh cua mot ung vien nam trong Candidate.secrets() - test "khong lo" (REQUIREMENT muc 7.1, quy uoc chung)
// kiem TOAN BO chuoi JSON khong chua gia tri nao trong do.
abstract class BlindReviewIntegrationTestSupport {

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String HIDDEN = IdentityMasker.REPLACEMENT;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CompanyRepository companyRepository;

    @Autowired
    protected JobRepository jobRepository;

    @Autowired
    protected ResumeRepository resumeRepository;

    @Autowired
    protected StorageService storageService;

    @Autowired
    protected ScoringRunRepository scoringRunRepository;

    @Autowired
    protected CriterionScoreRepository criterionScoreRepository;

    @Autowired
    protected ScoreExplanationRepository scoreExplanationRepository;

    protected record Hr(String token, UUID userId, UUID companyId) {
    }

    // contact* = gia tri contact trong CV da trich xuat (null khi chua tao CV da trich xuat).
    protected record Candidate(
            String token,
            UUID id,
            String fullName,
            String email,
            String phone,
            String contactFullName,
            String contactEmail,
            String contactPhone,
            String contactAddress,
            String contactLinkedin) {

        List<String> secrets() {
            List<String> values = new ArrayList<>(List.of(fullName, email, phone));
            for (String v : new String[] {contactFullName, contactEmail, contactPhone, contactAddress, contactLinkedin}) {
                if (v != null) {
                    values.add(v);
                }
            }
            return values;
        }
    }

    protected record App(UUID id, UUID jobId, Candidate candidate, UUID resumeId, String resumeFileName) {
    }

    // ---- Tai khoan, cong ty, tin ----

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(text(result));
    }

    protected static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected String register(String role, String email, String fullName) throws Exception {
        java.util.Map<String, String> request = new java.util.HashMap<>(
                java.util.Map.of("email", email, "password", "password123", "fullName", fullName));
        if (role.equals("hr")) {
            request.put("phone", "0900000000");
        }
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return body(login).get("accessToken").asString();
    }

    protected Hr hr() throws Exception {
        String email = uniqueEmail("h16-hr");
        String token = register("hr", email, "Nguoi Tuyen Dung");
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        Company company = new Company();
        company.setOwnerId(userId);
        company.setName("Cong ty " + UUID.randomUUID());
        return new Hr(token, userId, companyRepository.saveAndFlush(company).getId());
    }

    protected UUID job(Hr hr, boolean blindReview) {
        Job job = new Job();
        job.setCompanyId(hr.companyId());
        job.setCreatedBy(hr.userId());
        job.setTitle("Tin H16 " + UUID.randomUUID().toString().substring(0, 8));
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        UUID id = jobRepository.saveAndFlush(job).getId();
        if (blindReview) {
            setBlindReview(id, true);
        }
        return id;
    }

    // Doi cot truc tiep (B1 da co test rieng - T4). Khong qua entity: cot insertable/updatable = false.
    protected void setBlindReview(UUID jobId, boolean enabled) {
        jdbcTemplate.update("UPDATE jobs SET blind_review_enabled = ? WHERE id = ?", enabled, jobId);
    }

    // ---- Ung vien, CV, don ----

    protected Candidate candidate(String fullName, String phone) throws Exception {
        String email = uniqueEmail("h16-uv");
        String token = register("candidate", email, fullName);
        UUID id = userRepository.findByEmail(email).orElseThrow().getId();
        jdbcTemplate.update("UPDATE users SET phone = ? WHERE id = ?", phone, id);
        entityManager.clear();
        return new Candidate(token, id, fullName, email, phone, null, null, null, null, null);
    }

    protected Candidate withContact(
            Candidate c, String fullName, String email, String phone, String address, String linkedin) {
        return new Candidate(c.token(), c.id(), c.fullName(), c.email(), c.phone(), fullName, email, phone, address,
                linkedin);
    }

    // CV co tep that trong storage; ten tep chua ho ten (giong ten tep CV builder "CV {ho ten}.pdf", a5).
    protected App application(UUID jobId, Candidate candidate, String coverLetter) {
        String fileName = "CV " + candidate.fullName() + ".pdf";
        String storedName = UUID.randomUUID() + ".pdf";
        storageService.store("resumes", storedName,
                new ByteArrayInputStream("%PDF-1.4 noi dung CV gia lap".getBytes(StandardCharsets.UTF_8)));
        Resume resume = new Resume();
        resume.setCandidateId(candidate.id());
        resume.setFileUrl("resumes/" + storedName);
        resume.setFileName(fileName);
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.saveAndFlush(resume);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent,
                                              ai_consent_at, cover_letter)
                VALUES (?, ?, ?, ?, 1, 'PENDING', TRUE, now(), ?)
                """,
                id, jobId, candidate.id(), resume.getId(), coverLetter);
        history(id, null, "PENDING");
        return new App(id, jobId, candidate, resume.getId(), fileName);
    }

    protected void history(UUID applicationId, String from, String to) {
        jdbcTemplate.update(
                "INSERT INTO application_status_history (application_id, from_status, to_status) VALUES (?, ?, ?)",
                applicationId, from, to);
    }

    // Don da gui giay moi (R-S1: co dong lich su INTERVIEW_INVITED) - khong qua H12 (T13 kiem duong that).
    protected void invite(App app) {
        jdbcTemplate.update("UPDATE job_applications SET status = 'INTERVIEW_INVITED' WHERE id = ?", app.id());
        history(app.id(), "PENDING", "INTERVIEW_INVITED");
        entityManager.clear();
    }

    // CV da trich xuat. dataJson la toan bo resume_parsed_data.data; cot danh muc/kinh nghiem dat san de so sanh E2 bat
    // bien (industry/location/experience) giua Job tat va bat.
    protected void parsedData(UUID resumeId, String rawText, String dataJson) {
        jdbcTemplate.update(
                """
                INSERT INTO resume_parsed_data (id, resume_id, raw_text, data, model, prompt_version, industry_code,
                                                region_code, experience_months, experience_entries_counted,
                                                experience_entries_skipped, experience_computed_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), 'claude-sonnet-4-6', 'resume-parse-v2', 'IT_SOFTWARE', 'HA_NOI', 60, 2,
                        0, now())
                """,
                UUID.randomUUID(), resumeId, rawText, dataJson);
    }

    // ---- Ket qua cham (ghi thang, khong chay pipeline/LLM - mau ApplicationHrDetailControllerIntegrationTest) ----

    protected record Criterion(String name, BigDecimal weight, BigDecimal score, String reasoning, String quote) {
    }

    protected UUID doneRun(UUID applicationId, BigDecimal totalScore, List<Criterion> criteria) {
        List<RubricSnapshot.CriterionSnapshot> snapshot = criteria.stream()
                .map(c -> new RubricSnapshot.CriterionSnapshot(UUID.randomUUID(), c.name(), "Mo ta", c.weight(), 10,
                        List.of()))
                .toList();
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(new RubricSnapshot("Rubric H16", snapshot));
        run.setTotalScore(totalScore);
        UUID runId = scoringRunRepository.saveAndFlush(run).getId();
        for (Criterion c : criteria) {
            CriterionScore score = new CriterionScore();
            score.setScoringRunId(runId);
            score.setCriterionNameSnapshot(c.name());
            score.setWeightSnapshot(c.weight());
            score.setMaxScoreSnapshot(10);
            score.setScore(c.score());
            score.setReasoning(c.reasoning());
            score.setEvidence(List.of(new EvidenceEntry(c.quote(), "experience")));
            criterionScoreRepository.saveAndFlush(score);
        }
        return runId;
    }

    protected void explanation(UUID runId, String criterionName, String summary, String strength, String weakness) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(runId);
        explanation.setSummary(summary);
        explanation.setStrengths(List.of(new ExplanationPoint(criterionName, strength)));
        explanation.setWeaknesses(List.of(new ExplanationPoint(criterionName, weakness)));
        explanation.setMetCriteria(List.of(criterionName));
        explanation.setMissingCriteria(List.of());
        explanation.setModel("claude-sonnet-4-6");
        explanation.setPromptVersion("score-explanation-v1");
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    // ---- Sang loc (ban chup FR-U09) ----

    protected void screeningCaptured(UUID applicationId) {
        jdbcTemplate.update("UPDATE job_applications SET screening_captured_at = now() WHERE id = ?", applicationId);
        entityManager.clear();
    }

    protected void screeningText(UUID applicationId, int order, String question, String answer) {
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers (application_id, display_order, question_type_snapshot,
                    question_text_snapshot, options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, 'SHORT_TEXT', ?, NULL, FALSE, ?, NULL)
                """,
                applicationId, order, question, answer);
    }

    protected void screeningChoice(UUID applicationId, int order, String question, List<String> options, String answer) {
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers (application_id, display_order, question_type_snapshot,
                    question_text_snapshot, options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, 'SINGLE_CHOICE', ?, CAST(? AS jsonb), FALSE, ?, NULL)
                """,
                applicationId, order, question, JSON.writeValueAsString(options), answer);
    }

    protected void screeningYesNo(UUID applicationId, int order, String question, boolean answer) {
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers (application_id, display_order, question_type_snapshot,
                    question_text_snapshot, options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, 'YES_NO', ?, NULL, FALSE, NULL, ?)
                """,
                applicationId, order, question, answer);
    }

    // ---- HTTP ----

    protected MvcResult getAs(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
    }

    protected JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = getAs(token, path);
        assertThat(result.getResponse().getStatus()).as("GET %s", path).isEqualTo(200);
        return body(result);
    }

    // Quy uoc chung cua muc 7.1: TOAN BO chuoi JSON khong chua gia tri danh tinh nao (khong phan biet hoa thuong).
    protected static void assertNoLeak(String json, Candidate candidate) {
        String lower = json.toLowerCase(java.util.Locale.ROOT);
        for (String secret : candidate.secrets()) {
            assertThat(lower).as("lo gia tri danh tinh: %s", secret).doesNotContain(secret.toLowerCase(java.util.Locale.ROOT));
        }
    }
}
