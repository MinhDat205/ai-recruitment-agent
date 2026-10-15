package com.recruitment.cvqa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anthropic.models.messages.StopReason;
import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

// FR-C08 dot 3b - bo helper dung chung cho cac lop test tich hop cua cvqa/ (khuon MessageDraftIntegrationTestSupport).
// Cung cau hinh context (Testcontainers + LlmTestConfiguration + MockMvc + profile test) -> dung chung context cache.
//
// KHONG @Transactional cap lop: luot hoi thanh cong goi K3, ma K3 nem IllegalStateException neu luong request dang co
// transaction (R-A1) - MockMvc chay tren cung luong test. Du lieu moi test tu tao (email, ten duy nhat); hang
// resume_parsed_data chen bang JdbcTemplate vi job trich xuat tat trong profile test.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class CvQaIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";

    // CV mau: hai cau da biet cho trich dan; dong "Kỹ năng:" co 3 dau cach de trich dan cua AI khac khoang trang.
    static final String QUOTE_EXPERIENCE = "3 năm phát triển ứng dụng Java tại Công ty ABC";
    static final String QUOTE_SKILLS = "Kỹ năng: Docker và Kubernetes cho hệ thống nội bộ";
    static final String RAW_TEXT = "Nguyễn Văn A\nKinh nghiệm: " + QUOTE_EXPERIENCE + ".\n"
            + "Kỹ năng:   Docker và Kubernetes cho hệ thống nội bộ.\nHọc vấn: Đại học Bách khoa Hà Nội\n";
    static final String DEFAULT_ANSWER = "Ứng viên có 3 năm phát triển ứng dụng Java tại Công ty ABC.";
    static final String DEFAULT_QUESTION = "Ứng viên đã dùng Java ở đâu?";

    // Muc 4.4 / K3 (a10) - cau co dinh.
    static final String EMPTY_QUESTION_MESSAGE = "Vui lòng nhập câu hỏi.";
    static final String TOO_LONG_MESSAGE = "Câu hỏi tối đa 500 ký tự.";
    static final String UNREADABLE_MESSAGE = "Yêu cầu hỏi đáp CV không hợp lệ.";
    static final String CV_TEXT_MESSAGE = "CV chưa được trích xuất xong nên chưa hỏi đáp được.";
    static final String INVALID_OUTPUT_MESSAGE = "AI trả về kết quả không hợp lệ, vui lòng thử lại.";

    private static final String PARSED_JSON = """
            {"contact": {"fullName": "Nguyen Van A"}, "education": [], "experience": [], "skills": ["Java"],
             "certifications": [], "projects": []}
            """;

    record Fixture(
            UUID applicationId,
            UUID jobId,
            UUID resumeId,
            String hrToken,
            String candidateToken,
            UUID hrUserId,
            UUID candidateUserId) {
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatModel chatModel;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

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

    static String answeredJson(String answer, String... quotes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("answer", answer);
        body.put("citations", Arrays.stream(quotes).map(quote -> Map.of("quote", quote)).toList());
        body.put("notFound", false);
        body.put("refused", null);
        return JSON.writeValueAsString(body);
    }

    static String notFoundJson() {
        return "{\"answer\": null, \"citations\": [], \"notFound\": true, \"refused\": null}";
    }

    static String refusedJson(String reason) {
        return "{\"answer\": null, \"citations\": [], \"notFound\": false, \"refused\": \"" + reason + "\"}";
    }

    // Moi phan tu = mot lan goi ChatModel theo thu tu; lan goi vuot so phan tu lap lai phan tu cuoi (Mockito).
    void stubOutputs(String first, String... rest) {
        ChatResponse[] next = Arrays.stream(rest).map(CvQaIntegrationTestSupport::chatResponse).toArray(ChatResponse[]::new);
        doReturn(chatResponse(first), (Object[]) next).when(chatModel).call(any(Prompt.class));
    }

    void stubDefaultAnswer() {
        stubOutputs(answeredJson(DEFAULT_ANSWER, QUOTE_EXPERIENCE));
    }

    List<Prompt> capturedPrompts(int expectedCalls) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(expectedCalls)).call(captor.capture());
        return captor.getAllValues();
    }

    void assertNoModelCall() {
        Mockito.verify(chatModel, never()).call(any(Prompt.class));
    }

    static String systemText(Prompt prompt) {
        return prompt.getSystemMessage().getText();
    }

    static String userText(Prompt prompt) {
        return prompt.getUserMessage().getText();
    }

    // ---- HTTP ----

    static String hrPath(Object applicationId) {
        return "/api/hr/applications/" + applicationId + "/cv-qa";
    }

    MvcResult postRaw(Object applicationId, String token, String rawJson) throws Exception {
        var request = post(hrPath(applicationId)).contentType(MediaType.APPLICATION_JSON).content(rawJson);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult ask(Object applicationId, String token, String question) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", question);
        return postRaw(applicationId, token, JSON.writeValueAsString(body));
    }

    MvcResult ask(Fixture f, String question) throws Exception {
        return ask(f.applicationId(), f.hrToken(), question);
    }

    static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static String rawBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static void assertStatus(MvcResult result, int httpStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(httpStatus);
    }

    static void assertError(MvcResult result, int httpStatus, String code) throws Exception {
        assertStatus(result, httpStatus);
        assertThat(body(result).get("error").asString()).isEqualTo(code);
    }

    static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertError(result, httpStatus, code);
        assertThat(body(result).get("message").asString()).isEqualTo(message);
    }

    static List<String> quotesOf(JsonNode root) {
        return root.get("citations").valueStream().map(citation -> citation.get("quote").asString()).toList();
    }

    // 200 + outcome; tra body de khang dinh tiep.
    static JsonNode assertOutcome(MvcResult result, String outcome) throws Exception {
        assertStatus(result, 200);
        JsonNode root = body(result);
        assertThat(root.get("outcome").asString()).isEqualTo(outcome);
        return root;
    }

    static void assertRefused(MvcResult result, String reason) throws Exception {
        JsonNode root = assertOutcome(result, "REFUSED");
        assertThat(root.get("refusalReason").asString()).isEqualTo(reason);
        assertThat(root.get("answer").isNull()).isTrue();
        assertThat(root.get("citations").isEmpty()).isTrue();
        assertThat(root.get("cvTruncated").asBoolean()).isFalse();
    }

    // ---- du lieu ----

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

    String register(String role, String email) throws Exception {
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"%s","phone":"0900000000"}
                                """.formatted(email, uniqueName("Nguoi Dung"))))
                .andExpect(status().isCreated());
        return login(email);
    }

    void createCompany(String token) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueName("Cong ty"))))
                .andExpect(status().isCreated());
    }

    String registerHrWithCompany(String prefix) throws Exception {
        String token = register("hr", uniqueEmail(prefix + "-hr"));
        createCompany(token);
        return token;
    }

    String createOpenJob(String token, String criterionName) throws Exception {
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
                                """.formatted(uniqueName("Job"))))
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

    UUID uploadResume(String candidateToken) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult resume = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(extractJsonField(resume.getResponse().getContentAsString(), "id"));
    }

    // Don PENDING moi: HR co cong ty + Job OPEN (1 tieu chi rubric), ung vien nop don voi CV gia lap (PENDING, chua co
    // resume_parsed_data).
    Fixture createApplication(String prefix) throws Exception {
        return createApplication(prefix, "Tieu chi");
    }

    Fixture createApplication(String prefix, String criterionName) throws Exception {
        String hrEmail = uniqueEmail(prefix + "-hr");
        String hrToken = register("hr", hrEmail);
        createCompany(hrToken);
        String jobId = createOpenJob(hrToken, criterionName);

        String candidateEmail = uniqueEmail(prefix + "-uv");
        String candidateToken = register("candidate", candidateEmail);
        UUID resumeId = uploadResume(candidateToken);
        MvcResult application = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                                """.formatted(jobId, resumeId)))
                .andExpect(status().isCreated())
                .andReturn();
        return new Fixture(
                UUID.fromString(extractJsonField(application.getResponse().getContentAsString(), "id")),
                UUID.fromString(jobId),
                resumeId,
                hrToken,
                candidateToken,
                userRepository.findByEmail(hrEmail).orElseThrow().getId(),
                userRepository.findByEmail(candidateEmail).orElseThrow().getId());
    }

    // Don co CV da trich xuat xong (hang resume_parsed_data voi rawText cho truoc).
    Fixture createApplicationWithText(String prefix, String rawText) throws Exception {
        Fixture f = createApplication(prefix);
        insertParsedData(f.resumeId(), rawText);
        return f;
    }

    void insertParsedData(UUID resumeId, String rawText) {
        jdbcTemplate.update(
                "INSERT INTO resume_parsed_data (id, resume_id, raw_text, data, model, prompt_version) "
                        + "VALUES (?, ?, ?, CAST(? AS jsonb), 'claude-sonnet-4-6', 'resume-parse-v1')",
                UUID.randomUUID(),
                resumeId,
                rawText,
                PARSED_JSON);
    }

    void updateRawText(UUID resumeId, String rawText) {
        jdbcTemplate.update("UPDATE resume_parsed_data SET raw_text = ? WHERE resume_id = ?", rawText, resumeId);
    }

    void setJobPosting(UUID jobId, String title, String description, String requirements) {
        jdbcTemplate.update(
                "UPDATE jobs SET title = ?, description = ?, requirements = ? WHERE id = ?",
                title,
                description,
                requirements,
                jobId);
    }
}
