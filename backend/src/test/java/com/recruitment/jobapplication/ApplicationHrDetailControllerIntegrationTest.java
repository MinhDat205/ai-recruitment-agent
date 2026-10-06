package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H09 E1 (GET /api/hr/applications/{id}) va E5 (GET .../history) - T1, T2 (phan E1/E5), T3, T4,
// T10, T11 (E1), T14 cua REQUIREMENT.md muc 7.1. Bo helper dang ky/dang nhap/tao du lieu mau y het
// ApplicationStatusControllerIntegrationTest (tien le cua du an: khong tach tien ich test dung chung).
// @Transactional: moi @Test rollback rieng. Vi vay moi dong application_status_history tao trong
// cung mot test nhan CUNG changed_at (CLAUDE.md muc 3c) - test thu tu lich su (T10) phai lui thoi
// gian tuong minh bang native UPDATE.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationHrDetailControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationStatusHistoryRepository statusHistoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void addCriterion(String token, String jobId) throws Exception {
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
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

    // coverLetterJson: doan JSON da escape san (vd "\"Dong 1\\nDong 2\"") hoac null de bo han field.
    private String apply(String candidateToken, String jobId, String resumeId, String coverLetterJson)
            throws Exception {
        String coverLetterField = coverLetterJson == null ? "" : ",\"coverLetter\":" + coverLetterJson;
        String body = """
                {"jobId":"%s","resumeId":"%s","aiConsent":true%s}
                """.formatted(jobId, resumeId, coverLetterField);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void changeStatus(String token, String applicationId, String newStatus) throws Exception {
        mockMvc
                .perform(patch("/api/hr/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(newStatus)))
                .andExpect(status().isOk());
    }

    private void sendInterviewInvitation(String token, String applicationId) throws Exception {
        String body =
                """
                {"scheduledAt":"%s","location":"Van phong cong ty","subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                """
                        .formatted(Instant.now().plus(7, ChronoUnit.DAYS).toString());
        mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private MvcResult getDetail(String token, String applicationId) throws Exception {
        var request = get("/api/hr/applications/" + applicationId);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult getHistory(String token, String applicationId) throws Exception {
        return mockMvc
                .perform(get("/api/hr/applications/" + applicationId + "/history")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
    }

    private record Fixture(String hrToken, String jobId, String jobTitle, String applicationId) {
    }

    private Fixture createApplication(String prefix, String candidateName, String coverLetterJson) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobTitle = uniqueName("Job " + prefix);
        String jobId = createJob(hrToken, jobTitle);
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand", candidateName);
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId, coverLetterJson);
        return new Fixture(hrToken, jobId, jobTitle, applicationId);
    }

    private String createOtherCompanyHr(String prefix) throws Exception {
        String otherHrToken = registerAndLoginHr(prefix + "-other-hr");
        createCompany(otherHrToken, uniqueName("Cong ty khac " + prefix));
        return otherHrToken;
    }

    // ---- E1: case duong ----

    // T1 - du field R-D1 cua dau trang.
    @Test
    void getDetail_ownCompanyApplication_returnsHeaderFields() throws Exception {
        Fixture fixture = createApplication("detail-own", "Tran Thi Ung Vien", "\"Toi rat quan tam vi tri nay\"");

        MvcResult result = getDetail(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("id").asString()).isEqualTo(fixture.applicationId());
        assertThat(json.get("jobId").asString()).isEqualTo(fixture.jobId());
        assertThat(json.get("jobTitle").asString()).isEqualTo(fixture.jobTitle());
        assertThat(json.get("candidateName").asString()).isEqualTo("Tran Thi Ung Vien");
        assertThat(json.get("status").asString()).isEqualTo("PENDING");
        assertThat(json.get("appliedAt").isNull()).isFalse();
        assertThat(json.get("coverLetter").asString()).isEqualTo("Toi rat quan tam vi tri nay");
        // CV vua upload, chua co job nen trich xuat trong test -> PENDING, chua co loi.
        assertThat(json.get("resumeParseStatus").asString()).isEqualTo("PENDING");
        assertThat(json.get("resumeParseError").isNull()).isTrue();
        assertThat(json.get("resumeFileName").asString()).isEqualTo("cv.pdf");
    }

    // T14 (duong) - thu nhieu dong co ky tu HTML/& tra NGUYEN VAN, khong escape/loc/trim (R-D8).
    @Test
    void getDetail_multilineCoverLetterWithHtml_returnsVerbatim() throws Exception {
        Fixture fixture = createApplication(
                "detail-cover-html", "Le Van Ung Vien", "\"Dong 1\\nDong 2 <b>x</b> & y\\n\"");

        MvcResult result = getDetail(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("coverLetter").asString()).isEqualTo("Dong 1\nDong 2 <b>x</b> & y\n");
    }

    // T14 (am) - ung vien khong nhap thu -> null, khong phai chuoi rong.
    @Test
    void getDetail_noCoverLetter_returnsNull() throws Exception {
        Fixture fixture = createApplication("detail-no-cover", "Pham Van Ung Vien", null);

        MvcResult result = getDetail(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("coverLetter").isNull()).isTrue();
    }

    // T11 (E1) - khong co field gan nhan phan quyet (CLAUDE.md muc 7).
    @Test
    void getDetail_responseHasNoVerdictLikeFields() throws Exception {
        Fixture fixture = createApplication("detail-no-verdict", "Hoang Van Ung Vien", null);

        MvcResult result = getDetail(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        for (String forbidden : List.of("verdict", "label", "isQualified", "passed", "recommendation")) {
            assertThat(json.has(forbidden)).as("field %s", forbidden).isFalse();
        }
    }

    // ---- E1: case am ----

    // T2 (E1) - don cua cong ty khac -> 403 FORBIDDEN, dung quy uoc nhom route (R-Q2).
    @Test
    void getDetail_otherCompanyApplication_returns403() throws Exception {
        Fixture fixture = createApplication("detail-other-company", "Vo Van Ung Vien", null);
        String otherHrToken = createOtherCompanyHr("detail-other-company");

        MvcResult result = getDetail(otherHrToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).get("error").asString()).isEqualTo("FORBIDDEN");
    }

    // T2 (E1) - don khong ton tai -> 404 APPLICATION_NOT_FOUND.
    @Test
    void getDetail_nonExistentApplication_returns404() throws Exception {
        String hrToken = registerAndLoginHr("detail-missing-hr");
        createCompany(hrToken, uniqueName("Cong ty detail-missing"));

        MvcResult result = getDetail(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    // T3 - token ung vien -> 403 o filter chain (/api/hr/**).
    @Test
    void getDetail_candidateToken_returns403() throws Exception {
        Fixture fixture = createApplication("detail-candidate-token", "Dang Van Ung Vien", null);
        String candidateToken = registerAndLoginCandidate("detail-candidate-token-other", "Ung Vien Khac");

        MvcResult result = getDetail(candidateToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    // T3 - khong token -> 401 UNAUTHENTICATED.
    @Test
    void getDetail_noToken_returns401() throws Exception {
        Fixture fixture = createApplication("detail-no-token", "Bui Van Ung Vien", null);

        MvcResult result = getDetail(null, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).get("error").asString()).isEqualTo("UNAUTHENTICATED");
    }

    // T4 - HR chua co ho so cong ty -> 404 COMPANY_NOT_FOUND (kiem cong ty TRUOC don, R-Q3).
    @Test
    void getDetail_hrWithoutCompany_returns404CompanyNotFound() throws Exception {
        Fixture fixture = createApplication("detail-no-company", "Do Van Ung Vien", null);
        String hrWithoutCompanyToken = registerAndLoginHr("detail-no-company-hr2");

        MvcResult result = getDetail(hrWithoutCompanyToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
    }

    // ---- E5 ----

    @Test
    void getHistory_newApplication_returnsSubmittedEntry() throws Exception {
        Fixture fixture = createApplication("history-new", "Ngo Van Ung Vien", null);

        MvcResult result = getHistory(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get(0).get("fromStatus").isNull()).isTrue();
        assertThat(json.get(0).get("toStatus").asString()).isEqualTo("PENDING");
        // Dung DTO phia ung vien: khong co changed_by (R-D7).
        assertThat(json.get(0).has("changedBy")).isFalse();
    }

    // T10 (duong) - thu tu theo changed_at tang dan. Lui thoi gian NGUOC voi thu tu insert (dong
    // HIRED cu nhat) de chung minh sap theo changed_at, khong phai theo thu tu chen.
    @Test
    void getHistory_ordersByChangedAtAscending() throws Exception {
        Fixture fixture = createApplication("history-order", "Mai Van Ung Vien", null);
        sendInterviewInvitation(fixture.hrToken(), fixture.applicationId());
        changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");
        UUID applicationId = UUID.fromString(fixture.applicationId());
        backdateHistory(applicationId, "HIRED", "3 days");
        backdateHistory(applicationId, "INTERVIEW_INVITED", "2 days");
        backdateHistory(applicationId, "PENDING", "1 day");

        MvcResult result = getHistory(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        List<String> toStatuses = new ArrayList<>();
        List<Instant> changedAts = new ArrayList<>();
        for (JsonNode entry : json) {
            toStatuses.add(entry.get("toStatus").asString());
            changedAts.add(Instant.parse(entry.get("changedAt").asString()));
        }
        assertThat(toStatuses).containsExactly("HIRED", "INTERVIEW_INVITED", "PENDING");
        assertThat(changedAts).isSorted();
    }

    // T2 + T10 (am) - E5 voi don cua cong ty khac -> 403.
    @Test
    void getHistory_otherCompanyApplication_returns403() throws Exception {
        Fixture fixture = createApplication("history-other-company", "Ly Van Ung Vien", null);
        String otherHrToken = createOtherCompanyHr("history-other-company");

        MvcResult result = getHistory(otherHrToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).get("error").asString()).isEqualTo("FORBIDDEN");
    }

    // T2 (E5) - don khong ton tai -> 404 APPLICATION_NOT_FOUND.
    @Test
    void getHistory_nonExistentApplication_returns404() throws Exception {
        String hrToken = registerAndLoginHr("history-missing-hr");
        createCompany(hrToken, uniqueName("Cong ty history-missing"));

        MvcResult result = getHistory(hrToken, UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    // CLAUDE.md muc 3c: ca lop @Transactional -> moi dong lich su cung changed_at. Lui tung dong
    // bang native UPDATE, roi clear() de lan doc sau lay gia tri moi tu DB (changed_at la cot DB
    // sinh, entity trong persistence context khong tu cap nhat).
    private void backdateHistory(UUID applicationId, String toStatus, String interval) {
        entityManager.flush();
        int updated = entityManager
                .createNativeQuery("UPDATE application_status_history SET changed_at = now() - INTERVAL '" + interval
                        + "' WHERE application_id = ?1 AND to_status = ?2")
                .setParameter(1, applicationId)
                .setParameter(2, toStatus)
                .executeUpdate();
        assertThat(updated).isEqualTo(1);
        entityManager.clear();
        assertThat(statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(applicationId)).isNotEmpty();
    }
}
