package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anthropic.models.messages.StopReason;
import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.jobdraft.JobAiDraftService;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H11 - helper dung chung cho cac lop test tich hop cua jobdraft/. Cung cau hinh context voi
// MessageDraftIntegrationTestSupport (Testcontainers + LlmTestConfiguration + MockMvc + profile test) -> dung chung
// context cache. KHONG @Transactional cap lop: J1 goi K3 (K3/facade nem IllegalStateException neu luong request dang co
// transaction). Du lieu moi test tu tao, email duy nhat.
//
// Mock ChatModel (dot 4): ba phan chay SONG SONG tren cung mock nen phan biet phan theo system message (bat dau bang dong
// dau cua file prompt cua phan do - muc 7.1), dem so lan goi rieng tung phan.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class JobDraftIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String GENERATE_PATH = "/api/hr/job-ai-drafts";
    static final String TEST_MODEL = "claude-test";
    // Bang "khong ghi gi" cua muc 7.1.
    static final List<String> WRITE_TABLES = List.of(
            "jobs", "rubrics", "rubric_criteria", "interview_templates", "job_screening_questions", "job_ai_generations");

    private static final Map<JobDraftPart, String> PROMPT_FIRST_LINES = new EnumMap<>(Map.of(
            JobDraftPart.JOB, firstLine(JobAiDraftService.JOB_PROMPT_VERSION),
            JobDraftPart.CRITERIA, firstLine(JobAiDraftService.CRITERIA_PROMPT_VERSION),
            JobDraftPart.QUESTIONS, firstLine(JobAiDraftService.QUESTIONS_PROMPT_VERSION)));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ChatModel chatModel;

    private final Map<JobDraftPart, AtomicInteger> callCounts = new ConcurrentHashMap<>();

    // Mock ChatModel la singleton dung chung moi lop - reset truoc moi test (giu default answer "nem loi khi chua stub").
    @BeforeEach
    void resetChatModelMock() {
        Mockito.reset(chatModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();
        callCounts.clear();
    }

    // ---- mock ChatModel theo phan ----

    // Tra loi cua mock cho mot phan; call = lan goi thu may CUA PHAN DO (1, 2).
    @FunctionalInterface
    interface PartAnswer {
        ChatResponse answer(int call) throws Exception;
    }

    static PartAnswer always(String json) {
        return call -> chatResponse(json);
    }

    void stubParts(Map<JobDraftPart, PartAnswer> answers) {
        doAnswer(invocation -> {
                    JobDraftPart part = partOf(invocation.getArgument(0));
                    int call = callCounts.computeIfAbsent(part, p -> new AtomicInteger()).incrementAndGet();
                    PartAnswer answer = answers.get(part);
                    if (answer == null) {
                        throw new IllegalStateException("Phan " + part + " chua duoc stub trong test");
                    }
                    return answer.answer(call);
                })
                .when(chatModel)
                .call(any(Prompt.class));
    }

    // Ba phan deu tra JSON hop le mac dinh.
    void stubAllValid() {
        stubParts(Map.of(
                JobDraftPart.JOB, always(jobJson(fullJob())),
                JobDraftPart.CRITERIA, always(criteriaJson(List.of(criterion("Kinh nghiệm Java", "Số năm làm Java")))),
                JobDraftPart.QUESTIONS, always(questionsJson(List.of(
                        question("YES_NO", "Bạn có thể đi làm tại Đà Nẵng không?", List.of()))))));
    }

    int callsOf(JobDraftPart part) {
        AtomicInteger count = callCounts.get(part);
        return count == null ? 0 : count.get();
    }

    List<Prompt> capturedPrompts() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.atLeast(0)).call(captor.capture());
        return captor.getAllValues();
    }

    Prompt promptOf(JobDraftPart part) {
        List<Prompt> prompts = capturedPrompts().stream().filter(p -> partOf(p) == part).toList();
        assertThat(prompts).as("prompt cua phan %s", part).isNotEmpty();
        return prompts.get(0);
    }

    static JobDraftPart partOf(Prompt prompt) {
        String system = prompt.getSystemMessage().getText();
        for (Map.Entry<JobDraftPart, String> entry : PROMPT_FIRST_LINES.entrySet()) {
            if (system.startsWith(entry.getValue())) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("System message khong thuoc phan nao cua FR-H11");
    }

    private static String firstLine(String promptVersion) {
        try {
            String content = new ClassPathResource("ai/prompt/" + promptVersion + ".st")
                    .getContentAsString(StandardCharsets.UTF_8);
            return content.lines().findFirst().orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ChatResponse chatResponse(String text) {
        return ChatResponse.builder()
                .generations(List.of(new Generation(
                        new AssistantMessage(text),
                        ChatGenerationMetadata.builder()
                                .finishReason(StopReason.END_TURN.toString())
                                .build())))
                .metadata(ChatResponseMetadata.builder().model(TEST_MODEL).build())
                .build();
    }

    // ---- JSON cua tung phan ----

    // Ban nhap day du cua T1 (moi gia tri co the ghi de truoc khi doi sang JSON).
    static Map<String, Object> fullJob() {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("title", "Lập trình viên Java Backend");
        job.put("description", "Phát triển API với Spring Boot.");
        job.put("requirements", "2 năm kinh nghiệm Java.");
        job.put("benefits", "Lương tháng 13.");
        job.put("industryCode", "IT_SOFTWARE");
        job.put("locationText", "Đà Nẵng");
        job.put("employmentType", "FULL_TIME");
        job.put("workMode", "ONSITE");
        job.put("salaryMin", 15000000);
        job.put("salaryMax", 20000000);
        job.put("salaryCurrency", "VND");
        job.put("deadline", "2099-12-31");
        return job;
    }

    static String jobJson(Map<String, Object> job) {
        return JSON.writeValueAsString(job);
    }

    static Map<String, Object> criterion(String name, String description) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("name", name);
        criterion.put("description", description);
        return criterion;
    }

    static String criteriaJson(List<Map<String, Object>> criteria) {
        return JSON.writeValueAsString(Map.of("criteria", criteria));
    }

    static Map<String, Object> question(String type, String text, List<String> options) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionType", type);
        question.put("questionText", text);
        question.put("options", options);
        return question;
    }

    static String questionsJson(List<Map<String, Object>> questions) {
        return JSON.writeValueAsString(Map.of("questions", questions));
    }

    // ---- HTTP J1 ----

    static String generateJson(String description, List<String> parts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", description);
        body.put("parts", parts);
        return JSON.writeValueAsString(body);
    }

    MvcResult postGenerate(String token, String rawJson) throws Exception {
        var request = post(GENERATE_PATH).contentType(MediaType.APPLICATION_JSON).content(rawJson);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult postGenerate(String token, String description, JobDraftPart... parts) throws Exception {
        List<String> names = new ArrayList<>();
        for (JobDraftPart part : parts) {
            names.add(part.name());
        }
        return postGenerate(token, generateJson(description, names));
    }

    static String rawBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(rawBody(result));
    }

    static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(httpStatus);
        assertThat(body(result).get("error").asString()).isEqualTo(code);
        if (message != null) {
            assertThat(body(result).get("message").asString()).isEqualTo(message);
        }
    }

    Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : WRITE_TABLES) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class));
        }
        return counts;
    }

    // HR co cong ty, tra access token.
    String hrWithCompany(String emailPrefix) throws Exception {
        String token = register("hr", uniqueEmail(emailPrefix));
        createCompany(token);
        return token;
    }

    // ---- tai khoan, cong ty, tin ----

    // prefix <= 27 ky tu.
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

    UUID userIdOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    void createCompany(String token) throws Exception {
        createCompany(token, uniqueName("Cong ty"));
    }

    void createCompany(String token, String name) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated());
    }

    // Tao tin DRAFT bang duong thu cong cua FR-H02 (khong qua FR-H11).
    UUID createDraftJob(String token) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "job": {"title":"Tin kiem thu K4","description":"Mo ta cong viec"},
                                  "interviewTemplate": {
                                    "subject":"Thu moi phong van",
                                    "body":"Kinh moi ban tham gia phong van.",
                                    "senderName":"Phong Nhan Su"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(extractJsonField(result.getResponse().getContentAsString(), "id"));
    }
}
