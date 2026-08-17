package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
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

// Mau y het ScoringRunHrControllerIntegrationTest (cung mot bo helper dang ky/dang nhap/tao du
// lieu, khong tach thanh tien ich dung chung - dung tien le cua toan bo cac test file trong du an
// nay). @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationOwnerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

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

    private String registerAndLoginCandidate(String prefix, String fullName) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody = """
                {"email":"%s","password":"password123","fullName":"%s"}
                """.formatted(email, fullName);
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

    private void addCriterion(String token, String jobId, String requestBody) throws Exception {
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());
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

    // Gia lap ket qua D1 (khong goi LLM that trong test).
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

    private MvcResult listApplications(String token, String jobId) throws Exception {
        return mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/applications").header("Authorization", "Bearer " + token))
                .andReturn();
    }

    // ---- Case duong ----

    @Test
    void listApplications_ownerHr_returnsCandidateNameAndParseStatusWithoutTotalScoreOrRank() throws Exception {
        String hrToken = registerAndLoginHr("hr-basic");
        createCompany(hrToken, uniqueName("Cong ty Basic"));
        String jobId = createJob(hrToken, uniqueName("Job Basic"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-basic", "Nguyen Van Ung Vien");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        apply(candidateToken, jobId, resumeId);

        MvcResult result = listApplications(hrToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"candidateName\":\"Nguyen Van Ung Vien\"");
        assertThat(body).contains("\"resumeParseStatus\":\"DONE\"");
        assertThat(body).contains("\"latestScoringRunId\":null");
        assertThat(body).doesNotContain("totalScore").doesNotContain("\"rank\"");
    }

    @Test
    void listApplications_afterCreatingScoringRun_includesLatestScoringRunInfo() throws Exception {
        String hrToken = registerAndLoginHr("hr-latest-run");
        createCompany(hrToken, uniqueName("Cong ty Latest Run"));
        String jobId = createJob(hrToken, uniqueName("Job Latest Run"));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate("cand-latest-run", "Tran Thi Ung Vien");
        String resumeId = uploadResume(candidateToken);
        markResumeParsedDone(UUID.fromString(resumeId));
        String applicationId = apply(candidateToken, jobId, resumeId);

        MvcResult createResult = createScoringRun(hrToken, applicationId);
        assertThat(createResult.getResponse().getStatus()).isEqualTo(201);
        String runId = extractJsonField(createResult.getResponse().getContentAsString(), "id");

        MvcResult result = listApplications(hrToken, jobId);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"latestScoringRunId\":\"" + runId + "\"");
        assertThat(body).contains("\"latestScoringRunStatus\":\"PENDING\"");
        assertThat(body).contains("\"latestScoringRunFinishedAt\":null");
    }

    @Test
    void listApplications_jobWithNoApplications_returnsEmptyList() throws Exception {
        String hrToken = registerAndLoginHr("hr-no-apps");
        createCompany(hrToken, uniqueName("Cong ty Khong Co Don"));
        String jobId = createJob(hrToken, uniqueName("Job Khong Co Don"));

        MvcResult result = listApplications(hrToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString().trim()).isEqualTo("[]");
    }

    // ---- Case am ----

    @Test
    void listApplications_calledByCandidate_returns403() throws Exception {
        String hrToken = registerAndLoginHr("hr-cand-call");
        createCompany(hrToken, uniqueName("Cong ty Cand Call"));
        String jobId = createJob(hrToken, uniqueName("Job Cand Call"));
        String candidateToken = registerAndLoginCandidate("cand-caller", "Ung Vien Goi Sai Quyen");

        MvcResult result = listApplications(candidateToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listApplications_byHrOfAnotherCompany_returns403() throws Exception {
        String hrToken = registerAndLoginHr("hr-owner");
        createCompany(hrToken, uniqueName("Cong ty Chu"));
        String jobId = createJob(hrToken, uniqueName("Job Chu"));

        String otherHrToken = registerAndLoginHr("hr-other");
        createCompany(otherHrToken, uniqueName("Cong ty Khac"));

        MvcResult result = listApplications(otherHrToken, jobId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void listApplications_jobNotFound_returns404() throws Exception {
        String hrToken = registerAndLoginHr("hr-job-notfound");
        createCompany(hrToken, uniqueName("Cong ty Job Khong Ton Tai"));

        MvcResult result = listApplications(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("JOB_NOT_FOUND");
    }
}
