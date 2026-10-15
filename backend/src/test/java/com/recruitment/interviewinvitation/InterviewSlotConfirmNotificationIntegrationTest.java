package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

// FR-U10 T10 (R-N) - thong bao INTERVIEW_SLOT_CONFIRMED. KHONG @Transactional toan lop: thong bao tao o listener
// AFTER_COMMIT (R-N1) chi chay khi transaction cua service THAT SU commit - khuon InterviewInvitationResendIntegrationTest.
// Du lieu ngau nhien rieng (email/ten UUID), chi khang dinh tren du lieu cua chinh test, khong don sau test. Poller email
// tat trong profile test (app.notification.enabled=false) nen email_status cua thong bao moi giu PENDING. Helper chep theo
// InterviewInvitationResendIntegrationTest (tien le du an: khong tach tien ich dung chung).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewSlotConfirmNotificationIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter VN_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String CANDIDATE_NAME = "Nguyen Van Ung Vien";
    private static final String TYPE = "INTERVIEW_SLOT_CONFIRMED";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InterviewInvitationService invitationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"%s\"}"
                                .formatted(email, CANDIDATE_NAME)))
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

    private record Fixture(
            String hrToken, String candidateToken, UUID ownerId, UUID candidateId, String jobTitle, String applicationId) {

        UUID applicationUuid() {
            return UUID.fromString(applicationId);
        }
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
        Map<String, Object> ids = jdbcTemplate.queryForMap(
                """
                SELECT a.candidate_id, c.owner_id FROM job_applications a
                JOIN jobs j ON j.id = a.job_id JOIN companies c ON c.id = j.company_id
                WHERE a.id = ?
                """,
                UUID.fromString(applicationId));
        return new Fixture(
                hrToken,
                candidateToken,
                (UUID) ids.get("owner_id"),
                (UUID) ids.get("candidate_id"),
                jobTitle,
                applicationId);
    }

    private static String slot(Instant at, String location) {
        return "{\"scheduledAt\":\"" + at + "\",\"location\":\"" + location + "\"}";
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    // Don INTERVIEW_INVITED qua I2 that voi 3 khung tuong lai (dia diem tieng Viet o khung 2), CHUA chot.
    private Fixture createInvitedApplication(String prefix) throws Exception {
        Fixture fixture = createPendingApplication(prefix);
        Instant first = inDays(4);
        mockMvc.perform(post("/api/hr/applications/" + fixture.applicationId() + "/interview-invitation")
                        .header("Authorization", "Bearer " + fixture.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[" + String.join(
                                        ",",
                                        slot(first, "Phong 1"),
                                        slot(first.plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES),
                                                "Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"),
                                        slot(first.plus(2, ChronoUnit.DAYS), "https://meet.example.com/abc-defg-hij"))
                                + "],\"subject\":\"Thu moi phong van\",\"content\":\"Noi dung\"}"))
                .andExpect(status().isCreated());
        return fixture;
    }

    private List<Map<String, Object>> activeSlots(Fixture fixture) {
        return jdbcTemplate.queryForList(
                """
                SELECT s.id, s.scheduled_at, s.location FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id
                WHERE i.application_id = ? AND i.superseded_at IS NULL ORDER BY s.display_order
                """,
                fixture.applicationUuid());
    }

    private MvcResult confirm(Fixture fixture, String token, String body) throws Exception {
        return mockMvc
                .perform(post("/api/candidates/applications/" + fixture.applicationId()
                                + "/interview-invitation/confirm-slot")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult confirm(Fixture fixture, Object slotId) throws Exception {
        return confirm(fixture, fixture.candidateToken(), "{\"slotId\":\"" + slotId + "\"}");
    }

    private static String errorCode(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("error")
                .asString();
    }

    // Moi thong bao INTERVIEW_SLOT_CONFIRMED gan voi don (bat ke nguoi nhan).
    private int slotConfirmedCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type = ? AND entity_id = ?",
                Integer.class,
                TYPE,
                fixture.applicationUuid());
    }

    private int notificationCountOfUser(UUID userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ?", Integer.class, userId);
    }

    // ---- T10 - chon thanh cong: dung MOT thong bao cho chu cong ty, noi dung dung R-N3/R-N4 ----

    @Test
    void t10_confirmSuccess_oneNotificationForCompanyOwner_exactContent_candidateGetsNone() throws Exception {
        Fixture fixture = createInvitedApplication("t10-ok");
        Map<String, Object> chosen = activeSlots(fixture).get(1);
        Instant chosenAt = ((Timestamp) chosen.get("scheduled_at")).toInstant();
        int candidateNotificationsBefore = notificationCountOfUser(fixture.candidateId());

        MvcResult result = confirm(fixture, chosen.get("id"));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE type = ? AND entity_id = ?", TYPE, fixture.applicationUuid());
        assertThat(rows).hasSize(1);
        Map<String, Object> notification = rows.get(0);
        assertThat(notification.get("user_id")).isEqualTo(fixture.ownerId());
        assertThat(notification.get("entity_type")).isEqualTo("APPLICATION");
        assertThat(notification.get("entity_id")).isEqualTo(fixture.applicationUuid());
        assertThat(notification.get("link")).isEqualTo("/hr/applications/" + fixture.applicationId() + "?tab=invitation");
        assertThat(notification.get("email_status")).isEqualTo("PENDING");
        assertThat(notification.get("is_read")).isEqualTo(false);
        assertThat(notification.get("title")).isEqualTo("Ứng viên đã chọn lịch phỏng vấn");
        String body = (String) notification.get("body");
        assertThat(body)
                .isEqualTo("Ứng viên " + CANDIDATE_NAME + " đã chọn khung giờ phỏng vấn " + VN_TIME.format(chosenAt)
                        + " (giờ Việt Nam) cho vị trí \"" + fixture.jobTitle() + "\".")
                .doesNotContain("\n")
                .doesNotContain("Bạch Đằng");
        assertThat(notification.get("email_body"))
                .isEqualTo(body
                        + "\n\nĐịa điểm: Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"
                        + "\n\nXem giấy mời tại: http://localhost:5173/hr/applications/" + fixture.applicationId()
                        + "?tab=invitation");

        // Ung vien KHONG nhan thong bao nao moi (Q7).
        assertThat(notificationCountOfUser(fixture.candidateId())).isEqualTo(candidateNotificationsBefore);

        // API thong bao cua HR co loai moi, KHONG tra email_body (H12 R-N3a).
        MvcResult notificationsResult = mockMvc
                .perform(get("/api/notifications").header("Authorization", "Bearer " + fixture.hrToken()))
                .andReturn();
        assertThat(notificationsResult.getResponse().getStatus()).isEqualTo(200);
        String notificationsJson = notificationsResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(notificationsJson).contains(TYPE);
        assertThat(notificationsJson).doesNotContainIgnoringCase("emailBody").doesNotContainIgnoringCase("email_body");
    }

    // ---- T10 - luot 409 PASSED va 409 ALREADY_CONFIRMED khong tao thong bao ----

    @Test
    void t10_passedAndAlreadyConfirmed_noExtraNotification() throws Exception {
        Fixture fixture = createInvitedApplication("t10-409");
        List<Map<String, Object>> slots = activeSlots(fixture);
        jdbcTemplate.update(
                "UPDATE interview_invitation_slots SET scheduled_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)),
                slots.get(0).get("id"));

        MvcResult passed = confirm(fixture, slots.get(0).get("id"));
        assertThat(passed.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(passed)).isEqualTo("INTERVIEW_SLOT_PASSED");
        assertThat(slotConfirmedCount(fixture)).isZero();

        assertThat(confirm(fixture, slots.get(1).get("id")).getResponse().getStatus()).isEqualTo(200);
        assertThat(slotConfirmedCount(fixture)).isEqualTo(1);

        MvcResult again = confirm(fixture, slots.get(2).get("id"));
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("INTERVIEW_SLOT_ALREADY_CONFIRMED");
        MvcResult sameSlot = confirm(fixture, slots.get(1).get("id"));
        assertThat(sameSlot.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(sameSlot)).isEqualTo("INTERVIEW_SLOT_ALREADY_CONFIRMED");
        assertThat(slotConfirmedCount(fixture)).isEqualTo(1);
    }

    // ---- R-N5 - cac ma loi khac cung khong tao thong bao ----

    @Test
    void t10_otherErrorCodes_noNotification() throws Exception {
        Fixture fixture = createInvitedApplication("t10-err");
        UUID slotId = (UUID) activeSlots(fixture).get(1).get("id");
        String otherCandidateToken = registerAndLoginCandidate("t10-err-other");

        assertThat(confirm(fixture, otherCandidateToken, "{\"slotId\":\"" + slotId + "\"}")
                        .getResponse()
                        .getStatus())
                .isEqualTo(403);
        assertThat(confirm(fixture, fixture.candidateToken(), "{}").getResponse().getStatus())
                .isEqualTo(400);
        MvcResult conflict = confirm(fixture, UUID.randomUUID());
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(conflict)).isEqualTo("INTERVIEW_INVITATION_CONFLICT");
        assertThat(slotConfirmedCount(fixture)).isZero();

        mockMvc.perform(patch("/api/hr/applications/" + fixture.applicationId() + "/status")
                        .header("Authorization", "Bearer " + fixture.hrToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIRED\"}"))
                .andExpect(status().isOk());
        MvcResult notAllowed = confirm(fixture, slotId);
        assertThat(notAllowed.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(notAllowed)).isEqualTo("SLOT_SELECTION_NOT_ALLOWED");
        assertThat(slotConfirmedCount(fixture)).isZero();
        assertThat(notificationCountOfUser(fixture.ownerId()))
                .as("HR chi co thong bao don moi")
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'APPLICATION_SUBMITTED'",
                        Integer.class,
                        fixture.ownerId()));
    }

    // ---- Su kien da phat nhung transaction rollback -> AFTER_COMMIT khong chay, khong thong bao, khong ghi khung chot ----

    @Test
    void t10_confirmInsideRolledBackTransaction_noNotification_nothingConfirmed() throws Exception {
        Fixture fixture = createInvitedApplication("t10-rb");
        UUID slotId = (UUID) activeSlots(fixture).get(1).get("id");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // confirmSlot (REQUIRED) tham gia transaction ngoai: UPDATE tra 1 dong, su kien duoc phat ...
            invitationService.confirmSlot(fixture.candidateId(), fixture.applicationUuid(), slotId);
            // ... roi transaction ngoai rollback.
            status.setRollbackOnly();
        });

        assertThat(slotConfirmedCount(fixture)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ? AND confirmed_slot_id IS NOT NULL",
                        Integer.class,
                        fixture.applicationUuid()))
                .isZero();
    }
}
