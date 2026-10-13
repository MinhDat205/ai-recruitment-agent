package com.recruitment.interviewinvitation;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U03 (E3, candidate-view-invitation) - ung vien doc lai giay moi phong van cua chinh minh. Mau
// helper y het InterviewInvitationControllerIntegrationTest (khong tach dung chung, tien le du an),
// them candidateToken vao Fixture vi test nay can goi API bang token ung vien.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterviewInvitationCandidateControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    private String createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        return name;
    }

    private String createJob(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van - {{jobTitle}}",
                    "body":"Kinh chao {{candidateName}}, ban duoc moi phong van tai {{companyName}}.",
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

    private void sendInvitation(String hrToken, String applicationId) throws Exception {
        String body =
                """
                {"slots":[{"scheduledAt":"%s","location":"Van phong cong ty"}],"subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                """
                        .formatted(Instant.now().plus(7, ChronoUnit.DAYS));
        mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void changeStatus(String hrToken, String applicationId, String newStatus) throws Exception {
        mockMvc
                .perform(patch("/api/hr/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(newStatus)))
                .andExpect(status().isOk());
    }

    private MvcResult getInvitation(String candidateToken, String applicationId) throws Exception {
        return mockMvc
                .perform(get("/api/candidates/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + candidateToken))
                .andReturn();
    }

    private record Fixture(String hrToken, String candidateToken, String jobId, String applicationId) {
    }

    private Fixture createPendingApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId, """
                {"name":"Tieu chi","weight":100}
                """);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand", "Nguyen Van Ung Vien");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, candidateToken, jobId, applicationId);
    }

    private Fixture createInterviewInvitedApplication(String prefix) throws Exception {
        Fixture fixture = createPendingApplication(prefix);
        sendInvitation(fixture.hrToken(), fixture.applicationId());
        return fixture;
    }

    // FR-H12 L5 (T13) - doi ten tu get_ownApplication_returnsInvitationWithFiveFieldsOnly. Doc JSON bang JsonMapper (khong
    // so chuoi): khung CO "id" (Q4) nen khang dinh chuoi doesNotContain("\"id\"") cu se do du goc JSON dung.
    @Test
    void get_ownApplication_returnsActiveInvitationWithSlots() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-own");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("subject").asString()).isEqualTo("Thu moi phong van");
        assertThat(json.get("renderedContent").asString()).isEqualTo("Xin chao, moi ban tham gia phong van.");
        assertThat(json.get("sentAt").isNull()).isFalse();
        assertThat(json.get("confirmedAt").isNull()).isFalse();
        assertThat(json.get("slots").size()).isEqualTo(1);
        JsonNode slot = json.get("slots").get(0);
        assertThat(slot.has("id")).isTrue();
        assertThat(slot.get("location").asString()).isEqualTo("Van phong cong ty");
        assertThat(slot.get("confirmed").asBoolean()).isTrue();
        // Goc KHONG co id noi bo phia HR va khong con scheduledAt o goc; khong lan du lieu scoring nao.
        for (String forbidden : List.of("id", "applicationId", "sentBy", "supersededAt", "scheduledAt", "location")) {
            assertThat(json.has(forbidden)).as("field goc %s", forbidden).isFalse();
        }
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("totalScore").doesNotContain("rank").doesNotContain("criterionScore");
    }

    @Test
    void get_applicationOfAnotherCandidate_returns403() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-other-candidate");
        String otherCandidateToken = registerAndLoginCandidate("get-other-cand", "Nguoi Khac");

        MvcResult result = getInvitation(otherCandidateToken, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void get_applicationDoesNotExist_returns404() throws Exception {
        Fixture fixture = createPendingApplication("get-not-exist");

        MvcResult result = getInvitation(fixture.candidateToken(), UUID.randomUUID().toString());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void get_applicationPendingWithoutInvitation_returns404() throws Exception {
        Fixture fixture = createPendingApplication("get-no-invitation");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void get_applicationHiredAfterInterview_stillReturnsInvitation() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-hired");
        changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("Xin chao, moi ban tham gia phong van.");
    }

    @Test
    void get_applicationRejectedAfterInterview_stillReturnsInvitation() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("get-rejected");
        changeStatus(fixture.hrToken(), fixture.applicationId(), "REJECTED");

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("Xin chao, moi ban tham gia phong van.");
    }

    // ---- FR-H12 T13 - I4 tra giay moi HIEU LUC (superseded_at IS NULL), khong dua vao created_at ----

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MvcResult resend(String hrToken, String applicationId, String slotsJson) throws Exception {
        return mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation/resend")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slots":%s,"subject":"Thu moi gui lai","content":"Noi dung gui lai"}
                                """.formatted(slotsJson)))
                .andReturn();
    }

    private static String slotJson(Instant at, String location) {
        return "{\"scheduledAt\":\"" + at + "\",\"location\":\"" + location + "\"}";
    }

    @Test
    void t13_afterResend_returnsNewActiveInvitation_evenIfOldHasLaterCreatedAt() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("t13-resend");
        UUID applicationId = UUID.fromString(fixture.applicationId());
        Instant first = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        Instant second = first.plus(1, ChronoUnit.DAYS);
        Instant third = first.plus(2, ChronoUnit.DAYS);
        MvcResult resent = resend(
                fixture.hrToken(),
                fixture.applicationId(),
                "[" + slotJson(second, "Phong 2") + "," + slotJson(first, "Phong 1") + "," + slotJson(third, "Phong 3") + "]");
        assertThat(resent.getResponse().getStatus()).isEqualTo(201);
        // Giay moi CU co created_at muon hon giay moi moi: I4 van phai tra giay moi moi (R-A1).
        // Lop @Transactional dung chung persistence context voi MockMvc - cap nhat bang JDBC khong lam moi entity trong
        // cache, nen flush truoc va clear sau cau UPDATE.
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE interview_invitations SET created_at = now() + INTERVAL '1 day' "
                        + "WHERE application_id = ? AND superseded_at IS NOT NULL",
                applicationId);
        entityManager.clear();

        MvcResult result = getInvitation(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("subject").asString()).isEqualTo("Thu moi gui lai");
        assertThat(json.get("renderedContent").asString()).isEqualTo("Noi dung gui lai");
        assertThat(json.get("confirmedAt").isNull()).isTrue();
        List<Map<String, Object>> dbSlots = jdbcTemplate.queryForList(
                """
                SELECT s.id, s.display_order FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id
                WHERE i.application_id = ? AND i.superseded_at IS NULL ORDER BY s.display_order
                """,
                applicationId);
        JsonNode slots = json.get("slots");
        assertThat(slots.size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(slots.get(i).get("id").asString()).isEqualTo(dbSlots.get(i).get("id").toString());
            assertThat(slots.get(i).get("displayOrder").asInt()).isEqualTo(i + 1);
            assertThat(slots.get(i).get("confirmed").asBoolean()).isFalse();
        }
        assertThat(slots.get(0).get("location").asString()).isEqualTo("Phong 2");
        assertThat(Instant.parse(slots.get(0).get("scheduledAt").asString())).isEqualTo(second);
        assertThat(slots.get(1).get("location").asString()).isEqualTo("Phong 1");
        assertThat(slots.get(2).get("location").asString()).isEqualTo("Phong 3");
    }

    @Test
    void t13_confirmedSlotOfActiveInvitation_markedConfirmed() throws Exception {
        Fixture fixture = createInterviewInvitedApplication("t13-confirmed");
        Instant first = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        assertThat(resend(
                                fixture.hrToken(),
                                fixture.applicationId(),
                                "[" + slotJson(first, "Phong 1") + "," + slotJson(first.plus(1, ChronoUnit.DAYS), "Phong 2")
                                        + "]")
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        // Gia lap FR-U10: chot khung 2 cua giay moi hieu luc.
        Map<String, Object> slotTwo = jdbcTemplate.queryForMap(
                """
                SELECT s.id, s.invitation_id FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id
                WHERE i.application_id = ? AND i.superseded_at IS NULL AND s.display_order = 2
                """,
                UUID.fromString(fixture.applicationId()));
        // Lop @Transactional dung chung persistence context voi MockMvc - cap nhat bang JDBC khong lam moi entity trong
        // cache, nen flush truoc va clear sau cau UPDATE.
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE interview_invitations SET confirmed_slot_id = ?, confirmed_at = now() WHERE id = ?",
                slotTwo.get("id"),
                slotTwo.get("invitation_id"));
        entityManager.clear();

        JsonNode json = body(getInvitation(fixture.candidateToken(), fixture.applicationId()));

        assertThat(json.get("confirmedAt").isNull()).isFalse();
        assertThat(json.get("slots").get(0).get("confirmed").asBoolean()).isFalse();
        assertThat(json.get("slots").get(1).get("confirmed").asBoolean()).isTrue();
        assertThat(json.get("slots").get(1).get("id").asString()).isEqualTo(slotTwo.get("id").toString());
    }
}
