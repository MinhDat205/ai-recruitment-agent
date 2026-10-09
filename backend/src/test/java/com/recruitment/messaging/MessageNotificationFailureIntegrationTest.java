package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.notification.Notification;
import com.recruitment.notification.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 T26 (muc 12 L7) - loi tao thong bao KHONG lam hong viec gui tin (R-N1). NotificationRepository la
// @MockitoSpyBean (spy goi that, tru save() bi ep nem loi) - lop nay vi vay co Spring context RIENG (chap nhan,
// L7). KHONG @Transactional cap lop: listener onMessageSent la AFTER_COMMIT, chi chay khi M2 that su commit.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageNotificationFailureIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationMessageRepository messageRepository;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @AfterEach
    void resetSpy() {
        reset(notificationRepository);
    }

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
        MvcResult loginResult = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
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

    private String createApplication(String prefix, String[] tokens) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        mockMvc.perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueName("Cong ty " + prefix))))
                .andExpect(status().isCreated());
        String title = uniqueName("Job " + prefix);
        MvcResult job = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                                  "interviewTemplate": {
                                    "subject":"Thu moi phong van vi tri %s",
                                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                                    "senderName":"Phong Nhan Su"
                                  }
                                }
                                """.formatted(title, title)))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = extractJsonField(job.getResponse().getContentAsString(), "id");
        mockMvc.perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        MvcResult resume = mockMvc
                .perform(multipart("/api/candidates/resumes")
                        .file(new MockMultipartFile(
                                "file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes()))
                        .header("Authorization", "Bearer " + candidateToken))
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
        tokens[0] = hrToken;
        tokens[1] = candidateToken;
        return extractJsonField(application.getResponse().getContentAsString(), "id");
    }

    @Test
    void notificationSaveFailure_doesNotBreakSendingMessage() throws Exception {
        String[] tokens = new String[2];
        String applicationId = createApplication("c06-t26", tokens);
        String hrPath = "/api/hr/applications/" + applicationId + "/messages";
        String candidatePath = "/api/candidates/applications/" + applicationId + "/messages";
        doThrow(new RuntimeException("Gia lap loi ghi thong bao"))
                .when(notificationRepository)
                .save(any(Notification.class));

        MvcResult sent = mockMvc
                .perform(multipart(hrPath).param("body", "Tin van phai gui duoc").header("Authorization", "Bearer " + tokens[0]))
                .andReturn();

        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        UUID messageId = UUID.fromString(body(sent).get("id").asString());
        assertThat(messageRepository.findById(messageId)).isPresent();
        // Listener DA chay va DA goi save (bi ep loi) - chung minh loi xay ra that, khong phai listener bi bo qua.
        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));

        MvcResult thread = mockMvc
                .perform(get(candidatePath).header("Authorization", "Bearer " + tokens[1]))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode messages = body(thread).get("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).get("id").asString()).isEqualTo(messageId.toString());
        assertThat(messages.get(0).get("body").asString()).isEqualTo("Tin van phai gui duoc");
    }
}
