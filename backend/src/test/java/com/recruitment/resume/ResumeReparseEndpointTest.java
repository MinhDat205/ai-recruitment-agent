package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-C05 R-R1..R-R3 + API danh sach/parsed (REQUIREMENT muc 4, muc 7.7, 7.8). KHONG @Transactional o
// class: MockMvc qua DispatcherServlet that, moi request tu commit rieng (mau
// CvImprovementSuggestionEndpointTest). Moi assertion scope theo resumeId cua chinh test.
@Import({TestcontainersConfiguration.class, LlmTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeReparseEndpointTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeParsedDataRepository resumeParsedDataRepository;

    @Autowired
    private ResumeReparseRequestRepository reparseRequestRepository;

    @Autowired
    private com.recruitment.user.UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void resetAiMocks() {
        Mockito.reset(chatModel, embeddingModel);
    }

    private record Auth(String token, UUID userId) {
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private Auth registerAndLogin(String role, String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nguoi Dung Test","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc.perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        return new Auth(token, userId);
    }

    private UUID createResume(UUID candidateId, ParseStatus status) {
        Resume resume = new Resume();
        resume.setCandidateId(candidateId);
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(status);
        return resumeRepository.save(resume).getId();
    }

    private UUID createParsedResume(UUID candidateId, String promptVersion) {
        UUID resumeId = createResume(candidateId, ParseStatus.DONE);
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("CV goc gia lap");
        data.setData(new ResumeParsedPayload(
                null, List.of(), List.of(), List.of("Java"), List.of(), List.of(), null, null, null));
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion(promptVersion);
        resumeParsedDataRepository.save(data);
        return resumeId;
    }

    private ResumeReparseRequest createRequest(UUID resumeId, ResumeReparseRequestStatus status) {
        ResumeReparseRequest request = new ResumeReparseRequest();
        request.setResumeId(resumeId);
        request.setStatus(status);
        return reparseRequestRepository.saveAndFlush(request);
    }

    private long countRequests(UUID resumeId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM resume_reparse_requests WHERE resume_id = ?", Long.class, resumeId);
    }

    private MvcResult postReparse(Auth auth, UUID resumeId) throws Exception {
        return mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/reparse")
                        .header("Authorization", "Bearer " + auth.token()))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    // ---- Thanh cong ----

    @Test
    void reparse_ownV1Resume_returns202WithPendingRequestAndKeepsParseStatusDone() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-ok");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        JsonNode json = body(result);
        assertThat(json.get("id").asString()).isEqualTo(resumeId.toString());
        assertThat(json.get("parseStatus").asString()).isEqualTo("DONE");
        assertThat(json.get("schemaVersion").asString()).isEqualTo("1");
        assertThat(json.get("reparse").get("status").asString()).isEqualTo("PENDING");
        assertThat(json.get("reparse").get("errorMessage").isNull()).isTrue();

        assertThat(countRequests(resumeId)).isEqualTo(1);
        assertThat(resumeRepository.findById(resumeId).orElseThrow().getParseStatus()).isEqualTo(ParseStatus.DONE);
        // Chi tao yeu cau - khong goi LLM/embedding dong bo trong request.
        verifyNoInteractions(chatModel, embeddingModel);
    }

    // Yeu cau truoc da xong (DONE/FAILED) khong chan yeu cau moi - chi PENDING/RUNNING chan.
    @Test
    void reparse_previousRequestFailed_allowsNewRequest() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-after-failed");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");
        createRequest(resumeId, ResumeReparseRequestStatus.FAILED);

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(countRequests(resumeId)).isEqualTo(2);
    }

    // ---- 404 / 409 (R-R2) ----

    @Test
    void reparse_otherCandidatesResume_returns404AndCreatesNothing() throws Exception {
        Auth owner = registerAndLogin("candidate", "reparse-owner");
        Auth other = registerAndLogin("candidate", "reparse-other");
        UUID resumeId = createParsedResume(owner.userId(), "resume-parse-v1");

        MvcResult result = postReparse(other, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("RESUME_NOT_FOUND");
        assertThat(countRequests(resumeId)).isZero();
    }

    @Test
    void reparse_v2Resume_returns409AlreadyLatest() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-v2");
        UUID resumeId = createParsedResume(candidate.userId(), ResumeParsingService.PROMPT_VERSION);

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo("RESUME_REPARSE_NOT_ALLOWED");
        assertThat(json.get("message").asString()).isEqualTo("CV này đã có dữ liệu trích xuất mới nhất.");
        assertThat(countRequests(resumeId)).isZero();
    }

    @Test
    void reparse_resumeNotDone_returns409() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-pending");
        UUID resumeId = createResume(candidate.userId(), ParseStatus.PENDING);

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo("RESUME_REPARSE_NOT_ALLOWED");
        assertThat(json.get("message").asString())
                .isEqualTo("CV chưa phân tích xong, chưa thể cập nhật dữ liệu trích xuất.");
        assertThat(countRequests(resumeId)).isZero();
    }

    @Test
    void reparse_resumeFailed_returns409() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-failed");
        UUID resumeId = createResume(candidate.userId(), ParseStatus.FAILED);

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo("RESUME_REPARSE_NOT_ALLOWED");
        assertThat(json.get("message").asString())
                .isEqualTo("CV chưa phân tích xong, chưa thể cập nhật dữ liệu trích xuất.");
        assertThat(countRequests(resumeId)).isZero();
    }

    @Test
    void reparse_pendingRequestExists_returns409InProgress() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-busy");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");
        createRequest(resumeId, ResumeReparseRequestStatus.PENDING);

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        JsonNode json = body(result);
        assertThat(json.get("error").asString()).isEqualTo("RESUME_REPARSE_IN_PROGRESS");
        assertThat(json.get("message").asString()).isEqualTo("CV đang được cập nhật dữ liệu trích xuất.");
        assertThat(countRequests(resumeId)).isEqualTo(1);
    }

    @Test
    void reparse_runningRequestExists_returns409InProgress() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-running");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");
        createRequest(resumeId, ResumeReparseRequestStatus.RUNNING);

        MvcResult result = postReparse(candidate, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).get("error").asString()).isEqualTo("RESUME_REPARSE_IN_PROGRESS");
    }

    // Chot chan that o DB: hai yeu cau PENDING cho cung CV (race vuot qua buoc kiem o service) -> partial
    // unique index uq_resume_reparse_request_active chan cai thu hai.
    @Test
    void activeRequestUniqueIndex_secondPendingForSameResume_rejectedByDatabase() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-race");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");
        createRequest(resumeId, ResumeReparseRequestStatus.PENDING);

        DataIntegrityViolationException error = assertThrows(
                DataIntegrityViolationException.class,
                () -> createRequest(resumeId, ResumeReparseRequestStatus.RUNNING));

        assertThat(error.getMostSpecificCause().getMessage()).contains("uq_resume_reparse_request_active");
        assertThat(countRequests(resumeId)).isEqualTo(1);
    }

    // ---- RBAC (muc 7.8) ----

    @Test
    void reparse_hrUser_returns403() throws Exception {
        Auth hr = registerAndLogin("hr", "reparse-hr");
        Auth candidate = registerAndLogin("candidate", "reparse-hr-target");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");

        MvcResult result = postReparse(hr, resumeId);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(countRequests(resumeId)).isZero();
    }

    @Test
    void reparse_withoutToken_returns401() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-anon");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");

        MvcResult result = mockMvc.perform(post("/api/candidates/resumes/" + resumeId + "/reparse")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(countRequests(resumeId)).isZero();
    }

    // ---- GET danh sach CV: schemaVersion + reparse (REQUIREMENT muc 4) ----

    @Test
    void listMine_includesSchemaVersionAndLatestReparse() throws Exception {
        Auth candidate = registerAndLogin("candidate", "reparse-list");
        UUID v1Resume = createParsedResume(candidate.userId(), "resume-parse-v1");
        UUID v2Resume = createParsedResume(candidate.userId(), ResumeParsingService.PROMPT_VERSION);
        UUID pendingResume = createResume(candidate.userId(), ParseStatus.PENDING);
        ResumeReparseRequest failed = createRequest(v1Resume, ResumeReparseRequestStatus.FAILED);
        failed.setErrorMessage(ResumeParsingErrorCode.LLM_INVALID_JSON.formatted());
        reparseRequestRepository.saveAndFlush(failed);

        MvcResult result = mockMvc.perform(get("/api/candidates/resumes")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = body(result);
        JsonNode v1 = findById(list, v1Resume);
        JsonNode v2 = findById(list, v2Resume);
        JsonNode pending = findById(list, pendingResume);
        assertThat(v1.get("schemaVersion").asString()).isEqualTo("1");
        assertThat(v1.get("reparse").get("status").asString()).isEqualTo("FAILED");
        assertThat(v1.get("reparse").get("errorMessage").asString())
                .isEqualTo(ResumeParsingErrorCode.LLM_INVALID_JSON.formatted());
        assertThat(v2.get("schemaVersion").asString()).isEqualTo("2");
        assertThat(v2.get("reparse").isNull()).isTrue();
        assertThat(pending.get("schemaVersion").isNull()).isTrue();
        assertThat(pending.get("reparse").isNull()).isTrue();
    }

    private static JsonNode findById(JsonNode list, UUID id) {
        for (JsonNode item : list) {
            if (id.toString().equals(item.get("id").asString())) {
                return item;
            }
        }
        throw new IllegalStateException("Khong thay CV " + id + " trong danh sach");
    }

    // ---- GET parsed: tong quan nghe nghiep (REQUIREMENT muc 4) ----

    @Test
    void getParsed_v1RecordNotYetComputed_newFieldsNullAndExperienceNull() throws Exception {
        Auth candidate = registerAndLogin("candidate", "parsed-v1");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");

        MvcResult result = mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/parsed")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = body(result);
        assertThat(json.get("schemaVersion").asString()).isEqualTo("1");
        assertThat(json.get("currentTitle").isNull()).isTrue();
        assertThat(json.get("industry").isNull()).isTrue();
        assertThat(json.get("location").isNull()).isTrue();
        assertThat(json.get("locationText").isNull()).isTrue();
        assertThat(json.get("experience").isNull()).isTrue();
    }

    @Test
    void getParsed_v2RecordWithComputedExperience_returnsLabelsAndYears() throws Exception {
        Auth candidate = registerAndLogin("candidate", "parsed-v2");
        UUID resumeId = createResume(candidate.userId(), ParseStatus.DONE);
        ResumeParsedData data = new ResumeParsedData();
        data.setResumeId(resumeId);
        data.setRawText("CV goc gia lap");
        data.setData(new ResumeParsedPayload(
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                "Senior Java Developer", "IT_SOFTWARE", "Bình Dương"));
        data.setModel("claude-sonnet-4-6");
        data.setPromptVersion(ResumeParsingService.PROMPT_VERSION);
        data.setIndustryCode("IT_SOFTWARE");
        data.setRegionCode("HO_CHI_MINH");
        data.setExperienceMonths(42);
        data.setExperienceEntriesCounted(4);
        data.setExperienceEntriesSkipped(1);
        data.setExperienceComputedAt(java.time.Instant.parse("2026-09-15T03:00:00Z"));
        resumeParsedDataRepository.save(data);

        MvcResult result = mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/parsed")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = body(result);
        assertThat(json.get("schemaVersion").asString()).isEqualTo("2");
        assertThat(json.get("currentTitle").asString()).isEqualTo("Senior Java Developer");
        assertThat(json.get("industry").get("code").asString()).isEqualTo("IT_SOFTWARE");
        assertThat(json.get("industry").get("label").asString()).isEqualTo("Công nghệ thông tin - Phần mềm");
        assertThat(json.get("location").get("code").asString()).isEqualTo("HO_CHI_MINH");
        assertThat(json.get("location").get("label").asString()).isEqualTo("TP. Hồ Chí Minh");
        assertThat(json.get("locationText").asString()).isEqualTo("Bình Dương");
        JsonNode experience = json.get("experience");
        assertThat(experience.get("months").asString()).isEqualTo("42");
        assertThat(experience.get("years").asString()).isEqualTo("3.5");
        assertThat(experience.get("countedEntries").asString()).isEqualTo("4");
        assertThat(experience.get("skippedEntries").asString()).isEqualTo("1");
        assertThat(experience.get("referenceMonth").asString()).isEqualTo("2026-09");
    }

    // Da tinh nhung khong co muc nao doc duoc: experience co mat, months/years null (khong 0).
    @Test
    void getParsed_computedWithNoCountedEntries_monthsAndYearsNull() throws Exception {
        Auth candidate = registerAndLogin("candidate", "parsed-nomonths");
        UUID resumeId = createParsedResume(candidate.userId(), "resume-parse-v1");
        jdbcTemplate.update(
                "UPDATE resume_parsed_data SET experience_entries_counted = 0, experience_entries_skipped = 2, "
                        + "experience_computed_at = now() WHERE resume_id = ?",
                resumeId);

        MvcResult result = mockMvc.perform(get("/api/candidates/resumes/" + resumeId + "/parsed")
                        .header("Authorization", "Bearer " + candidate.token()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode experience = body(result).get("experience");
        assertThat(experience.isNull()).isFalse();
        assertThat(experience.get("months").isNull()).isTrue();
        assertThat(experience.get("years").isNull()).isTrue();
        assertThat(experience.get("skippedEntries").asString()).isEqualTo("2");
    }
}
