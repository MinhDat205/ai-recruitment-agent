package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-U08 E1 (GET /api/candidates/applications/{id}) - T1-T8, T11, T12 cua REQUIREMENT.md muc 7.1 (T9 o
// CandidateApplicationCrossEndpointAccessIntegrationTest). Lop tu tao TOAN BO du lieu cua minh qua API/
// repository (email duy nhat), khong doc seed hay du lieu lop test khac. Bo helper chep theo
// ApplicationHrDetailControllerIntegrationTest (tien le du an: khong tach tien ich test dung chung).
// @Transactional: moi @Test rollback rieng; MockMvc chay cung thread nen request tham gia transaction cua
// test - thay doi qua repository phai saveAndFlush truoc khi goi E1 (findOpenJobById la native query).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApplicationCandidateDetailControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Muc 4 REQUIREMENT - so khop NGUYEN TEN KHOA (bang chinh xac), KHONG theo chuoi con:
    // categoryLabel/locationLabel hop le, chi khoa ten dung "label" bi cam. KHONG bo khoa nao de test xanh.
    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "totalScore",
            "rank",
            "criterionScores",
            "explanation",
            "explanationStatus",
            "scoringRunId",
            "latestScoringRunId",
            "latestScoringRunStatus",
            "latestScoringRunFinishedAt",
            "sentBy",
            "changedBy",
            "candidateId",
            "aiConsent",
            "recruitmentCycle",
            "description",
            "requirements",
            "fileUrl",
            "verdict",
            "label",
            "isQualified",
            "passed",
            "recommendation");

    private static final Set<String> AVAILABILITY_VALUES = Set.of("OPEN", "EXPIRED", "PAUSED", "CLOSED", "UNAVAILABLE");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private CatalogRegistry catalogRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    // Phan truoc @ = prefix + "-" + UUID (36 ky tu) phai <= 64 ky tu (gioi han cua @Email) -> prefix toi da
    // 27 ky tu, ke ca hau to helper tu noi them ("-hr", "-cand", "-other").
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

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
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

    private String uploadResume(String candidateToken, String fileName) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", fileName, "application/pdf", "%PDF-1.4 noi dung CV gia lap".getBytes());
        MvcResult result = mockMvc
                .perform(multipart("/api/candidates/resumes").file(file).header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    // coverLetterJson: doan JSON da escape san (vd "\"Dong 1\\nDong 2\"") hoac null de bo han field.
    private String apply(String candidateToken, String jobId, String resumeId, String coverLetterJson)
            throws Exception {
        String coverLetterField = coverLetterJson == null ? "" : ",\"coverLetter\":" + coverLetterJson;
        String body = """
                {"jobId":"%s","resumeId":"%s","aiConsent":true%s}
                """.formatted(jobId, resumeId, coverLetterField);
        MvcResult result = mockMvc
                .perform(post("/api/candidates/applications")
                        .header("Authorization", "Bearer " + candidateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private MvcResult getDetail(String token, String applicationId) throws Exception {
        var request = get("/api/candidates/applications/" + applicationId);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request).andReturn();
    }

    private record Fixture(
            String hrToken,
            String candidateToken,
            String jobId,
            String jobTitle,
            String companyName,
            String resumeId,
            String applicationId) {
    }

    private Fixture createApplication(String prefix, String coverLetterJson) throws Exception {
        String hrToken = registerAndLoginHr(prefix + "-hr");
        String companyName = uniqueName("Cong ty " + prefix);
        createCompany(hrToken, companyName);
        String jobTitle = uniqueName("Job " + prefix);
        String jobId = createJob(hrToken, jobTitle);
        addCriterion(hrToken, jobId);
        openJob(hrToken, jobId);

        String candidateToken = registerAndLoginCandidate(prefix + "-cand");
        String resumeId = uploadResume(candidateToken, "cv.pdf");
        String applicationId = apply(candidateToken, jobId, resumeId, coverLetterJson);
        return new Fixture(hrToken, candidateToken, jobId, jobTitle, companyName, resumeId, applicationId);
    }

    // "Ngay hien tai" theo DB (CURRENT_DATE cua Postgres, cung transaction voi E1) - KHONG dung LocalDate.now():
    // JVM va Postgres co the khac mui gio, lech ngay o ranh gioi (R-D4, muc 8).
    private LocalDate dbCurrentDate() {
        Object value = entityManager.createNativeQuery("SELECT CURRENT_DATE").getSingleResult();
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new IllegalStateException("Kieu CURRENT_DATE khong ho tro: " + value.getClass());
    }

    private void updateJob(String jobId, java.util.function.Consumer<Job> change) {
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        change.accept(job);
        jobRepository.saveAndFlush(job);
    }

    private String availabilityOf(String candidateToken, String applicationId) throws Exception {
        MvcResult result = getDetail(candidateToken, applicationId);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String value = body(result).get("job").get("availability").asString();
        assertThat(value).isIn(AVAILABILITY_VALUES);
        return value;
    }

    private static Set<String> keysOf(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    // Duyet de quy MOI khoa o MOI cap (object long nhau va phan tu mang).
    private static void collectKeys(JsonNode node, List<String> sink) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                sink.add(entry.getKey());
                collectKeys(entry.getValue(), sink);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node.values()) {
                collectKeys(child, sink);
            }
        }
    }

    // ---- case duong ----

    // T1 - don cua minh -> 200, du MOI field muc 4 voi gia tri dung, khong thua field nao.
    @Test
    void getDetail_ownApplication_returnsAllFields() throws Exception {
        Fixture fixture = createApplication("u08-own", "\"Toi rat quan tam\"");
        Job job = jobRepository.findById(UUID.fromString(fixture.jobId())).orElseThrow();

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(keysOf(json))
                .containsExactlyInAnyOrder("id", "status", "appliedAt", "updatedAt", "coverLetter", "job", "resume");
        assertThat(json.get("id").asString()).isEqualTo(fixture.applicationId());
        assertThat(json.get("status").asString()).isEqualTo("PENDING");
        assertThat(json.get("appliedAt").isNull()).isFalse();
        assertThat(json.get("updatedAt").isNull()).isFalse();
        assertThat(json.get("coverLetter").asString()).isEqualTo("Toi rat quan tam");

        JsonNode jobJson = json.get("job");
        assertThat(keysOf(jobJson))
                .containsExactlyInAnyOrder(
                        "id",
                        "title",
                        "companyId",
                        "companyName",
                        "availability",
                        "categoryCode",
                        "categoryLabel",
                        "locationCode",
                        "locationLabel",
                        "legacyCategory",
                        "legacyLocation",
                        "employmentType",
                        "workMode",
                        "salaryMin",
                        "salaryMax",
                        "salaryCurrency",
                        "deadline");
        assertThat(jobJson.get("id").asString()).isEqualTo(fixture.jobId());
        assertThat(jobJson.get("title").asString()).isEqualTo(fixture.jobTitle());
        assertThat(jobJson.get("companyId").asString()).isEqualTo(job.getCompanyId().toString());
        assertThat(jobJson.get("companyName").asString()).isEqualTo(fixture.companyName());
        assertThat(jobJson.get("availability").asString()).isEqualTo("OPEN");
        assertThat(jobJson.get("categoryCode").asString()).isEqualTo("IT_SOFTWARE");
        assertThat(jobJson.get("categoryLabel").asString()).isEqualTo(catalogRegistry.industryLabel("IT_SOFTWARE"));
        assertThat(jobJson.get("locationCode").asString()).isEqualTo("HA_NOI");
        assertThat(jobJson.get("locationLabel").asString()).isEqualTo(catalogRegistry.provinceLabel("HA_NOI"));
        assertThat(jobJson.get("legacyCategory").isNull()).isTrue();
        assertThat(jobJson.get("legacyLocation").isNull()).isTrue();
        // Tin tao qua API khong gui hinh thuc/luong/han nop -> null.
        assertThat(jobJson.get("employmentType").isNull()).isTrue();
        assertThat(jobJson.get("workMode").isNull()).isTrue();
        assertThat(jobJson.get("salaryMin").isNull()).isTrue();
        assertThat(jobJson.get("salaryMax").isNull()).isTrue();
        assertThat(jobJson.get("deadline").isNull()).isTrue();
        assertThat(jobJson.get("salaryCurrency").isNull() ? null : jobJson.get("salaryCurrency").asString())
                .isEqualTo(job.getSalaryCurrency());

        JsonNode resumeJson = json.get("resume");
        assertThat(keysOf(resumeJson)).containsExactlyInAnyOrder("id", "fileName", "parseStatus", "parseError");
        assertThat(resumeJson.get("id").asString()).isEqualTo(fixture.resumeId());
        assertThat(resumeJson.get("fileName").asString()).isEqualTo("cv.pdf");
        assertThat(resumeJson.get("parseStatus").asString()).isEqualTo("PENDING");
        assertThat(resumeJson.get("parseError").isNull()).isTrue();
    }

    // T5 - CV cua don la job_applications.resume_id, KHONG phai CV chinh hien tai (R-D2).
    @Test
    void getDetail_candidateChangedPrimaryAfterApplying_returnsAppliedResume() throws Exception {
        Fixture fixture = createApplication("u08-primary", null);
        String newResumeId = uploadResume(fixture.candidateToken(), "cv-moi.pdf");
        mockMvc
                .perform(patch("/api/candidates/resumes/" + newResumeId + "/primary")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk());
        assertThat(resumeRepository.findById(UUID.fromString(newResumeId)).orElseThrow().isPrimary()).isTrue();

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode resumeJson = body(result).get("resume");
        assertThat(resumeJson.get("id").asString()).isEqualTo(fixture.resumeId());
        assertThat(resumeJson.get("fileName").asString()).isEqualTo("cv.pdf");
    }

    // T6 (bien han nop) - OPEN: han NULL -> OPEN; ngay DB - 1 -> EXPIRED; = ngay DB -> OPEN; ngay DB + 1 -> OPEN.
    @Test
    void getDetail_openJobDeadlineBoundaries_availabilityMatchesPublicJobRule() throws Exception {
        Fixture fixture = createApplication("u08-deadline", null);
        LocalDate today = dbCurrentDate();

        updateJob(fixture.jobId(), job -> job.setDeadline(null));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("OPEN");

        updateJob(fixture.jobId(), job -> job.setDeadline(today.minusDays(1)));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("EXPIRED");

        updateJob(fixture.jobId(), job -> job.setDeadline(today));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("OPEN");

        updateJob(fixture.jobId(), job -> job.setDeadline(today.plusDays(1)));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("OPEN");
    }

    // T6 (trang thai) - PAUSED -> PAUSED; CLOSED -> CLOSED; DRAFT -> UNAVAILABLE (gop voi xoa mem, R-D4).
    @Test
    void getDetail_jobPausedClosedDraft_availabilityByStatus() throws Exception {
        Fixture fixture = createApplication("u08-status", null);

        updateJob(fixture.jobId(), job -> job.setStatus(JobStatus.PAUSED));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("PAUSED");

        updateJob(fixture.jobId(), job -> job.setStatus(JobStatus.CLOSED));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("CLOSED");

        updateJob(fixture.jobId(), job -> job.setStatus(JobStatus.DRAFT));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("UNAVAILABLE");
    }

    // T6 (xoa mem) - tin OPEN con han da xoa mem -> UNAVAILABLE, tom tat van tra dung; tin CLOSED da xoa
    // mem -> UNAVAILABLE (xoa mem thang status, R-D4).
    @Test
    void getDetail_softDeletedJob_unavailableAndSummaryStillReturned() throws Exception {
        Fixture fixture = createApplication("u08-deleted", null);
        LocalDate deadline = dbCurrentDate().plusDays(10);
        updateJob(fixture.jobId(), job -> {
            job.setDeadline(deadline);
            job.setSalaryMin(new java.math.BigDecimal("9000000.00"));
            job.setSalaryMax(new java.math.BigDecimal("14000000.00"));
            job.setSalaryCurrency("VND");
            job.setDeletedAt(Instant.now());
        });

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode jobJson = body(result).get("job");
        assertThat(jobJson.get("availability").asString()).isEqualTo("UNAVAILABLE");
        assertThat(jobJson.get("title").asString()).isEqualTo(fixture.jobTitle());
        assertThat(jobJson.get("salaryMin").decimalValue()).isEqualByComparingTo("9000000");
        assertThat(jobJson.get("salaryMax").decimalValue()).isEqualByComparingTo("14000000");
        assertThat(jobJson.get("salaryCurrency").asString()).isEqualTo("VND");
        assertThat(jobJson.get("deadline").asString()).isEqualTo(deadline.toString());

        updateJob(fixture.jobId(), job -> job.setStatus(JobStatus.CLOSED));
        assertThat(availabilityOf(fixture.candidateToken(), fixture.applicationId())).isEqualTo("UNAVAILABLE");
    }

    // T7 (duong) - thu nhieu dong co ky tu HTML/& tra NGUYEN VAN, giu \n (R-D7).
    @Test
    void getDetail_multilineCoverLetterWithHtml_returnsVerbatim() throws Exception {
        Fixture fixture = createApplication("u08-cover", "\"Dong 1\\nDong 2 <b>x</b> & y\\n\"");

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("coverLetter").asString()).isEqualTo("Dong 1\nDong 2 <b>x</b> & y\n");
    }

    // T7 (am) - khong nhap thu -> null, khong phai chuoi rong.
    @Test
    void getDetail_noCoverLetter_returnsNull() throws Exception {
        Fixture fixture = createApplication("u08-nocover", null);

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("coverLetter").isNull()).isTrue();
    }

    // T8 - tin chua chuan hoa (ma null, cot cu co gia tri) -> legacy* = gia tri cu, label null (R-D6). Tin co
    // ma -> label, legacy* null: da khang dinh o T1.
    @Test
    void getDetail_unnormalizedJob_returnsLegacyCatalogValues() throws Exception {
        Fixture fixture = createApplication("u08-legacy", null);
        updateJob(fixture.jobId(), job -> {
            job.setCategoryCode(null);
            job.setLocationCode(null);
            job.setCategory("Luat - Phap che doanh nghiep");
            job.setLocation("Sai Gon (gan san bay)");
        });

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode jobJson = body(result).get("job");
        assertThat(jobJson.get("categoryCode").isNull()).isTrue();
        assertThat(jobJson.get("categoryLabel").isNull()).isTrue();
        assertThat(jobJson.get("locationCode").isNull()).isTrue();
        assertThat(jobJson.get("locationLabel").isNull()).isTrue();
        assertThat(jobJson.get("legacyCategory").asString()).isEqualTo("Luat - Phap che doanh nghiep");
        assertThat(jobJson.get("legacyLocation").asString()).isEqualTo("Sai Gon (gan san bay)");
    }

    // T11 (duong) - CV FAILED -> tra dung chuoi trong cot parse_error (R-D8).
    @Test
    void getDetail_failedResume_returnsStoredParseError() throws Exception {
        Fixture fixture = createApplication("u08-cvfail", null);
        Resume resume = resumeRepository.findById(UUID.fromString(fixture.resumeId())).orElseThrow();
        resume.setParseStatus(ParseStatus.FAILED);
        resume.setParseError("EXTRACT_EMPTY: Không trích xuất được chữ từ tệp CV.");
        resumeRepository.saveAndFlush(resume);

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode resumeJson = body(result).get("resume");
        assertThat(resumeJson.get("parseStatus").asString()).isEqualTo("FAILED");
        assertThat(resumeJson.get("parseError").asString())
                .isEqualTo("EXTRACT_EMPTY: Không trích xuất được chữ từ tệp CV.");
    }

    // T11 (am) - CV PENDING -> parseError null.
    @Test
    void getDetail_pendingResume_parseErrorNull() throws Exception {
        Fixture fixture = createApplication("u08-cvpend", null);

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode resumeJson = body(result).get("resume");
        assertThat(resumeJson.get("parseStatus").asString()).isEqualTo("PENDING");
        assertThat(resumeJson.get("parseError").isNull()).isTrue();
    }

    // T12 - rut don roi goi lai E1 -> WITHDRAWN; lich su co dong cuoi PENDING -> WITHDRAWN.
    @Test
    void getDetail_afterWithdraw_returnsWithdrawnAndHistoryHasWithdrawEntry() throws Exception {
        Fixture fixture = createApplication("u08-withdraw", null);
        mockMvc
                .perform(patch("/api/candidates/applications/" + fixture.applicationId() + "/withdraw")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk());

        MvcResult detail = getDetail(fixture.candidateToken(), fixture.applicationId());
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(detail).get("status").asString()).isEqualTo("WITHDRAWN");

        MvcResult history = mockMvc
                .perform(get("/api/candidates/applications/" + fixture.applicationId() + "/history")
                        .header("Authorization", "Bearer " + fixture.candidateToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = body(history);
        List<JsonNode> list = new ArrayList<>(entries.values());
        assertThat(list).isNotEmpty();
        JsonNode last = list.get(list.size() - 1);
        assertThat(last.get("fromStatus").asString()).isEqualTo("PENDING");
        assertThat(last.get("toStatus").asString()).isEqualTo("WITHDRAWN");
    }

    // ---- case am ----

    // T2 - don cua ung vien khac va don khong ton tai -> CUNG 404 APPLICATION_NOT_FOUND (R-Q2).
    @Test
    void getDetail_otherCandidateOrMissing_bothReturn404ApplicationNotFound() throws Exception {
        Fixture fixture = createApplication("u08-other", null);
        String otherCandidateToken = registerAndLoginCandidate("u08-other-cand2");

        MvcResult otherResult = getDetail(otherCandidateToken, fixture.applicationId());
        MvcResult missingResult = getDetail(otherCandidateToken, UUID.randomUUID().toString());

        assertThat(otherResult.getResponse().getStatus()).isEqualTo(404);
        assertThat(missingResult.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherResult).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
        assertThat(body(missingResult).get("error").asString()).isEqualTo("APPLICATION_NOT_FOUND");
    }

    // T3 - token HR -> 403 o filter chain (/api/candidates/**).
    @Test
    void getDetail_hrToken_returns403() throws Exception {
        Fixture fixture = createApplication("u08-hrtoken", null);

        MvcResult result = getDetail(fixture.hrToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    // T3 - khong token -> 401 UNAUTHENTICATED.
    @Test
    void getDetail_noToken_returns401() throws Exception {
        Fixture fixture = createApplication("u08-notoken", null);

        MvcResult result = getDetail(null, fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).get("error").asString()).isEqualTo("UNAUTHENTICATED");
    }

    // T4 - JSON (duyet de quy moi cap) khong chua khoa cam nao; so khop NGUYEN TEN KHOA. Tin co du luong/han
    // nop/thu gioi thieu de moi field deu co gia tri, khong an khoa nao vi null.
    @Test
    void getDetail_responseHasNoForbiddenKeysAtAnyLevel() throws Exception {
        Fixture fixture = createApplication("u08-forbid", "\"Thu gioi thieu\"");
        updateJob(fixture.jobId(), job -> {
            job.setSalaryMin(new java.math.BigDecimal("9000000.00"));
            job.setSalaryMax(new java.math.BigDecimal("14000000.00"));
            job.setDeadline(dbCurrentDate().plusDays(5));
        });

        MvcResult result = getDetail(fixture.candidateToken(), fixture.applicationId());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<String> keys = new ArrayList<>();
        collectKeys(body(result), keys);
        // Kiem tra chinh helper: categoryLabel/locationLabel PHAI co mat va KHONG bi coi la khoa cam.
        assertThat(keys).contains("categoryLabel", "locationLabel");
        assertThat(keys).doesNotContainAnyElementsOf(FORBIDDEN_KEYS);
    }
}
