package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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

// FR-U10 T1-T5, T13 (REQUIREMENT muc 7.1) - C1 ung vien chon khung qua HTTP that. Helper chep theo
// InterviewInvitationCandidateControllerIntegrationTest (khong tach dung chung, tien le du an). @Transactional cap lop:
// moi test rollback; khong test nao o day can thu tu thoi gian giua cac ban ghi do Postgres sinh (confirmed_at, sent_at
// deu la Instant Java). Cap nhat bang JDBC thi flush truoc, clear sau (persistence context dung chung voi MockMvc).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterviewSlotConfirmControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String MSG_NOT_ALLOWED = "Chỉ chọn được khung giờ khi đơn đang ở trạng thái Đã mời phỏng vấn.";
    private static final String MSG_ALREADY_CONFIRMED =
            "Giấy mời này đã được chốt khung giờ. Muốn đổi lịch, hãy nhắn cho nhà tuyển dụng qua tab Trao đổi.";
    private static final String MSG_CONFLICT = "Giấy mời của đơn này vừa được thay đổi ở nơi khác. Vui lòng tải lại trang.";
    private static final String MSG_PASSED = "Khung giờ này đã qua. Vui lòng chọn khung giờ khác.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // ---- helper (chep theo InterviewInvitationCandidateControllerIntegrationTest) ----

    // prefix <= 27 ky tu ke ca hau to helper.
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
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "accessToken");
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
                                {"email":"%s","password":"password123","fullName":"Ung Vien Test"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String createOpenJob(String hrToken, String prefix) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueName("Cong ty " + prefix))))
                .andExpect(status().isCreated());
        MvcResult jobResult = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
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
                                        .formatted(uniqueName("Job " + prefix))))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = extractJsonField(jobResult.getResponse().getContentAsString(), "id");
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
        return jobId;
    }

    private String apply(String candidateToken, String jobId) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult resume = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        String resumeId = extractJsonField(resume.getResponse().getContentAsString(), "id");
        MvcResult application = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                                """.formatted(jobId, resumeId)))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(application.getResponse().getContentAsString(), "id");
    }

    private static String slotJson(Instant at, String location) {
        return "{\"scheduledAt\":\"" + at + "\",\"location\":\"" + location + "\"}";
    }

    // Khung tuong lai, cach nhau mot ngay, tron phut (I2 cung tu cat ve dau phut - H12 R-S3).
    private static String slotsJson(int count, int startDaysAhead) {
        Instant first = Instant.now().plus(startDaysAhead, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(slotJson(first.plus(i, ChronoUnit.DAYS), "Phong " + (i + 1)));
        }
        return json.append(']').toString();
    }

    private void sendInvitation(String hrToken, String applicationId, int slotCount) throws Exception {
        mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slots":%s,"subject":"Thu moi phong van","content":"Xin chao, moi ban tham gia phong van."}
                                """.formatted(slotsJson(slotCount, 3))))
                .andExpect(status().isCreated());
    }

    private void resend(String hrToken, String applicationId, int slotCount) throws Exception {
        mockMvc
                .perform(post("/api/hr/applications/" + applicationId + "/interview-invitation/resend")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slots":%s,"subject":"Thu moi gui lai","content":"Noi dung gui lai"}
                                """.formatted(slotsJson(slotCount, 10))))
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

    private MvcResult confirm(String token, String applicationId, String body) throws Exception {
        var request = post("/api/candidates/applications/" + applicationId + "/interview-invitation/confirm-slot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult confirm(String token, String applicationId, UUID slotId) throws Exception {
        return confirm(token, applicationId, "{\"slotId\":\"" + slotId + "\"}");
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(httpStatus);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo(code);
        assertThat(json.get("message").asString()).isEqualTo(message);
    }

    private record Fixture(String hrToken, String candidateToken, String jobId, String applicationId) {

        UUID applicationUuid() {
            return UUID.fromString(applicationId);
        }
    }

    private Fixture createPending(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        String jobId = createOpenJob(hrToken, prefix);
        String candidateToken = registerAndLoginCandidate(prefix + "-uv");
        return new Fixture(hrToken, candidateToken, jobId, apply(candidateToken, jobId));
    }

    private Fixture createInvited(String prefix, int slotCount) throws Exception {
        Fixture fixture = createPending(prefix);
        sendInvitation(fixture.hrToken(), fixture.applicationId(), slotCount);
        return fixture;
    }

    // Id khung cua giay moi HIEU LUC theo display_order.
    private List<UUID> activeSlotIds(Fixture fixture) {
        return jdbcTemplate.queryForList(
                """
                SELECT s.id FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id
                WHERE i.application_id = ? AND i.superseded_at IS NULL ORDER BY s.display_order
                """,
                UUID.class,
                fixture.applicationUuid());
    }

    private Map<String, Object> activeInvitationRow(Fixture fixture) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM interview_invitations WHERE application_id = ? AND superseded_at IS NULL",
                fixture.applicationUuid());
    }

    private List<Map<String, Object>> slotRows(Fixture fixture) {
        return jdbcTemplate.queryForList(
                """
                SELECT s.* FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id
                WHERE i.application_id = ? ORDER BY i.created_at, s.display_order, s.id
                """,
                fixture.applicationUuid());
    }

    private Map<String, Object> applicationRowWithoutUpdatedAt(Fixture fixture) {
        Map<String, Object> row = new HashMap<>(
                jdbcTemplate.queryForMap("SELECT * FROM job_applications WHERE id = ?", fixture.applicationUuid()));
        row.remove("updated_at");
        return row;
    }

    private int historyCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class,
                fixture.applicationUuid());
    }

    private int confirmedInvitationCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ? AND confirmed_slot_id IS NOT NULL",
                Integer.class,
                fixture.applicationUuid());
    }

    // Cap nhat bang JDBC trong lop @Transactional: flush truoc, clear sau.
    private void sql(String statement, Object... args) {
        entityManager.flush();
        jdbcTemplate.update(statement, args);
        entityManager.clear();
    }

    private void setSlotTime(UUID slotId, Instant at) {
        sql("UPDATE interview_invitation_slots SET scheduled_at = ? WHERE id = ?", Timestamp.from(at), slotId);
    }

    private void assertNothingConfirmed(Fixture fixture) {
        assertThat(confirmedInvitationCount(fixture)).isZero();
    }

    // ---- T1 ----

    @Test
    void t1_confirmMiddleSlot_returns200_onlyConfirmedColumnsChange() throws Exception {
        Fixture fixture = createInvited("u10-t1", 3);
        List<UUID> slots = activeSlotIds(fixture);
        Map<String, Object> invitationBefore = activeInvitationRow(fixture);
        List<Map<String, Object>> slotsBefore = slotRows(fixture);
        Map<String, Object> applicationBefore = applicationRowWithoutUpdatedAt(fixture);
        int historyBefore = historyCount(fixture);
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

        MvcResult result = confirm(fixture.candidateToken(), fixture.applicationId(), slots.get(1));

        Instant after = Instant.now();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        for (String forbidden : List.of("id", "applicationId", "sentBy", "supersededAt", "scheduledAt", "location")) {
            assertThat(json.has(forbidden)).as("field goc %s", forbidden).isFalse();
        }
        assertThat(json.get("confirmedAt").isNull()).isFalse();
        JsonNode responseSlots = json.get("slots");
        assertThat(responseSlots.size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            assertThat(responseSlots.get(i).get("id").asString()).isEqualTo(slots.get(i).toString());
            assertThat(responseSlots.get(i).get("confirmed").asBoolean()).as("khung %d", i + 1).isEqualTo(i == 1);
        }

        Map<String, Object> invitationAfter = activeInvitationRow(fixture);
        assertThat(invitationAfter.get("confirmed_slot_id")).isEqualTo(slots.get(1));
        Instant confirmedAt = ((Timestamp) invitationAfter.get("confirmed_at")).toInstant();
        assertThat(confirmedAt).isBetween(before, after);
        assertThat(confirmedAt).isAfter(((Timestamp) invitationAfter.get("sent_at")).toInstant());
        assertThat(Instant.parse(json.get("confirmedAt").asString())).isEqualTo(confirmedAt);
        // Moi cot khac cua giay moi va ca 3 khung giu nguyen (R-S3).
        Map<String, Object> invitationRest = new HashMap<>(invitationAfter);
        invitationRest.remove("confirmed_slot_id");
        invitationRest.remove("confirmed_at");
        Map<String, Object> invitationBeforeRest = new HashMap<>(invitationBefore);
        invitationBeforeRest.remove("confirmed_slot_id");
        invitationBeforeRest.remove("confirmed_at");
        assertThat(invitationRest).isEqualTo(invitationBeforeRest);
        assertThat(invitationBefore.get("confirmed_slot_id")).isNull();
        assertThat(slotRows(fixture)).isEqualTo(slotsBefore);
        // Don: moi cot giong het tru updated_at (R-S2); khong them lich su trang thai (R-S1).
        assertThat(applicationRowWithoutUpdatedAt(fixture)).isEqualTo(applicationBefore);
        assertThat(applicationBefore.get("status")).isEqualTo("INTERVIEW_INVITED");
        assertThat(historyCount(fixture)).isEqualTo(historyBefore);

        // I1 phia HR thay khung chot.
        MvcResult hrList = mockMvc
                .perform(get("/api/hr/applications/" + fixture.applicationId() + "/interview-invitations")
                        .header("Authorization", "Bearer " + fixture.hrToken()))
                .andReturn();
        assertThat(hrList.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(hrList).get("invitations").get(0).get("confirmedSlotId").asString())
                .isEqualTo(slots.get(1).toString());
    }

    // ---- T2 - bien thoi gian (R-C6) tren khung 1 ----

    @Test
    void t2_slotOneMinuteAgo_returns409Passed() throws Exception {
        Fixture fixture = createInvited("u10-t2-past", 3);
        UUID slot = activeSlotIds(fixture).get(0);
        setSlotTime(slot, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertError(confirm(fixture.candidateToken(), fixture.applicationId(), slot), 409, "INTERVIEW_SLOT_PASSED", MSG_PASSED);
        assertNothingConfirmed(fixture);
    }

    // "Dung now" thuc chat kiem scheduled_at <= now: gia tri ghi truoc request, now cua service luon >= gia tri do (muc 7.1
    // T2) - ket qua xac dinh, khong inject Clock.
    @Test
    void t2_slotAtNow_returns409Passed() throws Exception {
        Fixture fixture = createInvited("u10-t2-now", 3);
        UUID slot = activeSlotIds(fixture).get(0);
        setSlotTime(slot, Instant.now());

        assertError(confirm(fixture.candidateToken(), fixture.applicationId(), slot), 409, "INTERVIEW_SLOT_PASSED", MSG_PASSED);
        assertNothingConfirmed(fixture);
    }

    @Test
    void t2_slotOneMinuteAhead_returns200() throws Exception {
        Fixture fixture = createInvited("u10-t2-ahead", 3);
        UUID slot = activeSlotIds(fixture).get(0);
        setSlotTime(slot, Instant.now().plus(1, ChronoUnit.MINUTES));

        assertThat(confirm(fixture.candidateToken(), fixture.applicationId(), slot).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(activeInvitationRow(fixture).get("confirmed_slot_id")).isEqualTo(slot);
    }

    // ---- T3a - SLOT_SELECTION_NOT_ALLOWED ----

    @Test
    void t3a_pendingApplication_returns409NotAllowed() throws Exception {
        Fixture fixture = createPending("u10-t3a-pending");

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), UUID.randomUUID()),
                409,
                "SLOT_SELECTION_NOT_ALLOWED",
                MSG_NOT_ALLOWED);
    }

    @Test
    void t3a_hiredApplication_returns409NotAllowed() throws Exception {
        assertNotAllowedAfter("u10-t3a-hired", "HIRED");
    }

    @Test
    void t3a_rejectedApplication_returns409NotAllowed() throws Exception {
        assertNotAllowedAfter("u10-t3a-rejected", "REJECTED");
    }

    @Test
    void t3a_withdrawnApplication_returns409NotAllowed() throws Exception {
        Fixture fixture = createInvited("u10-t3a-withdrawn", 3);
        mockMvc
                .perform(patch("/api/candidates/applications/" + fixture.applicationId() + "/withdraw")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk());

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), activeSlotIds(fixture).get(0)),
                409,
                "SLOT_SELECTION_NOT_ALLOWED",
                MSG_NOT_ALLOWED);
        assertNothingConfirmed(fixture);
    }

    private void assertNotAllowedAfter(String prefix, String finalStatus) throws Exception {
        Fixture fixture = createInvited(prefix, 3);
        changeStatus(fixture.hrToken(), fixture.applicationId(), finalStatus);

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), activeSlotIds(fixture).get(0)),
                409,
                "SLOT_SELECTION_NOT_ALLOWED",
                MSG_NOT_ALLOWED);
        assertNothingConfirmed(fixture);
    }

    // ---- T3b - 404 INTERVIEW_INVITATION_NOT_FOUND (don INTERVIEW_INVITED khong co giay moi - du lieu ghi tay) ----

    @Test
    void t3b_interviewInvitedWithoutInvitation_returns404() throws Exception {
        Fixture fixture = createPending("u10-t3b");
        sql("UPDATE job_applications SET status = 'INTERVIEW_INVITED' WHERE id = ?", fixture.applicationUuid());

        MvcResult result = confirm(fixture.candidateToken(), fixture.applicationId(), UUID.randomUUID());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("INTERVIEW_INVITATION_NOT_FOUND");
    }

    // ---- T3c - INTERVIEW_SLOT_ALREADY_CONFIRMED (Q3) ----

    @Test
    void t3c_singleSlotInvitation_autoConfirmed_returns409AlreadyConfirmed() throws Exception {
        Fixture fixture = createInvited("u10-t3c-single", 1);
        UUID slot = activeSlotIds(fixture).get(0);
        Map<String, Object> before = activeInvitationRow(fixture);

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), slot),
                409,
                "INTERVIEW_SLOT_ALREADY_CONFIRMED",
                MSG_ALREADY_CONFIRMED);
        assertThat(activeInvitationRow(fixture)).isEqualTo(before);
    }

    @Test
    void t3c_secondConfirm_otherSlotThenSameSlot_returns409_confirmationUnchanged() throws Exception {
        Fixture fixture = createInvited("u10-t3c-twice", 3);
        List<UUID> slots = activeSlotIds(fixture);
        assertThat(confirm(fixture.candidateToken(), fixture.applicationId(), slots.get(1)).getResponse().getStatus())
                .isEqualTo(200);
        Map<String, Object> afterFirst = activeInvitationRow(fixture);

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), slots.get(2)),
                409,
                "INTERVIEW_SLOT_ALREADY_CONFIRMED",
                MSG_ALREADY_CONFIRMED);
        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), slots.get(1)),
                409,
                "INTERVIEW_SLOT_ALREADY_CONFIRMED",
                MSG_ALREADY_CONFIRMED);

        Map<String, Object> afterThird = activeInvitationRow(fixture);
        assertThat(afterThird.get("confirmed_slot_id")).isEqualTo(slots.get(1));
        assertThat(afterThird.get("confirmed_at")).isEqualTo(afterFirst.get("confirmed_at"));
    }

    // ---- T3d - INTERVIEW_INVITATION_CONFLICT, khong ca nao 500 (R-W4) ----

    @Test
    void t3d_slotOfOldInvitation_returns409Conflict() throws Exception {
        Fixture fixture = createInvited("u10-t3d-old", 3);
        UUID oldSlot = activeSlotIds(fixture).get(0);
        resend(fixture.hrToken(), fixture.applicationId(), 2);

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), oldSlot),
                409,
                "INTERVIEW_INVITATION_CONFLICT",
                MSG_CONFLICT);
        assertNothingConfirmed(fixture);
    }

    @Test
    void t3d_slotOfAnotherApplication_returns409Conflict() throws Exception {
        Fixture fixture = createInvited("u10-t3d-own", 3);
        Fixture other = createInvited("u10-t3d-other", 3);

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), activeSlotIds(other).get(0)),
                409,
                "INTERVIEW_INVITATION_CONFLICT",
                MSG_CONFLICT);
        assertNothingConfirmed(fixture);
        assertNothingConfirmed(other);
    }

    @Test
    void t3d_unknownSlotId_returns409Conflict() throws Exception {
        Fixture fixture = createInvited("u10-t3d-random", 3);

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), UUID.randomUUID()),
                409,
                "INTERVIEW_INVITATION_CONFLICT",
                MSG_CONFLICT);
        assertNothingConfirmed(fixture);
    }

    // ---- T3e - 400 Bean Validation (O3) ----

    @Test
    void t3e_nullOrMissingSlotId_returns400FieldMap() throws Exception {
        Fixture fixture = createInvited("u10-t3e", 3);

        for (String requestBody : List.of("{\"slotId\":null}", "{}")) {
            MvcResult result = confirm(fixture.candidateToken(), fixture.applicationId(), requestBody);
            assertThat(result.getResponse().getStatus()).as(requestBody).isEqualTo(400);
            assertThat(body(result).get("slotId").asString()).isEqualTo("Vui lòng chọn một khung giờ.");
        }
        assertNothingConfirmed(fixture);
    }

    // ---- T3f - quyen (R-A) ----

    @Test
    void t3f_applicationOfAnotherCandidate_returns403() throws Exception {
        Fixture fixture = createInvited("u10-t3f-own", 3);
        String otherToken = registerAndLoginCandidate("u10-t3f-other");

        MvcResult result = confirm(otherToken, fixture.applicationId(), activeSlotIds(fixture).get(0));

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertNothingConfirmed(fixture);
    }

    @Test
    void t3f_unknownApplication_returns404ApplicationNotFound() throws Exception {
        Fixture fixture = createPending("u10-t3f-unknown");

        MvcResult result = confirm(fixture.candidateToken(), UUID.randomUUID().toString(), UUID.randomUUID());

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    @Test
    void t3f_hrToken_returns403_noToken_returns401() throws Exception {
        Fixture fixture = createInvited("u10-t3f-auth", 3);
        UUID slot = activeSlotIds(fixture).get(0);

        assertThat(confirm(fixture.hrToken(), fixture.applicationId(), slot).getResponse().getStatus()).isEqualTo(403);
        assertThat(confirm(null, fixture.applicationId(), slot).getResponse().getStatus()).isEqualTo(401);
        assertNothingConfirmed(fixture);
    }

    // ---- T4 - thu tu kiem (R-W5, muc 4.3) ----

    @Test
    void t4_hiredWithUnconfirmedInvitationAndPastSlot_returnsNotAllowedFirst() throws Exception {
        Fixture fixture = createInvited("u10-t4-hired", 3);
        UUID slot = activeSlotIds(fixture).get(0);
        changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");
        setSlotTime(slot, Instant.now().minus(1, ChronoUnit.DAYS));

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), slot),
                409,
                "SLOT_SELECTION_NOT_ALLOWED",
                MSG_NOT_ALLOWED);
    }

    @Test
    void t4_confirmedActiveInvitationAndSlotOfOldInvitation_returnsAlreadyConfirmedFirst() throws Exception {
        Fixture fixture = createInvited("u10-t4-confirmed", 3);
        UUID oldSlot = activeSlotIds(fixture).get(0);
        resend(fixture.hrToken(), fixture.applicationId(), 1); // giay moi moi 1 khung - tu chot

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), oldSlot),
                409,
                "INTERVIEW_SLOT_ALREADY_CONFIRMED",
                MSG_ALREADY_CONFIRMED);
    }

    @Test
    void t4_pastSlotOfAnotherApplication_returnsConflictBeforePassed() throws Exception {
        Fixture fixture = createInvited("u10-t4-own", 3);
        Fixture other = createInvited("u10-t4-other", 3);
        UUID otherSlot = activeSlotIds(other).get(0);
        setSlotTime(otherSlot, Instant.now().minus(1, ChronoUnit.DAYS));

        assertError(
                confirm(fixture.candidateToken(), fixture.applicationId(), otherSlot),
                409,
                "INTERVIEW_INVITATION_CONFLICT",
                MSG_CONFLICT);
    }

    @Test
    void t4_hiredApplicationOfAnotherCandidate_returns403BeforeLock() throws Exception {
        Fixture fixture = createInvited("u10-t4-403", 3);
        changeStatus(fixture.hrToken(), fixture.applicationId(), "HIRED");
        String otherToken = registerAndLoginCandidate("u10-t4-403-other");

        assertThat(confirm(otherToken, fixture.applicationId(), activeSlotIds(fixture).get(0))
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
    }

    // ---- T5 - trang thai tin khong anh huong (R-C7, Q13) ----

    @Test
    void t5_closedJob_stillConfirms() throws Exception {
        Fixture fixture = createInvited("u10-t5-closed", 3);
        sql("UPDATE jobs SET status = 'CLOSED' WHERE id = ?", UUID.fromString(fixture.jobId()));

        assertThat(confirm(fixture.candidateToken(), fixture.applicationId(), activeSlotIds(fixture).get(0))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
    }

    @Test
    void t5_softDeletedJob_stillConfirms() throws Exception {
        Fixture fixture = createInvited("u10-t5-deleted", 3);
        sql("UPDATE jobs SET deleted_at = now() WHERE id = ?", UUID.fromString(fixture.jobId()));

        assertThat(confirm(fixture.candidateToken(), fixture.applicationId(), activeSlotIds(fixture).get(0))
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
    }

    // ---- T13 - bat bien route ----

    @Test
    void t13_noPutPatchDeleteOnConfirmSlot() throws Exception {
        Fixture fixture = createInvited("u10-t13", 3);
        String path = "/api/candidates/applications/" + fixture.applicationId() + "/interview-invitation/confirm-slot";
        String requestBody = "{\"slotId\":\"" + activeSlotIds(fixture).get(0) + "\"}";

        for (var request : List.of(put(path), patch(path), delete(path))) {
            int status = mockMvc.perform(request.header("Authorization", "Bearer " + fixture.candidateToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            assertThat(status).isIn(404, 405);
        }
        assertNothingConfirmed(fixture);
    }
}
