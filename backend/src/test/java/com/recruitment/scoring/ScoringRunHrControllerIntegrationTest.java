package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// @Transactional: moi @Test rollback rieng, giong RubricOwnerIntegrationTest. Khong test nao o day
// tao vi pham constraint DB THAT (trg_rubric_weight_sum, UNIQUE...) - moi dieu kien tien quyet deu
// bi chan boi kiem tra Java TRUOC khi cham toi INSERT/UPDATE nao co the vi pham - nen khong roi vao
// tinh huong rollback-only giua chung ma ApplicationIntegrationTest da ghi chu (test do co cham toi
// vi pham UNIQUE that o tang DB ngay trong luong dang kiem).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScoringRunHrControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private String login(String email) throws Exception {
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody = """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test"}
                """.formatted(email);
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        return login(email);
    }

    private void createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private String createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van vi tri %s",
                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title, title);
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private MvcResult addCriterion(String token, String jobId, String requestBody) throws Exception {
        return mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
    }

    private void openJob(String token, String jobId) throws Exception {
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
    }

    private String uploadResume(String candidateToken) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private String apply(String candidateToken, String jobId, String resumeId) throws Exception {
        String body =
                """
                {"jobId":"%s","resumeId":"%s","aiConsent":true,"coverLetter":"Toi rat quan tam vi tri nay"}
                """
                        .formatted(jobId, resumeId);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    // Gia lap ket qua D1 (khong goi LLM that trong test - Dot nay chua co orchestrator cua rieng
    // D2 nen khong the "cham that", chi can du lieu dau vao hop le cho dieu kien tien quyet).
    private void markResumeParsedDone(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow();
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.save(resume);

        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("Ung vien co 3 nam kinh nghiem Java va Docker trong CV");
        data.setData(new ResumeParsedPayload(
                new ResumeParsedPayload.Contact("Nguyen Van A", "a@example.com", null, null, null),
                List.of(),
                List.of(),
                List.of("Java", "Docker"),
                List.of(),
                List.of()));
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion("resume-parse-v1");
        resumeParsedDataRepository.save(data);
    }

    private MvcResult createScoringRun(String token, String applicationId) throws Exception {
        return mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/scoring-runs")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private record ReadyApplication(String hrToken, String jobId, String applicationId) {
    }

    // Chuoi day du: cong ty -> job -> 1 tieu chi 100% -> mo tin -> ung vien nop don voi CV da
    // "cham xong" (gia lap D1) - dieu kien tien quyet nao cung thoa, dung lam nen cho ca case duong
    // lan lam diem xuat phat de tao them tinh huong am (mutate tiep tu day).
    private ReadyApplication setupReadyApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr("hr-" + prefix);
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId, """
                {"name":"Kinh nghiem Java","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-" + prefix);
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        return new ReadyApplication(hrToken, jobId, applicationId);
    }

    // ---- Case duong ----

    @Test
    void createScoringRun_happyPath_returns201WithPendingStatus() throws Exception {
        ReadyApplication ctx = setupReadyApplication("ok");

        MvcResult result = createScoringRun(ctx.hrToken(), ctx.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"PENDING\"");
    }

    @Test
    void createScoringRun_capturesRubricSnapshotInDisplayOrder_andLocksRubric() throws Exception {
        String hrToken = registerAndLoginHr("hr-snapshot");
        createCompany(hrToken, uniqueName("Cong ty Snapshot"));
        String jobId = createJob(hrToken, uniqueName("Job Snapshot"));
        // Co y tao theo thu tu NGUOC voi displayOrder mong muon, de xac nhan snapshot sap dung
        // display_order chu khong phai thu tu tao.
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi B","weight":40,"displayOrder":2}
                """);
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi A","weight":60,"displayOrder":1}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-snapshot");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        MvcResult result = createScoringRun(hrToken, applicationId);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String runId = extractJsonField(result.getResponse().getContentAsString(), "id");

        ScoringRun run = scoringRunRepository.findById(UUID.fromString(runId)).orElseThrow();
        RubricSnapshot snapshot = run.getRubricSnapshot();
        assertThat(snapshot.criteria()).hasSize(2);
        assertThat(snapshot.criteria().get(0).name()).isEqualTo("Tieu chi A");
        assertThat(snapshot.criteria().get(0).weight()).isEqualByComparingTo("60");
        assertThat(snapshot.criteria().get(1).name()).isEqualTo("Tieu chi B");
        assertThat(snapshot.criteria().get(1).weight()).isEqualByComparingTo("40");

        Rubric rubric = rubricRepository.findByJobId(UUID.fromString(jobId)).orElseThrow();
        assertThat(rubric.isLocked()).isTrue();
    }

    @Test
    void createScoringRun_afterPreviousRunFinished_createsNewRunNotBlocked() throws Exception {
        // Cau tra loi (i) cua Q1: mot don DA CHAM XONG (finished_at != NULL) khong chan lot cham
        // moi - phai co test that, khong chi suy luan tren giay.
        ReadyApplication ctx = setupReadyApplication("second-run");

        ScoringRun previous = new ScoringRun();
        previous.setApplicationId(UUID.fromString(ctx.applicationId()));
        previous.setStatus(ScoringRunStatus.RUNNING);
        previous.setStartedAt(Instant.now());
        previous.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(previous);

        MvcResult result = createScoringRun(ctx.hrToken(), ctx.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void createScoringRun_rubricAlreadyLocked_stillSucceeds() throws Exception {
        ReadyApplication ctx = setupReadyApplication("idempotent-lock");

        Rubric rubric = rubricRepository.findByJobId(UUID.fromString(ctx.jobId())).orElseThrow();
        rubric.setLocked(true);
        rubricRepository.saveAndFlush(rubric);

        MvcResult result = createScoringRun(ctx.hrToken(), ctx.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    // ---- Case am ----

    @Test
    void createScoringRun_calledByCandidate_returns403() throws Exception {
        ReadyApplication ctx = setupReadyApplication("candidate-call");
        String candidateToken = registerAndLoginCandidate("cand-caller");

        MvcResult result = createScoringRun(candidateToken, ctx.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void createScoringRun_byHrOfAnotherCompany_returns403() throws Exception {
        ReadyApplication ctx = setupReadyApplication("wrong-hr");
        String otherHrToken = registerAndLoginHr("hr-other-company");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult result = createScoringRun(otherHrToken, ctx.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void createScoringRun_applicationNotFound_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-notfound");
        createCompany(hrToken, uniqueName("Cong ty Khong Ton Tai Don"));

        MvcResult result = createScoringRun(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("APPLICATION_NOT_FOUND");
    }

    @Test
    void createScoringRun_resumeNotParsedYet_returns409() throws Exception {
        String hrToken = registerAndLoginHr("hr-resume-pending");
        createCompany(hrToken, uniqueName("Cong ty CV Chua Xong"));
        String jobId = createJob(hrToken, uniqueName("Job CV Chua Xong"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-resume-pending");
        String resumeId = uploadResume(candidateToken);
        // KHONG goi markResumeParsedDone - resume o mac dinh PENDING sau upload.
        String applicationId = apply(candidateToken, jobId, resumeId);

        MvcResult result = createScoringRun(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RESUME_NOT_PARSED");
    }

    @Test
    void createScoringRun_resumeMarkedDoneButMissingParsedData_returns409() throws Exception {
        String hrToken = registerAndLoginHr("hr-resume-orphan");
        createCompany(hrToken, uniqueName("Cong ty CV Mo Coi"));
        String jobId = createJob(hrToken, uniqueName("Job CV Mo Coi"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-resume-orphan");
        String resumeId = uploadResume(candidateToken);
        // Tinh huong "khong nen xay ra binh thuong": parse_status=DONE nhung KHONG co
        // resume_parsed_data - chi tao duoc bang cach ghi thang qua repository, khong co duong
        // nghiep vu binh thuong nao tao ra trang thai nay.
        Resume resume = resumeRepository.findById(UUID.fromString(resumeId)).orElseThrow();
        resume.setParseStatus(ParseStatus.DONE);
        resumeRepository.saveAndFlush(resume);
        String applicationId = apply(candidateToken, jobId, resumeId);

        MvcResult result = createScoringRun(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RESUME_NOT_PARSED");
    }

    @Test
    void createScoringRun_rubricWithNoCriteria_returns409() throws Exception {
        String hrToken = registerAndLoginHr("hr-no-criteria");
        createCompany(hrToken, uniqueName("Cong ty Rubric Rong"));
        String jobId = createJob(hrToken, uniqueName("Job Rubric Rong"));
        MvcResult createdCriterion = addCriterion(hrToken, jobId, """
                {"name":"Tieu chi tam","weight":100}
                """);
        String criterionId = extractJsonField(createdCriterion.getResponse().getContentAsString(), "id");
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-no-criteria");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        // Rubric chua khoa (chua co luot cham nao duoc tao) nen van xoa duoc tieu chi, dua tong
        // trong so ve 0.
        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isNoContent());

        MvcResult result = createScoringRun(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RUBRIC_INCOMPLETE");
    }

    // Chi test 99.99% (chan) va 100.00% (cho qua, xem cac test case duong). KHONG test duoc moc
    // 100.01% bang duong ghi thong thuong: trg_rubric_weight_sum (DEFERRABLE CONSTRAINT TRIGGER)
    // da chan tong > 100% ngay o tang DB, khong co API nao tao ra duoc rubric vuot 100% de dung
    // lam fixture cho tinh huong nay.
    @Test
    void createScoringRun_rubricWeightAt99_99Percent_returns409() throws Exception {
        String hrToken = registerAndLoginHr("hr-weight-9999");
        createCompany(hrToken, uniqueName("Cong ty Trong So Thieu"));
        String jobId = createJob(hrToken, uniqueName("Job Trong So Thieu"));
        MvcResult createdCriterion = addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        String criterionId = extractJsonField(createdCriterion.getResponse().getContentAsString(), "id");
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-weight-9999");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        // Rubric chua khoa nen van sua duoc - giam trong so xuong duoi 100% SAU khi job da OPEN.
        mockMvc
                .perform(put("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":99.99}
                                """))
                .andExpect(status().isOk());

        MvcResult result = createScoringRun(hrToken, applicationId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RUBRIC_INCOMPLETE");
    }

    @Test
    void createScoringRun_existingPendingRun_returns409() throws Exception {
        ReadyApplication ctx = setupReadyApplication("pending-block");

        MvcResult first = createScoringRun(ctx.hrToken(), ctx.applicationId());
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = createScoringRun(ctx.hrToken(), ctx.applicationId());

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        assertThat(second.getResponse().getContentAsString()).contains("SCORING_RUN_IN_PROGRESS");
    }

    @Test
    void createScoringRun_existingRunningRunNotYetFinished_returns409() throws Exception {
        ReadyApplication ctx = setupReadyApplication("running-block");

        ScoringRun running = new ScoringRun();
        running.setApplicationId(UUID.fromString(ctx.applicationId()));
        running.setStatus(ScoringRunStatus.RUNNING);
        running.setStartedAt(Instant.now());
        scoringRunRepository.saveAndFlush(running);

        MvcResult result = createScoringRun(ctx.hrToken(), ctx.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("SCORING_RUN_IN_PROGRESS");
    }
}
