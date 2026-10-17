package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.cvbuilder.dto.CvBuilderRequest;
import com.recruitment.resume.LlmTestConfiguration;
import com.recruitment.user.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

// FR-U12 dot 4 - helper dung chung cho cac lop test tich hop cua cvbuilder/ (khuon CvQaIntegrationTestSupport). Cung cau
// hinh context (Testcontainers + LlmTestConfiguration + MockMvc + profile test). KHONG @Transactional cap lop (CLAUDE.md
// muc 3c): B1 ghi file + transaction rieng cua CvBuilderStateService, test phai quan sat duoc trang thai da commit/
// rollback. Du lieu moi test tu tao (email UUID ngau nhien).
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class CvBuilderIntegrationTestSupport {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static final String RESUMES_PATH = "/api/candidates/cv-builder/resumes";
    static final String PREVIEW_PATH = "/api/candidates/cv-builder/preview";

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

    @Value("${app.storage.local-path}")
    String storageLocalPath;

    // Mock singleton dung chung moi lop - reset (giu default answer "nem loi khi chua stub") de dem duoc so lan goi cua
    // rieng test nay (R-W8).
    @BeforeEach
    void resetAiMocks() {
        Mockito.reset(chatModel, embeddingModel);
    }

    void assertNoAiCall() {
        assertThat(Mockito.mockingDetails(chatModel).getInvocations()).as("ChatModel").isEmpty();
        assertThat(Mockito.mockingDetails(embeddingModel).getInvocations()).as("EmbeddingModel").isEmpty();
    }

    // ---- du lieu form ----

    // Form hop le, DA o dang chuan hoa (R-F3 khong doi gi) -> form_data luu xuong bang dung form nay. Kinh nghiem 1:
    // "03/2019" - dang lam (tinh duoc); kinh nghiem 2: moc chi co nam (bo qua - T5).
    static CvBuilderForm sampleForm() {
        return new CvBuilderForm(
                new CvBuilderForm.Contact(
                        "Nguyễn Văn An", "an.nguyen@example.com", "0901 234 567", "Số 1 Tràng Tiền, Hoàn Kiếm", null),
                "Lập trình viên Java",
                "Hà Nội",
                "IT_SOFTWARE",
                List.of(new CvBuilderForm.Education(
                        "Đại học Bách khoa Hà Nội", "Kỹ sư", "Công nghệ thông tin", "09/2012", "06/2017", 3.25)),
                List.of(
                        new CvBuilderForm.Experience(
                                "Công ty Ánh Dương", "Lập trình viên", "03/2019", null, true,
                                "Phát triển API thanh toán <Java> & Spring.\nGiảm 30% thời gian phản hồi."),
                        new CvBuilderForm.Experience(
                                "Công ty Hưng Thịnh", null, "2018", null, null, "Bảo trì hệ thống nội bộ.")),
                List.of("Java", "Spring Boot", "PostgreSQL"),
                List.of(new CvBuilderForm.Certification("IELTS 7.5", "IDP Việt Nam", "06/2021")),
                List.of(new CvBuilderForm.Project(
                        "Ứng dụng đặt lịch", "Xây dựng API đặt lịch cho 20 phòng khám.", List.of("Java", "React"))));
    }

    static CvBuilderRequest sampleRequest(CvTemplate template) {
        return new CvBuilderRequest(template, "Bản tạo bằng công cụ", sampleForm());
    }

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    // ---- HTTP ----

    MvcResult postJson(String path, String token, String rawJson) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(rawJson);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    MvcResult createCv(String token, CvBuilderRequest request) throws Exception {
        return postJson(RESUMES_PATH, token, json(request));
    }

    MvcResult preview(String token, CvBuilderRequest request) throws Exception {
        return postJson(PREVIEW_PATH, token, json(request));
    }

    MvcResult getDocument(String token, Object resumeId) throws Exception {
        var request = get(RESUMES_PATH + "/" + resumeId);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    // B1 thanh cong -> id CV moi.
    UUID createCvOk(String token, CvBuilderRequest request) throws Exception {
        MvcResult result = createCv(token, request);
        assertStatus(result, 201);
        return UUID.fromString(body(result).get("id").asString());
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

    static void assertError(MvcResult result, int httpStatus, String code, String message) throws Exception {
        assertStatus(result, httpStatus);
        JsonNode root = body(result);
        assertThat(root.get("error").asString()).isEqualTo(code);
        assertThat(root.get("message").asString()).isEqualTo(message);
    }

    // ---- tai khoan, tin, don ----

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

    // Ung vien moi: token + id.
    record Candidate(String token, UUID id) {
    }

    Candidate registerCandidate(String prefix) throws Exception {
        String email = uniqueEmail(prefix + "-uv");
        String token = register("candidate", email);
        return new Candidate(token, userRepository.findByEmail(email).orElseThrow().getId());
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

    // HR co cong ty + Job OPEN (1 tieu chi rubric) - khuon CvQaIntegrationTestSupport.createOpenJob.
    String createOpenJob(String hrToken) throws Exception {
        mockMvc
                .perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(uniqueName("Cong ty"))))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc
                .perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + hrToken)
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
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tieu chi","weight":100}
                                """))
                .andExpect(status().isCreated());
        mockMvc
                .perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OPEN"}
                                """))
                .andExpect(status().isOk());
        return jobId;
    }

    UUID apply(String candidateToken, String jobId, UUID resumeId) throws Exception {
        MvcResult application = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jobId":"%s","resumeId":"%s","aiConsent":true}
                                """.formatted(jobId, resumeId)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(extractJsonField(application.getResponse().getContentAsString(), "id"));
    }

    // ---- DB, kho file ----

    long countRows(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    long countResumesOf(UUID candidateId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resumes WHERE candidate_id = ?", Long.class, candidateId);
    }

    Path storageBase() {
        return Path.of(storageLocalPath).toAbsolutePath().normalize();
    }

    // Khoa "resumes/<uuid>.pdf" (resumes.file_url) -> file that trong kho test.
    Path storedFile(String key) {
        return storageBase().resolve(key);
    }

    // So file (de quy) trong thu muc con cua kho test; thu muc chua ton tai -> 0.
    long countFiles(String subdirectory) throws IOException {
        Path dir = storageBase().resolve(subdirectory);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
