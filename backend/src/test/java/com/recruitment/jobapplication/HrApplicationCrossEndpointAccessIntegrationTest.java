package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// FR-H09 T13 (R-Q4) - CUNG mot don cua cong ty khac: moi endpoint moi cua FR-H09 (E1-E5) VA cac
// endpoint co san trang ho so don goi toi deu tra 403, nhat quan mot quy uoc. Khoa hai huong sai:
// endpoint moi lo tra 404, hoac endpoint cu bi doi tien tay. PATCH /status dung body REJECTED - voi
// INTERVIEW_INVITED controller tra 400 TRUOC khi kiem quyen (ApplicationStatusController), test se
// thay 400 vi sai ly do. Bo helper mau y het ApplicationStatusControllerIntegrationTest.
// @Transactional: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HrApplicationCrossEndpointAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated());
    }

    // Job co mau giay moi (bat buoc cho GET .../interview-invitation/preview) va rubric 100% de mo tin.
    private String createOpenJob(String token) throws Exception {
        String title = uniqueName("Job cross");
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
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
        String jobId = extractJsonField(result.getResponse().getContentAsString(), "id");
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
        return jobId;
    }

    private String applyToJob(String candidateToken, String jobId) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult upload = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        String resumeId = extractJsonField(upload.getResponse().getContentAsString(), "id");
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                                """.formatted(jobId, resumeId)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    @Test
    void otherCompanyHr_allApplicationDetailEndpointsNewAndExisting_return403() throws Exception {
        String ownerHrToken = registerAndLoginHr("cross-owner-hr");
        createCompany(ownerHrToken, uniqueName("Cong ty chu"));
        String jobId = createOpenJob(ownerHrToken);
        String applicationId = applyToJob(registerAndLoginCandidate("cross-cand"), jobId);

        String otherHrToken = registerAndLoginHr("cross-other-hr");
        createCompany(otherHrToken, uniqueName("Cong ty khac"));
        String base = "/api/hr/applications/" + applicationId;

        Map<String, MockHttpServletRequestBuilder> requests = new LinkedHashMap<>();
        // Endpoint moi FR-H09.
        requests.put("E1 GET", get(base));
        requests.put("E2 GET /resume/parsed", get(base + "/resume/parsed"));
        requests.put("E3 GET /scores", get(base + "/scores"));
        requests.put("E4 GET /explanation", get(base + "/explanation"));
        requests.put("E5 GET /history", get(base + "/history"));
        // Endpoint co san trang ho so don goi toi (R-Q4 - khong doi hanh vi).
        requests.put("GET /scoring-runs", get(base + "/scoring-runs"));
        requests.put("GET /resume/download", get(base + "/resume/download"));
        requests.put("PATCH /status REJECTED", patch(base + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"REJECTED"}
                        """));
        requests.put("GET /interview-invitation/preview", get(base + "/interview-invitation/preview"));

        for (Map.Entry<String, MockHttpServletRequestBuilder> entry : requests.entrySet()) {
            MvcResult result = mockMvc
                    .perform(entry.getValue().header("Authorization", "Bearer " + otherHrToken))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as(entry.getKey()).isEqualTo(403);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .as(entry.getKey())
                    .contains("FORBIDDEN");
        }

        // PATCH bi chan that su: don van PENDING.
        assertThat(jobApplicationRepository.findById(UUID.fromString(applicationId)).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }
}
