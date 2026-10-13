package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H12 T1 (phan thong bao), T5, T7, T8 (phan "khong thong bao"). KHONG @Transactional toan lop: thong bao tao o
// listener AFTER_COMMIT (R-N1, R-F2) chi chay khi transaction cua service THAT SU commit - mau
// NotificationEventListenerIntegrationTest. Du lieu ngau nhien rieng (email/ten UUID), chi khang dinh tren du lieu cua
// chinh test, khong don sau test. Poller email tat trong profile test (app.notification.enabled=false) nen email_status
// cua thong bao moi giu PENDING. Bo helper dang ky/dang nhap chep tu InterviewInvitationControllerIntegrationTest (tien le
// du an: khong tach tien ich dung chung).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewInvitationResendIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter VN_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String CONTENT = "Xin chao, moi ban tham gia phong van.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private String login(String email) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "accessToken");
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nha Tuyen Dung Test\",\"phone\":\"0900000000\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        mockMvc.perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nguyen Van Ung Vien\"}"
                                .formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String postForId(String path, String token, String body) throws Exception {
        MvcResult result = mockMvc
                .perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private record Fixture(String hrToken, String candidateToken, UUID candidateId, String jobTitle, String applicationId) {
    }

    private Fixture createPendingApplication(String prefix) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        postForId("/api/hr/companies", hrToken, "{\"name\":\"Cong ty " + UUID.randomUUID() + "\"}");
        String jobTitle = "Nhan vien " + UUID.randomUUID();
        String jobId = postForId(
                "/api/hr/jobs",
                hrToken,
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                  "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao {{candidateName}}","senderName":"Phong Nhan Su"}
                }
                """
                        .formatted(jobTitle));
        mockMvc.perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tieu chi\",\"weight\":100}"))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult upload = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        String resumeId = extractJsonField(upload.getResponse().getContentAsString(), "id");
        String applicationId = postForId(
                "/api/candidates/applications",
                candidateToken,
                "{\"jobId\":\"%s\",\"resumeId\":\"%s\",\"aiConsent\":true}".formatted(jobId, resumeId));
        UUID candidateId = jdbcTemplate.queryForObject(
                "SELECT candidate_id FROM job_applications WHERE id = ?", UUID.class, UUID.fromString(applicationId));
        return new Fixture(hrToken, candidateToken, candidateId, jobTitle, applicationId);
    }

    private static String slot(Instant at, String location) {
        return "{\"scheduledAt\":\"" + at + "\",\"location\":\"" + location + "\"}";
    }

    private static String bodyWithSlots(String... slots) {
        return "{\"slots\":[" + String.join(",", slots) + "],\"subject\":\"Thu moi phong van\",\"content\":\"" + CONTENT
                + "\"}";
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    private MvcResult send(Fixture fixture, String path, String body) throws Exception {
        return mockMvc
                .perform(post("/api/hr/applications/" + fixture.applicationId() + path)
                        .header("Authorization", "Bearer " + fixture.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private int notificationCount(UUID userId, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ?", Integer.class, userId, type);
    }

    private List<Map<String, Object>> invitations(String applicationId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM interview_invitations WHERE application_id = ? ORDER BY sent_at",
                UUID.fromString(applicationId));
    }

    private List<Map<String, Object>> slotsOf(Object invitationId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM interview_invitation_slots WHERE invitation_id = ? ORDER BY display_order", invitationId);
    }

    private Map<String, Object> applicationRow(String applicationId) {
        return jdbcTemplate.queryForMap("SELECT * FROM job_applications WHERE id = ?", UUID.fromString(applicationId));
    }

    private int historyCount(String applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_status_history WHERE application_id = ?",
                Integer.class,
                UUID.fromString(applicationId));
    }

    // ---- T1 - gui lan dau: dung 1 thong bao APPLICATION_STATUS_CHANGED, khong INTERVIEW_INVITATION_RESENT (R-F2) ----

    @Test
    void t1_sendOneSlot_oneStatusChangedNotification_noResentNotification() throws Exception {
        Fixture fixture = createPendingApplication("t1-notif");

        MvcResult result = send(fixture, "/interview-invitation", bodyWithSlots(slot(inDays(3), "Tang 3")));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(notificationCount(fixture.candidateId(), "APPLICATION_STATUS_CHANGED")).isEqualTo(1);
        assertThat(notificationCount(fixture.candidateId(), "INTERVIEW_INVITATION_RESENT")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email_body FROM notifications WHERE user_id = ? AND type = 'APPLICATION_STATUS_CHANGED'",
                        String.class,
                        fixture.candidateId()))
                .isNull();
    }

    // ---- T5 ----

    @Test
    void t5_resendTwoSlots_oldSupersededUnchanged_applicationUnchanged_oneResentNotificationWithEmailBody()
            throws Exception {
        Fixture fixture = createPendingApplication("t5");
        assertThat(send(fixture, "/interview-invitation", bodyWithSlots(slot(inDays(2), "Phong cu")))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        Map<String, Object> oldBefore = invitations(fixture.applicationId()).get(0);
        List<Map<String, Object>> oldSlotsBefore = slotsOf(oldBefore.get("id"));
        Map<String, Object> applicationBefore = applicationRow(fixture.applicationId());
        int historyBefore = historyCount(fixture.applicationId());
        int statusNotificationsBefore = notificationCount(fixture.candidateId(), "APPLICATION_STATUS_CHANGED");
        Instant first = inDays(4).truncatedTo(ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS);
        Instant second = first.plus(1, ChronoUnit.DAYS).plus(5, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);

        MvcResult result = send(
                fixture,
                "/interview-invitation/resend",
                bodyWithSlots(
                        slot(first, "Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"),
                        slot(second, "https://meet.example.com/abc-defg-hij")));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        List<Map<String, Object>> after = invitations(fixture.applicationId());
        assertThat(after).hasSize(2);
        Map<String, Object> oldAfter = after.stream()
                .filter(row -> row.get("id").equals(oldBefore.get("id")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> created = after.stream()
                .filter(row -> !row.get("id").equals(oldBefore.get("id")))
                .findFirst()
                .orElseThrow();

        // Giay moi cu: superseded_at = sent_at cua giay moi moi, MOI cot khac va khung y het truoc (R-G5).
        assertThat(oldAfter.get("superseded_at")).isEqualTo(created.get("sent_at"));
        Map<String, Object> oldWithoutSuperseded = new HashMap<>(oldAfter);
        oldWithoutSuperseded.put("superseded_at", null);
        assertThat(oldWithoutSuperseded).isEqualTo(oldBefore);
        assertThat(slotsOf(oldBefore.get("id"))).isEqualTo(oldSlotsBefore);

        // Giay moi moi: hieu luc, chua chot (2 khung).
        assertThat(created.get("superseded_at")).isNull();
        assertThat(created.get("confirmed_slot_id")).isNull();
        assertThat(created.get("confirmed_at")).isNull();
        assertThat(slotsOf(created.get("id"))).hasSize(2);

        // Hang don: moi cot giong het tru updated_at (R-G4 - trigger doi updated_at, chap nhan); khong them lich su.
        Map<String, Object> applicationAfter = new HashMap<>(applicationRow(fixture.applicationId()));
        Map<String, Object> applicationBeforeCopy = new HashMap<>(applicationBefore);
        applicationAfter.remove("updated_at");
        applicationBeforeCopy.remove("updated_at");
        assertThat(applicationAfter).isEqualTo(applicationBeforeCopy);
        assertThat(historyCount(fixture.applicationId())).isEqualTo(historyBefore);

        // Thong bao: khong them APPLICATION_STATUS_CHANGED; dung 1 INTERVIEW_INVITATION_RESENT cho ung vien.
        assertThat(notificationCount(fixture.candidateId(), "APPLICATION_STATUS_CHANGED"))
                .isEqualTo(statusNotificationsBefore);
        List<Map<String, Object>> resent = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? AND type = 'INTERVIEW_INVITATION_RESENT'",
                fixture.candidateId());
        assertThat(resent).hasSize(1);
        Map<String, Object> notification = resent.get(0);
        assertThat(notification.get("email_status")).isEqualTo("PENDING");
        assertThat(notification.get("entity_type")).isEqualTo("APPLICATION");
        assertThat(notification.get("entity_id")).isEqualTo(UUID.fromString(fixture.applicationId()));
        assertThat(notification.get("link")).isEqualTo("/candidate/applications/" + fixture.applicationId());
        String body = (String) notification.get("body");
        assertThat(body).isEqualTo("Giấy mời phỏng vấn vị trí \"" + fixture.jobTitle()
                + "\" vừa được gửi lại với 2 khung giờ. Xem chi tiết trong đơn ứng tuyển.");
        assertThat(body).doesNotContain("\n").doesNotContain(VN_TIME.format(first)).doesNotContain(VN_TIME.format(second));
        assertThat(notification.get("email_body")).isEqualTo(body
                + "\n\nKhung giờ (giờ Việt Nam):"
                + "\n1. " + VN_TIME.format(first) + " — Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"
                + "\n2. " + VN_TIME.format(second) + " — https://meet.example.com/abc-defg-hij"
                + "\n\nXem giấy mời tại: http://localhost:5173/candidate/applications/" + fixture.applicationId());
        // HR khong nhan thong bao gui lai (R-N1).
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE type = 'INTERVIEW_INVITATION_RESENT' AND entity_id = ?",
                        Integer.class,
                        UUID.fromString(fixture.applicationId())))
                .isEqualTo(1);

        // API thong bao KHONG tra email_body (R-N3a).
        MvcResult notificationsResult = mockMvc
                .perform(get("/api/notifications").header("Authorization", "Bearer " + fixture.candidateToken()))
                .andReturn();
        assertThat(notificationsResult.getResponse().getStatus()).isEqualTo(200);
        String notificationsJson = notificationsResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(notificationsJson).contains("INTERVIEW_INVITATION_RESENT");
        assertThat(notificationsJson).doesNotContainIgnoringCase("emailBody").doesNotContainIgnoringCase("email_body");
    }

    // ---- T7, T8 - gui lai bi tu choi: khong thong bao nao duoc tao ----

    @Test
    void t7_resendOnPendingAndHiredApplications_noNotification() throws Exception {
        Fixture pending = createPendingApplication("t7n-pending");
        MvcResult pendingResult = send(pending, "/interview-invitation/resend", bodyWithSlots(slot(inDays(3), "Tang 3")));
        assertThat(pendingResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(notificationCount(pending.candidateId(), "INTERVIEW_INVITATION_RESENT")).isZero();

        Fixture hired = createPendingApplication("t7n-hired");
        assertThat(send(hired, "/interview-invitation", bodyWithSlots(slot(inDays(2), "Tang 3")))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        mockMvc.perform(patch("/api/hr/applications/" + hired.applicationId() + "/status")
                        .header("Authorization", "Bearer " + hired.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIRED\"}"))
                .andExpect(status().isOk());

        MvcResult hiredResult = send(hired, "/interview-invitation/resend", bodyWithSlots(slot(inDays(3), "Tang 3")));

        assertThat(hiredResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(JSON.readTree(hiredResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .get("error")
                        .asString())
                .isEqualTo("INVITATION_RESEND_NOT_ALLOWED");
        assertThat(notificationCount(hired.candidateId(), "INTERVIEW_INVITATION_RESENT")).isZero();
        assertThat(invitations(hired.applicationId())).hasSize(1);
        assertThat(invitations(hired.applicationId()).get(0).get("superseded_at")).isNull();
    }

    @Test
    void t8_resendWithInvalidSlots_noNotification_oldInvitationStillActive() throws Exception {
        Fixture fixture = createPendingApplication("t8n");
        assertThat(send(fixture, "/interview-invitation", bodyWithSlots(slot(inDays(2), "Tang 3")))
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);

        MvcResult result = send(
                fixture, "/interview-invitation/resend", bodyWithSlots(slot(Instant.now().minus(1, ChronoUnit.DAYS), "Tang 3")));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode json = JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.get("error").asString()).isEqualTo("INVALID_INTERVIEW_SCHEDULE");
        assertThat(notificationCount(fixture.candidateId(), "INTERVIEW_INVITATION_RESENT")).isZero();
        List<Map<String, Object>> rows = invitations(fixture.applicationId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("superseded_at")).isNull();
    }
}
