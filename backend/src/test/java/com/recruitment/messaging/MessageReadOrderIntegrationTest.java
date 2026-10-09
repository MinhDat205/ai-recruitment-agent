package com.recruitment.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C06 dot 2 (muc 12 L1) - T5 (thu tu tin) va T15 (da doc, unreadCount, nguong 200 tin cua R-M7).
// KHONG @Transactional cap lop (CLAUDE.md muc 3c): now() cua Postgres theo transaction - boc ca lop thi moi tin
// cung created_at va thu tu giua cac tin khong xac dinh. Moi request MockMvc o day tu commit rieng; du lieu de
// lai trong DB nhung moi test tu tao HR/ung vien/don rieng (email duy nhat) nen khong dung nhau.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageReadOrderIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationMessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

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

    private String registerHr(String email) throws Exception {
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

    private record Fixture(String hrToken, UUID hrUserId, String candidateToken, String applicationId) {

        String hrPath() {
            return "/api/hr/applications/" + applicationId + "/messages";
        }

        String candidatePath() {
            return "/api/candidates/applications/" + applicationId + "/messages";
        }

        UUID applicationUuid() {
            return UUID.fromString(applicationId);
        }
    }

    private Fixture createApplication(String prefix) throws Exception {
        String hrEmail = uniqueEmail(prefix + "-hr");
        String hrToken = registerHr(hrEmail);
        UUID hrUserId = userRepository.findByEmail(hrEmail).orElseThrow().getId();
        createCompany(hrToken, uniqueName("Cong ty " + prefix));
        String jobId = createJob(hrToken, uniqueName("Job " + prefix));
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        String resumeId = uploadResume(candidateToken);
        String applicationId = apply(candidateToken, jobId, resumeId);
        return new Fixture(hrToken, hrUserId, candidateToken, applicationId);
    }

    private String sendText(String token, String path, String text) throws Exception {
        MvcResult result = mockMvc
                .perform(multipart(path).param("body", text).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").asString();
    }

    private JsonNode getThread(String token, String path) throws Exception {
        MvcResult result = mockMvc
                .perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return body(result);
    }

    private void markRead(String token, String path) throws Exception {
        mockMvc.perform(patch(path + "/read").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private Instant readAt(String messageId) {
        return messageRepository.findById(UUID.fromString(messageId)).orElseThrow().getReadAt();
    }

    // Chen thang qua repository de dung nguong 200 tin (R-M7) ma khong goi 200 request HTTP. Moi tin cua HR,
    // chua doc; saveAll trong MOT transaction nen cung created_at - M1 sap tiep theo id (R-M7).
    private void insertHrMessages(Fixture f, int count) {
        List<ApplicationMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ApplicationMessage message = new ApplicationMessage();
            message.setApplicationId(f.applicationUuid());
            message.setSenderId(f.hrUserId());
            message.setSenderRole(MessageSenderRole.HR);
            message.setBody("Tin so " + i);
            messages.add(message);
        }
        messageRepository.saveAll(messages);
    }

    // ---- T5 - thu tu ----

    @Test
    void threadReturnsMessagesInSendOrder_createdAtNonDecreasing() throws Exception {
        Fixture f = createApplication("c06-t5");
        String first = sendText(f.hrToken(), f.hrPath(), "Tin 1 cua HR");
        String second = sendText(f.candidateToken(), f.candidatePath(), "Tin 2 cua ung vien");
        String third = sendText(f.hrToken(), f.hrPath(), "Tin 3 cua HR");

        for (JsonNode thread : List.of(
                getThread(f.hrToken(), f.hrPath()), getThread(f.candidateToken(), f.candidatePath()))) {
            JsonNode messages = thread.get("messages");
            assertThat(messages).hasSize(3);
            assertThat(List.of(
                            messages.get(0).get("id").asString(),
                            messages.get(1).get("id").asString(),
                            messages.get(2).get("id").asString()))
                    .containsExactly(first, second, third);
            Instant t1 = Instant.parse(messages.get(0).get("createdAt").asString());
            Instant t2 = Instant.parse(messages.get(1).get("createdAt").asString());
            Instant t3 = Instant.parse(messages.get(2).get("createdAt").asString());
            assertThat(t2).isAfterOrEqualTo(t1);
            assertThat(t3).isAfterOrEqualTo(t2);
        }
    }

    // ---- T15 - da doc ----

    @Test
    void unreadCountsOnlyOtherSide_andMarkReadSetsReadAtOfOtherSideOnly() throws Exception {
        Fixture f = createApplication("c06-t15-read");
        String hr1 = sendText(f.hrToken(), f.hrPath(), "HR 1");
        String hr2 = sendText(f.hrToken(), f.hrPath(), "HR 2");
        String own = sendText(f.candidateToken(), f.candidatePath(), "Tin ung vien tu gui");

        assertThat(getThread(f.candidateToken(), f.candidatePath()).get("unreadCount").asInt()).isEqualTo(2);
        assertThat(getThread(f.hrToken(), f.hrPath()).get("unreadCount").asInt()).isEqualTo(1);

        // Chi GET M1 (khong M3) KHONG doi read_at (R-R2).
        assertThat(readAt(hr1)).isNull();
        assertThat(readAt(hr2)).isNull();

        markRead(f.candidateToken(), f.candidatePath());
        assertThat(getThread(f.candidateToken(), f.candidatePath()).get("unreadCount").asInt()).isZero();
        Instant firstReadAt1 = readAt(hr1);
        Instant firstReadAt2 = readAt(hr2);
        assertThat(firstReadAt1).isNotNull();
        assertThat(firstReadAt2).isNotNull();
        // Tin ung vien tu gui KHONG bi M3 cua chinh ho danh dau; HR van con 1 tin chua doc.
        assertThat(readAt(own)).isNull();
        assertThat(getThread(f.hrToken(), f.hrPath()).get("unreadCount").asInt()).isEqualTo(1);

        // Goi lai M3: 204, read_at khong doi.
        markRead(f.candidateToken(), f.candidatePath());
        assertThat(readAt(hr1)).isEqualTo(firstReadAt1);
        assertThat(readAt(hr2)).isEqualTo(firstReadAt2);

        // Phia HR danh dau: chi tin cua ung vien.
        markRead(f.hrToken(), f.hrPath());
        assertThat(readAt(own)).isNotNull();
        assertThat(getThread(f.hrToken(), f.hrPath()).get("unreadCount").asInt()).isZero();
    }

    @Test
    void markReadWorksOnWithdrawnApplication() throws Exception {
        Fixture f = createApplication("c06-t15-wd");
        String hrMessage = sendText(f.hrToken(), f.hrPath(), "Tin truoc khi rut don");
        JobApplication application = jobApplicationRepository.findById(f.applicationUuid()).orElseThrow();
        application.setStatus(ApplicationStatus.WITHDRAWN);
        jobApplicationRepository.saveAndFlush(application);

        assertThat(getThread(f.candidateToken(), f.candidatePath()).get("unreadCount").asInt()).isEqualTo(1);
        markRead(f.candidateToken(), f.candidatePath());
        assertThat(getThread(f.candidateToken(), f.candidatePath()).get("unreadCount").asInt()).isZero();
        assertThat(readAt(hrMessage)).isNotNull();
    }

    // R-M7 - nguong 200 tin: 199 / 200 / 201. unreadCount dem tren TOAN BO tin, khong chi 200 tin duoc tra.
    @Test
    void threadLimitBoundary_199And200NotHidden_201Hidden_unreadCountsAll() throws Exception {
        Fixture f = createApplication("c06-t15-limit");

        insertHrMessages(f, 199);
        JsonNode at199 = getThread(f.candidateToken(), f.candidatePath());
        assertThat(at199.get("messages")).hasSize(199);
        assertThat(at199.get("olderMessagesHidden").asBoolean()).isFalse();
        assertThat(at199.get("unreadCount").asInt()).isEqualTo(199);

        insertHrMessages(f, 1);
        JsonNode at200 = getThread(f.candidateToken(), f.candidatePath());
        assertThat(at200.get("messages")).hasSize(200);
        assertThat(at200.get("olderMessagesHidden").asBoolean()).isFalse();
        assertThat(at200.get("unreadCount").asInt()).isEqualTo(200);

        String newest = sendText(f.hrToken(), f.hrPath(), "Tin thu 201");
        JsonNode at201 = getThread(f.candidateToken(), f.candidatePath());
        JsonNode messages = at201.get("messages");
        assertThat(messages).hasSize(200);
        assertThat(at201.get("olderMessagesHidden").asBoolean()).isTrue();
        assertThat(at201.get("unreadCount").asInt()).isEqualTo(201);
        // Tin moi nhat nam CUOI danh sach (thu tu tang dan), tin cu nhat bi an.
        assertThat(messages.get(199).get("id").asString()).isEqualTo(newest);
        for (int i = 1; i < messages.size(); i++) {
            Instant previous = Instant.parse(messages.get(i - 1).get("createdAt").asString());
            Instant current = Instant.parse(messages.get(i).get("createdAt").asString());
            assertThat(current).isAfterOrEqualTo(previous);
        }
    }
}
