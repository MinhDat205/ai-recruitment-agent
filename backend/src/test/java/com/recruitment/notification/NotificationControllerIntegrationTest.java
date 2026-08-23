package com.recruitment.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

// Mau y het ApplicationStatusControllerIntegrationTest (helper dang ky/dang nhap rieng, khong tach
// util dung chung - dung tien le cua toan bo test file trong du an). Dot nay chua co
// publisher/listener nao ghi notifications (xem Dot 2) nen seed truc tiep qua NotificationRepository.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

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
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    // Tra ve (token, userId) - dang ky candidate vi endpoint /api/notifications dung chung ca 2
    // role, chi can 1 phia de kiem tra hanh vi, khong phu thuoc HR/CANDIDATE.
    private record RegisteredUser(String token, UUID userId) {
    }

    private RegisteredUser registerAndLoginCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien Test"}
                """
                        .formatted(email);
        MvcResult registerResult = mockMvc
                .perform(post("/api/auth/register/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID userId = UUID.fromString(extractJsonField(registerResult.getResponse().getContentAsString(), "id"));
        return new RegisteredUser(login(email), userId);
    }

    private Notification seedNotification(UUID userId, boolean read) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(NotificationType.APPLICATION_STATUS_CHANGED);
        n.setTitle("Đơn ứng tuyển của bạn đã được cập nhật");
        n.setBody("Trạng thái đơn cho vị trí Backend Developer đã chuyển sang Đã mời phỏng vấn");
        n.setLink("/candidate/applications");
        n.setEntityType("APPLICATION");
        n.setEntityId(UUID.randomUUID());
        n.setRead(read);
        if (read) {
            n.setReadAt(Instant.now());
        }
        return notificationRepository.save(n);
    }

    // ---- Case duong ----

    @Test
    void list_happyPath_returnsOnlyOwnNotificationsAndUnreadCount() throws Exception {
        RegisteredUser owner = registerAndLoginCandidate("notif-owner");
        RegisteredUser other = registerAndLoginCandidate("notif-other");
        seedNotification(owner.userId(), false);
        seedNotification(owner.userId(), true);
        seedNotification(other.userId(), false);

        MvcResult result = mockMvc
                .perform(get("/api/notifications").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"unreadCount\":1");
        assertThat(body).doesNotContain(other.userId().toString());
    }

    @Test
    void markRead_happyPath_setsReadTrueAndReadAt() throws Exception {
        RegisteredUser owner = registerAndLoginCandidate("notif-read-owner");
        Notification notification = seedNotification(owner.userId(), false);

        MvcResult result = mockMvc
                .perform(patch("/api/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + owner.token()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"isRead\":true");

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isTrue();
        assertThat(reloaded.getReadAt()).isNotNull();
    }

    // ---- Case am ----

    @Test
    void markRead_otherUsersNotification_returns403() throws Exception {
        RegisteredUser owner = registerAndLoginCandidate("notif-403-owner");
        RegisteredUser intruder = registerAndLoginCandidate("notif-403-intruder");
        Notification notification = seedNotification(owner.userId(), false);

        MvcResult result = mockMvc
                .perform(patch("/api/notifications/" + notification.getId() + "/read")
                        .header("Authorization", "Bearer " + intruder.token()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.isRead()).isFalse();
    }

    @Test
    void markRead_notFound_returns404() throws Exception {
        RegisteredUser owner = registerAndLoginCandidate("notif-404-owner");

        MvcResult result = mockMvc
                .perform(patch("/api/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer " + owner.token()))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }
}
