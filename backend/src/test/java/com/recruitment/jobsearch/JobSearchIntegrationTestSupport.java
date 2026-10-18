package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anthropic.models.messages.StopReason;
import com.recruitment.TestcontainersConfiguration;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobEmbeddingStateService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U13 dot 4 - helper dung chung cho cac lop test tich hop cua jobsearch/ (khuon CvBuilderIntegrationTestSupport). KHONG
// @Transactional cap lop: K3 tu choi khi luong goi dang co transaction (MockMvc chay tren luong test), va moi saveAndFlush
// tu commit nen published_at backdate tuong minh quan sat duoc (CLAUDE.md 3c).
//
// L32 - cach ly voi job OPEN ro ri tu lop khac: production truyen titlePattern = null (R-Q3) nen khuon L18 (nhan rieng qua
// titlePattern) khong dung duoc qua HTTP. Dung tien le U15 (JobRecommendationCandidateServiceTest/
// JobRecommendationCandidateControllerIntegrationTest): soft-delete MOI job OPEN con lai truoc moi test, roi tao job rieng
// cua test - khang dinh duoc DUNG tap/DUNG thu tu.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class JobSearchIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static final String PATH = "/api/candidates/job-search";
    static final String MODEL = "text-embedding-3-small";
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    // Muc 4.4 / K3 - cau co dinh.
    static final String UNREADABLE_MESSAGE = "Yêu cầu tìm việc không hợp lệ.";
    static final String EMPTY_MESSAGE = "Vui lòng nhập mô tả công việc bạn muốn tìm.";
    static final String TOO_SHORT_MESSAGE = "Mô tả cần ít nhất 2 ký tự.";
    static final String TOO_LONG_MESSAGE = "Mô tả tìm việc tối đa 300 ký tự.";
    static final String CHARACTER_MESSAGE = "Mô tả có ký tự không hỗ trợ.";
    static final String INVALID_OUTPUT_MESSAGE = "AI trả về kết quả không hợp lệ, vui lòng thử lại.";
    static final String UNAVAILABLE_MESSAGE = "Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau.";
    static final String TIMEOUT_MESSAGE = "AI phản hồi quá lâu, vui lòng thử lại.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ChatModel chatModel;

    @Autowired
    EmbeddingModel embeddingModel;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JobEmbeddingStateService jobEmbeddingStateService;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    JobApplicationRepository jobApplicationRepository;

    @Autowired
    CatalogRegistry catalogRegistry;

    @Autowired
    JobSearchCatalogPrompt catalogPrompt;

    UUID hrId;
    UUID companyId;

    // Mock singleton dung chung moi lop - reset (giu default answer "nem loi khi chua stub") de dem duoc so lan goi cua
    // rieng test nay. ChatClient doc getOptions/getDefaultOptions khi dung Prompt.
    @BeforeEach
    void resetAiMocksAndJobs() {
        Mockito.reset(chatModel, embeddingModel);
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getOptions();
        doReturn(AnthropicChatOptions.builder().build()).when(chatModel).getDefaultOptions();

        jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE status = 'OPEN' AND deleted_at IS NULL");

        User hr = new User();
        hr.setEmail("u13-hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hrId = userRepository.saveAndFlush(hr).getId();
        Company company = new Company();
        company.setOwnerId(hrId);
        company.setName("Cong ty U13 " + UUID.randomUUID());
        companyId = companyRepository.saveAndFlush(company).getId();
    }

    // ---- AI mock ----

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

    // Moi phan tu = mot lan goi ChatModel theo thu tu; lan goi vuot so phan tu lap lai phan tu cuoi (Mockito).
    void stubOutputs(String first, String... rest) {
        ChatResponse[] next =
                Arrays.stream(rest).map(JobSearchIntegrationTestSupport::chatResponse).toArray(ChatResponse[]::new);
        doReturn(chatResponse(first), (Object[]) next).when(chatModel).call(any(Prompt.class));
    }

    List<Prompt> capturedPrompts(int expectedCalls) {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(expectedCalls)).call(captor.capture());
        return captor.getAllValues();
    }

    // Vector truy van: truc 2. Job dung vectorWithSimilarity(s) -> cosine voi vector truy van dung bang s.
    static float[] queryVector() {
        float[] v = new float[1536];
        v[2] = 1f;
        return v;
    }

    static float[] vectorWithSimilarity(double s) {
        float[] v = new float[1536];
        v[2] = (float) s;
        v[3] = (float) Math.sqrt(1 - s * s);
        return v;
    }

    static EmbeddingResponse embeddingResponse(float[] vector) {
        return new EmbeddingResponse(
                List.of(new Embedding(vector, 0)), new EmbeddingResponseMetadata(MODEL, new DefaultUsage(10, 0)));
    }

    void stubEmbedding() {
        doReturn(embeddingResponse(queryVector())).when(embeddingModel).embedForResponse(anyList());
    }

    // Moi chuoi da gui EmbeddingModel, theo thu tu goi.
    List<List<String>> embeddingInputs() {
        List<List<String>> inputs = new ArrayList<>();
        Mockito.mockingDetails(embeddingModel).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("embedForResponse"))
                .forEach(invocation -> inputs.add(invocation.getArgument(0)));
        return inputs;
    }

    long embeddingCalls() {
        return Mockito.mockingDetails(embeddingModel).getInvocations().stream()
                .filter(invocation -> !invocation.getMethod().getName().equals("toString"))
                .count();
    }

    long chatCalls() {
        return Mockito.mockingDetails(chatModel).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("call"))
                .count();
    }

    void assertNoAiCall() {
        assertThat(chatCalls()).as("ChatModel.call").isZero();
        assertThat(embeddingCalls()).as("EmbeddingModel").isZero();
    }

    // ---- output AI (JobSearchParsePayload) ----

    static Payload payload() {
        return new Payload();
    }

    static final class Payload {
        private final List<Map<String, Object>> locations = new ArrayList<>();
        private final List<Map<String, Object>> industries = new ArrayList<>();
        private final List<Map<String, Object>> workModes = new ArrayList<>();
        private final List<String> personalInfoTexts = new ArrayList<>();
        private Map<String, Object> salary;

        Payload location(String code, String sourceText) {
            locations.add(condition(code, sourceText));
            return this;
        }

        Payload industry(String code, String sourceText) {
            industries.add(condition(code, sourceText));
            return this;
        }

        Payload workMode(String code, String sourceText) {
            workModes.add(condition(code, sourceText));
            return this;
        }

        Payload salary(Integer min, Integer max, String sourceText) {
            salary = new LinkedHashMap<>();
            salary.put("minMillionVnd", min);
            salary.put("maxMillionVnd", max);
            salary.put("sourceText", sourceText);
            return this;
        }

        Payload personalInfo(String text) {
            personalInfoTexts.add(text);
            return this;
        }

        String json() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("locations", locations);
            body.put("industries", industries);
            body.put("salary", salary);
            body.put("workModes", workModes);
            body.put("personalInfoTexts", personalInfoTexts);
            return JSON.writeValueAsString(body);
        }

        private static Map<String, Object> condition(String code, String sourceText) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("code", code);
            c.put("sourceText", sourceText);
            return c;
        }
    }

    // ---- HTTP ----

    MvcResult search(String token, String query) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        return postJson(token, JSON.writeValueAsString(body));
    }

    MvcResult postJson(String token, String rawJson) throws Exception {
        var request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(rawJson);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(rawBody(result));
    }

    static String rawBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    static void assertStatus(MvcResult result, int httpStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(httpStatus);
    }

    // Body loi chi gom {error, message}.
    static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertStatus(result, httpStatus);
        JsonNode root = body(result);
        assertThat(root.propertyNames()).containsExactlyInAnyOrder("error", "message");
        assertThat(root.get("error").asString()).isEqualTo(code);
        assertThat(root.get("message").asString()).isEqualTo(message);
    }

    static List<UUID> itemIds(JsonNode root) {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode item : root.get("items")) {
            ids.add(UUID.fromString(item.get("job").get("id").asString()));
        }
        return ids;
    }

    static List<Boolean> rankedFlags(JsonNode root) {
        List<Boolean> flags = new ArrayList<>();
        for (JsonNode item : root.get("items")) {
            flags.add(item.get("rankedByDescription").asBoolean());
        }
        return flags;
    }

    static List<String> matched(JsonNode item) {
        List<String> conditions = new ArrayList<>();
        for (JsonNode condition : item.get("matchedConditions")) {
            conditions.add(condition.asString());
        }
        return conditions;
    }

    static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            values.add(value.asString());
        }
        return values;
    }

    // ---- tai khoan ----

    static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
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
        Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]*)\"").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    String register(String role, String email) throws Exception {
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Nguoi Dung U13","phone":"0900000000"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    record Candidate(String token, UUID id) {
    }

    Candidate registerCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix + "-uv");
        String token = register("candidate", email);
        return new Candidate(token, userRepository.findByEmail(email).orElseThrow().getId());
    }

    // ---- viec lam ----

    Job newJob(String title, Instant publishedAt) {
        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(hrId);
        job.setTitle("U13 " + title + " " + UUID.randomUUID());
        job.setDescription("Mo ta cong viec test");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        job.setPublishedAt(publishedAt);
        return job;
    }

    UUID save(Job job) {
        return jobRepository.saveAndFlush(job).getId();
    }

    UUID embedded(UUID jobId, double similarity) {
        jobEmbeddingStateService.save(jobId, vectorWithSimilarity(similarity), MODEL);
        return jobId;
    }

    static void salary(Job job, Long min, Long max, String currency) {
        job.setSalaryMin(min == null ? null : BigDecimal.valueOf(min));
        job.setSalaryMax(max == null ? null : BigDecimal.valueOf(max));
        job.setSalaryCurrency(currency);
    }

    // Don ung tuyen cung chu ky (ghi thang qua repository - khong can job nen/AI).
    void apply(UUID candidateId, UUID jobId) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        UUID resumeId = resumeRepository.saveAndFlush(resume).getId();
        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidateId);
        application.setResumeId(resumeId);
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        jobApplicationRepository.saveAndFlush(application);
    }

    long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
