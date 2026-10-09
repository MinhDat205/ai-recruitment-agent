package com.recruitment.messaging;

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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 dot 2 (muc 12 L1) - phan M1-M3 cua T2 (phia HR), T3 (phia ung vien), T4 (sai vai tro / khong token).
// Phan M4 cua T2/T3 va phan M5 cua T4 them o dot 3. Bo helper chep theo
// ApplicationCandidateDetailControllerIntegrationTest. @Transactional cap lop: moi @Test rollback rieng.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MessageAccessIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager entityManager;

    // ---- helper (chep theo ApplicationCandidateDetailControllerIntegrationTest) ----

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

    private String apply(String candidateToken, String jobId, String resumeId) throws Exception {
        String body = """
                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                """.formatted(jobId, resumeId);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private record Fixture(String hrToken, String candidateToken, String applicationId) {}

    private Fixture createApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, candidateToken, applicationId);
    }

    private static String hrPath(String applicationId) {
        return "/api/hr/applications/" + applicationId + "/messages";
    }

    private static String candidatePath(String applicationId) {
        return "/api/candidates/applications/" + applicationId + "/messages";
    }

    // Spring 7: MockMultipartHttpServletRequestBuilder KHONG con la lop con cua MockHttpServletRequestBuilder -
    // ca hai cung ke thua AbstractMockHttpServletRequestBuilder (javap spring-test-7.0.8).
    private MvcResult perform(AbstractMockHttpServletRequestBuilder<?> request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    // Ba endpoint M1-M3 tren cung mot tien to: GET, POST (multipart, co body hop le), PATCH /read.
    private List<MvcResult> callM1ToM3(String path, String token) throws Exception {
        return List.of(
                perform(get(path), token),
                perform(multipart(path).param("body", "Xin chao"), token),
                perform(patch(path + "/read"), token));
    }

    private void sendText(String path, String token, String text) throws Exception {
        assertThat(perform(multipart(path).param("body", text), token).getResponse().getStatus()).isEqualTo(201);
    }

    private long countMessages(String applicationId) {
        return entityManager
                .createQuery(
                        "SELECT COUNT(m) FROM ApplicationMessage m WHERE m.applicationId = :id", Long.class)
                .setParameter("id", UUID.fromString(applicationId))
                .getSingleResult();
    }

    private List<Instant> readAts(String applicationId) {
        return entityManager
                .createQuery(
                        "SELECT m.readAt FROM ApplicationMessage m WHERE m.applicationId = :id", Instant.class)
                .setParameter("id", UUID.fromString(applicationId))
                .getResultList();
    }

    private void assertError(MvcResult result, int httpStatus, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(httpStatus);
        assertThat(body(result).get("error").asString()).isEqualTo(code);
    }

    // ---- T2 - phia HR ----

    @Test
    void hrOfAnotherCompany_getsForbiddenOnM1ToM3_andNothingChanges() throws Exception {
        Fixture f = createApplication("c06-t2-own");
        sendText(candidatePath(f.applicationId()), f.candidateToken(), "Tin chưa đọc của ứng viên");
        String otherHr = registerAndLoginHr("c06-t2-other-hr");
        createCompany(otherHr, uniqueName("Cong ty khac"));

        for (MvcResult result : callM1ToM3(hrPath(f.applicationId()), otherHr)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(countMessages(f.applicationId())).isEqualTo(1);
        assertThat(readAts(f.applicationId())).containsOnlyNulls();
    }

    @Test
    void hrOnMissingApplication_getsApplicationNotFoundOnM1ToM3() throws Exception {
        String hrToken = registerAndLoginHr("c06-t2-missing");
        createCompany(hrToken, uniqueName("Cong ty missing"));

        for (MvcResult result : callM1ToM3(hrPath(MISSING_ID), hrToken)) {
            assertError(result, 404, "APPLICATION_NOT_FOUND");
        }
    }

    @Test
    void hrWithoutCompany_getsCompanyNotFoundOnM1ToM3() throws Exception {
        Fixture f = createApplication("c06-t2-nocomp");
        String hrWithoutCompany = registerAndLoginHr("c06-t2-nocomp-x");

        for (MvcResult result : callM1ToM3(hrPath(f.applicationId()), hrWithoutCompany)) {
            assertError(result, 404, "COMPANY_NOT_FOUND");
        }
        assertThat(countMessages(f.applicationId())).isZero();
    }

    @Test
    void nonUuidApplicationId_isBadRequest() throws Exception {
        Fixture f = createApplication("c06-t2-uuid");
        assertThat(perform(get(hrPath("khong-phai-uuid")), f.hrToken()).getResponse().getStatus()).isEqualTo(400);
        assertThat(perform(get(candidatePath("khong-phai-uuid")), f.candidateToken()).getResponse().getStatus())
                .isEqualTo(400);
    }

    // ---- T3 - phia ung vien: don cua nguoi khac va don khong ton tai CUNG 404 ----

    @Test
    void candidateOnOthersOrMissingApplication_getsSameNotFoundOnM1ToM3_andNothingChanges() throws Exception {
        Fixture f = createApplication("c06-t3-own");
        sendText(hrPath(f.applicationId()), f.hrToken(), "Tin chưa đọc của HR");
        String otherCandidate = registerAndLoginCandidate("c06-t3-other");

        List<MvcResult> onOthers = callM1ToM3(candidatePath(f.applicationId()), otherCandidate);
        List<MvcResult> onMissing = callM1ToM3(candidatePath(MISSING_ID), otherCandidate);
        for (int i = 0; i < 3; i++) {
            assertError(onOthers.get(i), 404, "APPLICATION_NOT_FOUND");
            assertError(onMissing.get(i), 404, "APPLICATION_NOT_FOUND");
        }
        assertThat(countMessages(f.applicationId())).isEqualTo(1);
        assertThat(readAts(f.applicationId())).containsOnlyNulls();
    }

    // ---- T4 - sai vai tro / khong token (phan M1-M3; M5 o dot 3) ----

    @Test
    void wrongRole_getsForbidden_andNoToken_getsUnauthenticated() throws Exception {
        Fixture f = createApplication("c06-t4-role");

        for (MvcResult result : callM1ToM3(hrPath(f.applicationId()), f.candidateToken())) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
        }
        for (MvcResult result : callM1ToM3(candidatePath(f.applicationId()), f.hrToken())) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
        }
        for (MvcResult result : callM1ToM3(hrPath(f.applicationId()), null)) {
            assertError(result, 401, "UNAUTHENTICATED");
        }
        for (MvcResult result : callM1ToM3(candidatePath(f.applicationId()), null)) {
            assertError(result, 401, "UNAUTHENTICATED");
        }
        assertThat(countMessages(f.applicationId())).isZero();
    }
}
