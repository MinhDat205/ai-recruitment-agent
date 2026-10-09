package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.notification.EmailStatus;
import com.recruitment.notification.Notification;
import com.recruitment.notification.NotificationRepository;
import com.recruitment.notification.NotificationType;
import com.recruitment.user.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// FR-C06 dot 4 (muc 12 L1) - T18 (thong bao tin moi), T19 (gop thong bao + R-R3), T20 (tin chi co tep, doan
// trich). KHONG @Transactional cap lop: listener onMessageSent la AFTER_COMMIT - chi chay khi transaction cua
// M2 THAT SU commit (mau NotificationEventListenerIntegrationTest). Poller email tat trong test
// (app.notification.enabled=false) nen email_status cua thong bao moi giu PENDING.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageNotificationIntegrationTest {

    private static final byte[] PDF = Arrays.copyOf(new byte[] {0x25, 0x50, 0x44, 0x46}, 40);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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

    private record Account(String token, UUID userId, String fullName) {}

    private Account registerHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String fullName = uniqueName("Nha Tuyen Dung");
        mockMvc
                .perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","phone":"0900000000"}
                                """.formatted(email, fullName)))
                .andExpect(status().isCreated());
        return new Account(login(email), userRepository.findByEmail(email).orElseThrow().getId(), fullName);
    }

    private Account registerCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String fullName = uniqueName("Ung Vien");
        mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s"}
                                """.formatted(email, fullName)))
                .andExpect(status().isCreated());
        return new Account(login(email), userRepository.findByEmail(email).orElseThrow().getId(), fullName);
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

    private record Fixture(Account hr, Account candidate, String companyName, String jobTitle, String applicationId) {

        UUID applicationUuid() {
            return UUID.fromString(applicationId);
        }

        String hrPath() {
            return "/api/hr/applications/" + applicationId + "/messages";
        }

        String candidatePath() {
            return "/api/candidates/applications/" + applicationId + "/messages";
        }
    }

    private Fixture createApplication(String prefix) throws Exception {
        Account hr = registerHr(prefix + "-hr");
        String companyName = uniqueName("Cong ty " + prefix);
        createCompany(hr.token(), companyName);
        String jobTitle = uniqueName("Job " + prefix);
        String jobId = createJob(hr.token(), jobTitle);
        addCriterion(hr.token(), jobId);
        openJob(hr.token(), jobId);
        Account candidate = registerCandidate(prefix + "-cand");
        String applicationId = apply(candidate.token(), jobId, uploadResume(candidate.token()));
        return new Fixture(hr, candidate, companyName, jobTitle, applicationId);
    }

    private void sendText(String token, String path, String text) throws Exception {
        mockMvc.perform(multipart(path).param("body", text).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void sendFileOnly(String token, String path) throws Exception {
        mockMvc.perform(multipart(path)
                        .file(new MockMultipartFile("file", "bang-cap.pdf", "application/pdf", PDF))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    private void markRead(String token, String path) throws Exception {
        mockMvc.perform(patch(path + "/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private List<Notification> notificationsOf(UUID userId) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100))
                .getContent();
    }

    private List<Notification> newMessageNotificationsOf(UUID userId) {
        return notificationsOf(userId).stream()
                .filter(n -> n.getType() == NotificationType.NEW_MESSAGE)
                .toList();
    }

    // ---- T18 ----

    @Test
    void hrMessage_createsOneNotificationForCandidate_andNoneForHr() throws Exception {
        Fixture f = createApplication("c06-t18-hr");
        int hrNotificationsBefore = notificationsOf(f.hr().userId()).size();

        sendText(f.hr().token(), f.hrPath(), "Chào bạn,\nhồ sơ của bạn đang được xem xét.");

        List<Notification> candidateNotifications = newMessageNotificationsOf(f.candidate().userId());
        assertThat(candidateNotifications).hasSize(1);
        Notification n = candidateNotifications.get(0);
        assertThat(n.getTitle()).isEqualTo("Tin nhắn mới từ nhà tuyển dụng");
        assertThat(n.getBody()).isEqualTo(f.companyName() + " đã gửi tin nhắn về đơn ứng tuyển vị trí \""
                + f.jobTitle() + "\": Chào bạn, hồ sơ của bạn đang được xem xét.");
        assertThat(n.getLink()).isEqualTo("/candidate/applications/" + f.applicationId() + "?tab=messages");
        assertThat(n.getEntityType()).isEqualTo("APPLICATION");
        assertThat(n.getEntityId()).isEqualTo(f.applicationUuid());
        assertThat(n.getEmailStatus()).isEqualTo(EmailStatus.PENDING);
        assertThat(n.isRead()).isFalse();
        // Thong bao cho ung vien KHONG chua ho ten HR (R-I2, R-G2).
        assertThat(n.getBody()).doesNotContain(f.hr().fullName());

        assertThat(notificationsOf(f.hr().userId())).hasSize(hrNotificationsBefore);
    }

    @Test
    void candidateMessage_createsOneNotificationForCompanyOwner_andNoneForCandidate() throws Exception {
        Fixture f = createApplication("c06-t18-uv");
        int candidateNotificationsBefore = notificationsOf(f.candidate().userId()).size();

        sendText(f.candidate().token(), f.candidatePath(), "Em muốn hỏi về tiến độ hồ sơ ạ.");

        List<Notification> hrNotifications = newMessageNotificationsOf(f.hr().userId());
        assertThat(hrNotifications).hasSize(1);
        Notification n = hrNotifications.get(0);
        assertThat(n.getTitle()).isEqualTo("Tin nhắn mới từ ứng viên");
        assertThat(n.getBody()).isEqualTo("Ứng viên " + f.candidate().fullName()
                + " đã gửi tin nhắn về đơn ứng tuyển vị trí \"" + f.jobTitle() + "\": Em muốn hỏi về tiến độ hồ sơ ạ.");
        assertThat(n.getLink()).isEqualTo("/hr/applications/" + f.applicationId() + "?tab=messages");
        assertThat(n.getEntityId()).isEqualTo(f.applicationUuid());
        assertThat(n.getEmailStatus()).isEqualTo(EmailStatus.PENDING);

        assertThat(notificationsOf(f.candidate().userId())).hasSize(candidateNotificationsBefore);
    }

    // ---- T19 - gop thong bao (R-N4) + M3 danh dau thong bao (R-R3) ----

    @Test
    void consecutiveMessages_areMergedUntilRead_thenANewNotificationIsCreated() throws Exception {
        Fixture f = createApplication("c06-t19-merge");
        sendText(f.hr().token(), f.hrPath(), "Tin 1");
        sendText(f.hr().token(), f.hrPath(), "Tin 2");
        sendText(f.hr().token(), f.hrPath(), "Tin 3");

        List<Notification> merged = newMessageNotificationsOf(f.candidate().userId());
        assertThat(merged).hasSize(1);
        UUID firstId = merged.get(0).getId();
        // Noi dung giu theo tin DAU TIEN - khong cap nhat theo tin sau.
        assertThat(merged.get(0).getBody()).endsWith(": Tin 1");

        markRead(f.candidate().token(), f.candidatePath());
        Notification afterRead = notificationRepository.findById(firstId).orElseThrow();
        assertThat(afterRead.isRead()).isTrue();
        assertThat(afterRead.getReadAt()).isNotNull();

        sendText(f.hr().token(), f.hrPath(), "Tin 4");
        List<Notification> afterFourth = newMessageNotificationsOf(f.candidate().userId());
        assertThat(afterFourth).hasSize(2);
        Notification second = afterFourth.stream().filter(n -> !n.getId().equals(firstId)).findFirst().orElseThrow();
        assertThat(second.isRead()).isFalse();
        assertThat(second.getBody()).endsWith(": Tin 4");
    }

    @Test
    void markRead_onlyTouchesCallersNewMessageNotificationsOfThatApplication() throws Exception {
        Fixture f = createApplication("c06-t19-scope");
        String secondJob = createJob(f.hr().token(), uniqueName("Job c06-t19-scope-2"));
        addCriterion(f.hr().token(), secondJob);
        openJob(f.hr().token(), secondJob);
        String secondApplication = apply(f.candidate().token(), secondJob, uploadResume(f.candidate().token()));

        // Thong bao loai khac cua CUNG don (gia lap thong bao doi trang thai) - M3 KHONG duoc danh dau no.
        Notification other = new Notification();
        other.setUserId(f.candidate().userId());
        other.setType(NotificationType.APPLICATION_STATUS_CHANGED);
        other.setTitle("Cập nhật đơn ứng tuyển");
        other.setBody("Đơn ứng tuyển đã chuyển sang trạng thái: Đã mời phỏng vấn");
        other.setLink("/candidate/applications/" + f.applicationId());
        other.setEntityType("APPLICATION");
        other.setEntityId(f.applicationUuid());
        other.setRead(false);
        other.setEmailStatus(EmailStatus.PENDING);
        other = notificationRepository.save(other);

        sendText(f.hr().token(), f.hrPath(), "Tin don 1");
        sendText(f.hr().token(), "/api/hr/applications/" + secondApplication + "/messages", "Tin don 2");
        sendText(f.candidate().token(), f.candidatePath(), "Ung vien tra loi don 1");

        markRead(f.candidate().token(), f.candidatePath());

        List<Notification> candidateNew = newMessageNotificationsOf(f.candidate().userId());
        Notification ofFirst = candidateNew.stream()
                .filter(n -> n.getEntityId().equals(f.applicationUuid())).findFirst().orElseThrow();
        Notification ofSecond = candidateNew.stream()
                .filter(n -> n.getEntityId().equals(UUID.fromString(secondApplication))).findFirst().orElseThrow();
        assertThat(ofFirst.isRead()).isTrue();
        assertThat(ofSecond.isRead()).isFalse();
        assertThat(notificationRepository.findById(other.getId()).orElseThrow().isRead()).isFalse();
        // Thong bao NEW_MESSAGE cua HR (nguoi khac) cho cung don KHONG bi M3 cua ung vien danh dau.
        assertThat(newMessageNotificationsOf(f.hr().userId()).get(0).isRead()).isFalse();

        // Phia HR goi M3 -> thong bao cua HR duoc danh dau.
        markRead(f.hr().token(), f.hrPath());
        assertThat(newMessageNotificationsOf(f.hr().userId()).get(0).isRead()).isTrue();
    }

    // ---- T20 - tin chi co tep; doan trich 120 ky tu ----

    @Test
    void fileOnlyMessage_usesAttachmentWording_withoutExcerpt() throws Exception {
        Fixture f = createApplication("c06-t20-file");

        sendFileOnly(f.candidate().token(), f.candidatePath());

        Notification n = newMessageNotificationsOf(f.hr().userId()).get(0);
        assertThat(n.getBody()).isEqualTo("Ứng viên " + f.candidate().fullName()
                + " đã gửi một tệp đính kèm về đơn ứng tuyển vị trí \"" + f.jobTitle() + "\"");

        sendFileOnly(f.hr().token(), f.hrPath());
        Notification toCandidate = newMessageNotificationsOf(f.candidate().userId()).get(0);
        assertThat(toCandidate.getBody()).isEqualTo(f.companyName()
                + " đã gửi một tệp đính kèm về đơn ứng tuyển vị trí \"" + f.jobTitle() + "\"");
    }

    @Test
    void longMessage_bodyEndsWithFirst120CharsAndEllipsis() throws Exception {
        Fixture f = createApplication("c06-t20-long");
        String text = "x".repeat(300);

        sendText(f.hr().token(), f.hrPath(), text);

        Notification n = newMessageNotificationsOf(f.candidate().userId()).get(0);
        assertThat(n.getBody()).endsWith("\": " + "x".repeat(120) + "…");
    }
}
