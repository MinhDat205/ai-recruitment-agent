package com.recruitment.messagedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anthropic.models.messages.StopReason;
import com.recruitment.TestcontainersConfiguration;
import com.recruitment.interviewinvitation.InterviewInvitation;
import com.recruitment.interviewinvitation.InterviewInvitationRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.messaging.ApplicationMessage;
import com.recruitment.messaging.ApplicationMessageRepository;
import com.recruitment.messaging.AttachmentType;
import com.recruitment.messaging.MessageSenderRole;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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

// FR-C07 dot 4 - bo helper dung chung cho cac lop test tich hop cua messagedraft/ (thay vi chep lai o tung lop). Cung
// cau hinh context voi JobRecommendationCandidateControllerIntegrationTest (Testcontainers + LlmTestConfiguration +
// MockMvc + profile test) -> dung chung context cache.
//
// KHONG @Transactional cap lop: (1) A2 thanh cong goi K3, ma K3 nem IllegalStateException neu luong request dang co
// transaction (R-K3-4) - MockMvc chay tren cung luong test nen transaction cua test se bi K3 bat; (2) thu tu tin can
// created_at khac nhau (CLAUDE.md muc 3c) - backdate tuong minh. Du lieu moi test tu tao, email duy nhat.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class MessageDraftIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";
    static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");
    static final String DEFAULT_DRAFT = "Xin chào, đây là bản nháp thử nghiệm.";

    enum Side {
        HR("/api/hr/applications/"),
        CANDIDATE("/api/candidates/applications/");

        private final String prefix;

        Side(String prefix) {
            this.prefix = prefix;
        }

        String draftPath(Object applicationId) {
            return prefix + applicationId + "/messages/ai-draft";
        }

        String scenariosPath(Object applicationId) {
            return draftPath(applicationId) + "/scenarios";
        }

        String messagesPath(Object applicationId) {
            return prefix + applicationId + "/messages";
        }
    }

    record Fixture(
            UUID applicationId,
            String hrToken,
            String candidateToken,
            UUID hrUserId,
            UUID candidateUserId,
            String hrEmail,
            String hrFullName,
            String candidateEmail,
            String candidateName,
            String jobTitle,
            String companyName) {

        String token(Side side) {
            return side == Side.HR ? hrToken : candidateToken;
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatModel chatModel;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ApplicationMessageRepository messageRepository;

    @Autowired
    InterviewInvitationRepository invitationRepository;

    // Mock ChatModel la singleton dung chung moi lop - reset truoc moi test (giu default answer "nem loi khi chua stub"
    // cua LlmTestConfiguration). getOptions/getDefaultOptions duoc ChatClient doc khi dung Prompt.
    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
    }

    // ---- mock ChatModel ----

    static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(
                        new AssistantMessage(text),
                        ChatGenerationMetadata.builder()
                                .finishReason(StopReason.END_TURN.toString())
                                .build())))
                .metadata(ChatResponseMetadata.builder().model("claude-test").build())
                .build();
    }

    static String draftJson(String draft) {
        return JSON.writeValueAsString(Map.of("draft", draft));
    }

    void stubDraft(String draft) {
        doReturn(chatResponse(draftJson(draft))).when(chatModel).call(any(Prompt.class));
    }

    List<Prompt> capturedPrompts(int expectedCalls) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(expectedCalls)).call(captor.capture());
        return captor.getAllValues();
    }

    static String systemText(Prompt prompt) {
        return prompt.getSystemMessage().getText();
    }

    static String userText(Prompt prompt) {
        return prompt.getUserMessage().getText();
    }

    // ---- HTTP ----

    MvcResult getScenarios(Side side, Object applicationId, String token) throws Exception {
        var request = get(side.scenariosPath(applicationId));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult postDraft(Side side, Object applicationId, String token, String rawJson) throws Exception {
        var request = post(side.draftPath(applicationId)).contentType(MediaType.APPLICATION_JSON).content(rawJson);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult postDraft(Side side, Object applicationId, String token, String scenario, String tone) throws Exception {
        return postDraft(side, applicationId, token, requestJson(scenario, tone, null));
    }

    static String requestJson(String scenario, String tone, String customPurpose) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (scenario != null) {
            body.put("scenario", scenario);
        }
        if (tone != null) {
            body.put("tone", tone);
        }
        if (customPurpose != null) {
            body.put("customPurpose", customPurpose);
        }
        return JSON.writeValueAsString(body);
    }

    static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static String rawBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static void assertError(MvcResult result, int httpStatus, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(httpStatus);
        assertThat(body(result).get("error").asString()).isEqualTo(code);
    }

    static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertError(result, httpStatus, code);
        assertThat(body(result).get("message").asString()).isEqualTo(message);
    }

    static void assertDraft(MvcResult result, String expectedDraft) throws Exception {
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(200);
        assertThat(body(result).get("draft").asString()).isEqualTo(expectedDraft);
    }

    // ---- du lieu ----

    // prefix <= 27 ky tu (no uniqueEmail cua FR-H09).
    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    static String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    String login(String email) throws Exception {
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

    String register(String role, String email, String fullName) throws Exception {
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","phone":"0900000000"}
                                """.formatted(email, fullName)))
                .andExpect(status().isCreated());
        return login(email);
    }

    String registerHrWithCompany(String prefix) throws Exception {
        String token = register("hr", uniqueEmail(prefix), uniqueName("Nha Tuyen Dung"));
        createCompany(token, uniqueName("Cong ty " + prefix));
        return token;
    }

    void createCompany(String token, String companyName) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(companyName)))
                .andExpect(status().isCreated());
    }

    String createOpenJob(String token, String title, String criterionName) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "job": {"title":"%s","description":"Mo ta cong viec","categoryCode":"IT_SOFTWARE","locationCode":"HA_NOI"},
                                  "interviewTemplate": {
                                    "subject":"Thu moi phong van",
                                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                                    "senderName":"Phong Nhan Su"
                                  }
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        String jobId = extractJsonField(result.getResponse().getContentAsString(), "id");
        mockMvc
                .perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","weight":100}
                                """.formatted(criterionName)))
                .andExpect(status().isCreated());
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
        return jobId;
    }

    Fixture createApplication(String prefix) throws Exception {
        return createApplication(prefix, "Tieu chi", null);
    }

    // Don PENDING moi: HR co cong ty + Job OPEN (1 tieu chi rubric), ung vien nop don voi CV gia lap.
    Fixture createApplication(String prefix, String criterionName, String coverLetter) throws Exception {
        String hrEmail = uniqueEmail(prefix + "-hr");
        String hrFullName = uniqueName("Nha Tuyen Dung");
        String hrToken = register("hr", hrEmail, hrFullName);
        String companyName = uniqueName("Cong ty " + prefix);
        createCompany(hrToken, companyName);
        String jobTitle = uniqueName("Job " + prefix);
        String jobId = createOpenJob(hrToken, jobTitle, criterionName);

        String candidateEmail = uniqueEmail(prefix + "-uv");
        String candidateName = uniqueName("Ung Vien");
        String candidateToken = register("candidate", candidateEmail, candidateName);
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult resume = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        String resumeId = extractJsonField(resume.getResponse().getContentAsString(), "id");
        Map<String, Object> applyBody = new LinkedHashMap<>();
        applyBody.put("jobId", jobId);
        applyBody.put("resumeId", resumeId);
        applyBody.put("aiConsent", true);
        if (coverLetter != null) {
            applyBody.put("coverLetter", coverLetter);
        }
        MvcResult application = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.writeValueAsString(applyBody)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID applicationId = UUID.fromString(extractJsonField(application.getResponse().getContentAsString(), "id"));

        return new Fixture(
                applicationId,
                hrToken,
                candidateToken,
                userRepository.findByEmail(hrEmail).orElseThrow().getId(),
                userRepository.findByEmail(candidateEmail).orElseThrow().getId(),
                hrEmail,
                hrFullName,
                candidateEmail,
                candidateName,
                jobTitle,
                companyName);
    }

    // Dung trang thai test bang native UPDATE (du lieu dung san, khong di qua may trang thai cua FR-H07).
    void setStatus(Fixture f, ApplicationStatus status) {
        jdbcTemplate.update("UPDATE job_applications SET status = ? WHERE id = ?", status.name(), f.applicationId());
    }

    // Moi saveAndFlush tu commit rieng; created_at backdate tuong minh = BASE_TIME + order giay de thu tu xac dinh.
    void insertMessage(Fixture f, MessageSenderRole role, String body, boolean withAttachment, int order) {
        ApplicationMessage message = new ApplicationMessage();
        message.setApplicationId(f.applicationId());
        message.setSenderId(role == MessageSenderRole.HR ? f.hrUserId() : f.candidateUserId());
        message.setSenderRole(role);
        message.setBody(body);
        if (withAttachment) {
            message.setAttachmentKey("message-attachments/" + UUID.randomUUID() + ".pdf");
            message.setAttachmentName("bang-cap-bi-mat.pdf");
            message.setAttachmentType(AttachmentType.PDF);
            message.setAttachmentSize(1234L);
        }
        UUID id = messageRepository.saveAndFlush(message).getId();
        jdbcTemplate.update(
                "UPDATE application_messages SET created_at = ? WHERE id = ?",
                Timestamp.from(BASE_TIME.plusSeconds(order)),
                id);
    }

    void insertInvitation(Fixture f, Instant scheduledAt, String location) {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setApplicationId(f.applicationId());
        invitation.setScheduledAt(scheduledAt);
        invitation.setLocation(location);
        invitation.setSubject("Thu moi phong van");
        invitation.setRenderedContent("Noi dung giay moi KHONG duoc vao ngu canh");
        invitation.setSentAt(BASE_TIME);
        invitation.setSentBy(f.hrUserId());
        invitationRepository.saveAndFlush(invitation);
    }

    void deleteInvitations(Fixture f) {
        jdbcTemplate.update("DELETE FROM interview_invitations WHERE application_id = ?", f.applicationId());
    }
}
