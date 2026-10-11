package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H10 S6 (GET /api/hr/applications/{id}/screening-answers) - T10, T11, T12, T16 (phan S6).
// Dong ban chup + screening_captured_at ghi bang JdbcTemplate (gia lap FR-U09, R-B2): entity ApplicationScreeningAnswer
// @Immutable, khong setter - FR-H10 khong co duong ghi nao. @Transactional: moi @Test rollback rieng; JdbcTemplate
// dung chung connection cua transaction test nen S6 thay ngay du lieu vua ghi. Helper dang ky/dang nhap/tao du lieu
// theo khuon ApplicationHrDetailControllerIntegrationTest (khong tach tien ich test dung chung).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationScreeningHrIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-05T17:35:00Z");

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

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(text(result));
    }

    private static String text(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
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

    private void createCompany(String token) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cong ty " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
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
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private UUID createApplicationEntity(String jobId, ApplicationStatus applicationStatus) {
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();

        User candidate = new User();
        candidate.setEmail(uniqueEmail("cand-entity"));
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
        application.setStatus(applicationStatus);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        return jobApplicationRepository.saveAndFlush(application).getId();
    }

    private record Fixture(String hrToken, String jobId, UUID applicationId) {
    }

    private Fixture createFixture(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken);
        String jobId = createJob(hrToken);
        return new Fixture(hrToken, jobId, createApplicationEntity(jobId, ApplicationStatus.PENDING));
    }

    // Gia lap buoc chup cua FR-U09 (R-S4).
    private void markCaptured(UUID applicationId) {
        jdbcTemplate.update(
                "UPDATE job_applications SET screening_captured_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(CAPTURED_AT, ZoneOffset.UTC),
                applicationId);
    }

    private void insertAnswer(
            UUID applicationId,
            UUID questionId,
            int displayOrder,
            String type,
            String questionText,
            String optionsJson,
            boolean required,
            String answerText,
            Boolean answerYesNo) {
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers
                    (application_id, question_id, display_order, question_type_snapshot, question_text_snapshot,
                     options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
                applicationId, questionId, displayOrder, type, questionText, optionsJson, required, answerText,
                answerYesNo);
    }

    private MvcResult getAnswers(String token, UUID applicationId) throws Exception {
        var request = get("/api/hr/applications/" + applicationId + "/screening-answers");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult call(String method, String path, String token, String json) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException(method);
        };
        builder = builder.header("Authorization", "Bearer " + token);
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(builder).andReturn();
    }

    // ---- T11: ba trang thai ----

    @Test
    void t11_notCaptured_returnsNotCapturedWithEmptyItems() throws Exception {
        Fixture fixture = createFixture("s6-not-captured");
        // Co dong ban chup nhung chua danh dau screening_captured_at -> van NOT_CAPTURED (R-A3 xet cot truoc).
        insertAnswer(fixture.applicationId(), null, 1, "YES_NO", "Cau hoi", null, false, null, true);

        MvcResult result = getAnswers(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("applicationId").asString()).isEqualTo(fixture.applicationId().toString());
        assertThat(json.get("state").asString()).isEqualTo("NOT_CAPTURED");
        assertThat(json.get("capturedAt").isNull()).isTrue();
        assertThat(json.get("items").size()).isZero();
    }

    @Test
    void t11_capturedWithoutRows_returnsNoQuestions() throws Exception {
        Fixture fixture = createFixture("s6-no-questions");
        markCaptured(fixture.applicationId());

        JsonNode json = body(getAnswers(fixture.hrToken(), fixture.applicationId()));

        assertThat(json.get("state").asString()).isEqualTo("NO_QUESTIONS");
        assertThat(Instant.parse(json.get("capturedAt").asString())).isEqualTo(CAPTURED_AT);
        assertThat(json.get("items").size()).isZero();
    }

    @Test
    void t11_capturedWithRows_returnsItemsByDisplayOrderAndNullForBlankAnswers() throws Exception {
        Fixture fixture = createFixture("s6-captured");
        markCaptured(fixture.applicationId());
        // Chen nguoc thu tu de chung minh S6 sap theo display_order, khong theo thu tu chen.
        insertAnswer(fixture.applicationId(), null, 3, "SINGLE_CHOICE", "Muc luong mong muon?",
                "[\"Duoi 10 trieu\",\"10-14 trieu\"]", false, null, null);
        insertAnswer(fixture.applicationId(), null, 2, "YES_NO", "San sang lam ca toi?", null, false, null, null);
        insertAnswer(fixture.applicationId(), null, 1, "SHORT_TEXT", "Bat dau di lam khi nao?", null, true,
                "Dau thang 11", null);

        MvcResult result = getAnswers(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("state").asString()).isEqualTo("CAPTURED");
        assertThat(Instant.parse(json.get("capturedAt").asString())).isEqualTo(CAPTURED_AT);
        JsonNode items = json.get("items");
        assertThat(items.size()).isEqualTo(3);

        JsonNode first = items.get(0);
        assertThat(first.get("displayOrder").asInt()).isEqualTo(1);
        assertThat(first.get("questionType").asString()).isEqualTo("SHORT_TEXT");
        assertThat(first.get("questionText").asString()).isEqualTo("Bat dau di lam khi nao?");
        assertThat(first.get("required").asBoolean()).isTrue();
        assertThat(first.get("answerText").asString()).isEqualTo("Dau thang 11");
        assertThat(first.get("answerYesNo").isNull()).isTrue();
        assertThat(first.get("options").size()).isZero();

        JsonNode second = items.get(1);
        assertThat(second.get("displayOrder").asInt()).isEqualTo(2);
        assertThat(second.get("questionType").asString()).isEqualTo("YES_NO");
        assertThat(second.get("required").asBoolean()).isFalse();
        assertThat(second.get("answerText").isNull()).as("YES_NO bo trong").isTrue();
        assertThat(second.get("answerYesNo").isNull()).as("YES_NO bo trong").isTrue();

        JsonNode third = items.get(2);
        assertThat(third.get("displayOrder").asInt()).isEqualTo(3);
        assertThat(third.get("questionType").asString()).isEqualTo("SINGLE_CHOICE");
        assertThat(third.get("options").get(0).asString()).isEqualTo("Duoi 10 trieu");
        assertThat(third.get("options").get(1).asString()).isEqualTo("10-14 trieu");
        assertThat(third.get("answerText").isNull()).as("SINGLE_CHOICE bo trong").isTrue();
        assertThat(third.get("answerYesNo").isNull()).isTrue();
        // Item khong mang questionId (muc 4.4).
        assertThat(first.has("questionId")).isFalse();
    }

    // R-A4 - don da rut van xem duoc ban chup.
    @Test
    void s6_withdrawnApplication_stillReadable() throws Exception {
        String hrToken = registerAndLoginHr("s6-withdrawn-hr");
        createCompany(hrToken);
        String jobId = createJob(hrToken);
        UUID applicationId = createApplicationEntity(jobId, ApplicationStatus.WITHDRAWN);
        markCaptured(applicationId);
        insertAnswer(applicationId, null, 1, "YES_NO", "Cau hoi", null, true, null, false);

        JsonNode json = body(getAnswers(hrToken, applicationId));

        assertThat(json.get("state").asString()).isEqualTo("CAPTURED");
        assertThat(json.get("items").get(0).get("answerYesNo").asBoolean()).isFalse();
    }

    // ---- T10: ban chup bat bien ----

    @Test
    void t10_editingReorderingDeletingQuestions_doesNotChangeSnapshot() throws Exception {
        Fixture fixture = createFixture("s6-immutable");
        String questionsUrl = "/api/hr/jobs/" + fixture.jobId() + "/screening-questions";
        MvcResult q1 = call("POST", questionsUrl, fixture.hrToken(),
                "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Bat dau di lam khi nao?\",\"required\":true}");
        MvcResult q2 = call("POST", questionsUrl, fixture.hrToken(),
                "{\"questionType\":\"YES_NO\",\"questionText\":\"San sang lam ca toi?\"}");
        MvcResult q3 = call("POST", questionsUrl, fixture.hrToken(),
                "{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Muc luong?\",\"options\":[\"Thap\",\"Cao\"]}");
        assertThat(List.of(q1, q2, q3)).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(201));
        UUID id1 = UUID.fromString(body(q1).get("id").asString());
        UUID id2 = UUID.fromString(body(q2).get("id").asString());
        UUID id3 = UUID.fromString(body(q3).get("id").asString());

        // Gia lap FR-U09 chup dung ba cau tren tai thoi diem nop.
        markCaptured(fixture.applicationId());
        insertAnswer(fixture.applicationId(), id1, 1, "SHORT_TEXT", "Bat dau di lam khi nao?", null, true,
                "Ngay mai", null);
        insertAnswer(fixture.applicationId(), id2, 2, "YES_NO", "San sang lam ca toi?", null, false, null, true);
        insertAnswer(fixture.applicationId(), id3, 3, "SINGLE_CHOICE", "Muc luong?", "[\"Thap\",\"Cao\"]", false,
                "Cao", null);

        MvcResult before = getAnswers(fixture.hrToken(), fixture.applicationId());
        assertThat(before.getResponse().getStatus()).isEqualTo(200);
        String beforeJson = text(before);

        // S3: doi noi dung + loai + phuong an cua ca ba cau.
        assertThat(call("PUT", questionsUrl + "/" + id1, fixture.hrToken(),
                        "{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Cau 1 da sua\",\"options\":[\"A\",\"B\"]}")
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(call("PUT", questionsUrl + "/" + id2, fixture.hrToken(),
                        "{\"questionType\":\"SHORT_TEXT\",\"questionText\":\"Cau 2 da sua\",\"required\":true}")
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(call("PUT", questionsUrl + "/" + id3, fixture.hrToken(),
                        "{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Cau 3 da sua\",\"options\":[\"X\",\"Y\",\"Z\"]}")
                .getResponse().getStatus()).isEqualTo(200);
        // S5: dao thu tu.
        String order = JSON.writeValueAsString(
                Map.of("questionIds", List.of(id3.toString(), id2.toString(), id1.toString())));
        assertThat(call("PUT", questionsUrl + "/order", fixture.hrToken(), order).getResponse().getStatus())
                .isEqualTo(200);
        // S4: xoa mem mot cau.
        assertThat(call("DELETE", questionsUrl + "/" + id2, fixture.hrToken(), null).getResponse().getStatus())
                .isEqualTo(204);

        MvcResult after = getAnswers(fixture.hrToken(), fixture.applicationId());

        assertThat(after.getResponse().getStatus()).isEqualTo(200);
        assertThat(text(after)).isEqualTo(beforeJson);
        JsonNode items = body(after).get("items");
        assertThat(items.get(0).get("questionText").asString()).isEqualTo("Bat dau di lam khi nao?");
        assertThat(items.get(0).get("questionType").asString()).isEqualTo("SHORT_TEXT");
        assertThat(items.get(1).get("questionType").asString()).isEqualTo("YES_NO");
        assertThat(items.get(2).get("options").size()).isEqualTo(2);
    }

    // ---- T12: quyen S6 (nhu FR-H09 T2-T4) ----

    @Test
    void t12_otherCompanyApplication_returns403() throws Exception {
        Fixture fixture = createFixture("s6-other-co");
        markCaptured(fixture.applicationId());
        String otherHrToken = registerAndLoginHr("s6-other-co-hr2");
        createCompany(otherHrToken);

        MvcResult result = getAnswers(otherHrToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).get("error").asString()).isEqualTo("FORBIDDEN");
        // Khong lo noi dung: than loi khong co state/items.
        assertThat(body(result).has("state")).isFalse();
    }

    @Test
    void t12_nonExistentApplication_returns404ApplicationNotFound() throws Exception {
        String hrToken = registerAndLoginHr("s6-missing-hr");
        createCompany(hrToken);

        MvcResult result = getAnswers(hrToken, UUID.randomUUID());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    // Kiem cong ty TRUOC don: HR chua co cong ty -> COMPANY_NOT_FOUND ca voi don cua cong ty khac lan don khong ton tai.
    @Test
    void t12_hrWithoutCompany_returns404CompanyNotFound() throws Exception {
        Fixture fixture = createFixture("s6-no-company");
        String hrWithoutCompany = registerAndLoginHr("s6-no-company-hr2");

        MvcResult existing = getAnswers(hrWithoutCompany, fixture.applicationId());
        MvcResult missing = getAnswers(hrWithoutCompany, UUID.randomUUID());

        assertThat(existing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(existing).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missing).get("error").asString()).isEqualTo("COMPANY_NOT_FOUND");
    }

    @Test
    void t12_candidateToken_returns403() throws Exception {
        Fixture fixture = createFixture("s6-candidate");
        String candidateToken = registerAndLoginCandidate("s6-candidate-uv");

        assertThat(getAnswers(candidateToken, fixture.applicationId()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void t12_noToken_returns401() throws Exception {
        Fixture fixture = createFixture("s6-no-token");

        MvcResult result = getAnswers(null, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).get("error").asString()).isEqualTo("UNAUTHENTICATED");
    }

    // ---- T16 (S6) ----

    @Test
    void t16_s6ResponseHasNoVerdictLikeFields() throws Exception {
        Fixture fixture = createFixture("s6-no-verdict");
        markCaptured(fixture.applicationId());
        insertAnswer(fixture.applicationId(), null, 1, "YES_NO", "Cau hoi", null, true, null, true);

        JsonNode json = body(getAnswers(fixture.hrToken(), fixture.applicationId()));

        assertThat(json.get("state").asString()).isEqualTo("CAPTURED");
        for (String forbidden : List.of("verdict", "label", "isQualified", "passed", "recommendation")) {
            assertThat(json.has(forbidden)).as("field %s", forbidden).isFalse();
            assertThat(json.get("items").get(0).has(forbidden)).as("item field %s", forbidden).isFalse();
        }
    }
}
