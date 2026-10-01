package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
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

// REQUIREMENT FR-C05 muc 7.3 (guard mo tin R-J3/R-J4/R-J6, ma la R-J5) va 7.9 (legacyCategory/
// legacyLocation o 3 trang thai). Di qua API that /api/hr/jobs de kiem dung duong ma HR dung.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobCatalogGuardIntegrationTest {

    private static final String OPENING_MESSAGE =
            "Cần chọn ngành nghề và tỉnh/thành từ danh mục trước khi mở tin tuyển dụng.";
    private static final String OPENING_REMOTE_MESSAGE = "Cần chọn ngành nghề từ danh mục trước khi mở tin tuyển dụng.";
    private static final String OPEN_UPDATE_MESSAGE = "Tin đang mở phải giữ ngành nghề và tỉnh/thành từ danh mục.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EntityManager entityManager;

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // null -> field vang mat trong JSON (Jackson gan null), khong phai chuoi "null".
    private static String jsonField(String name, String value) {
        return value == null ? "" : ",\"" + name + "\":\"" + value + "\"";
    }

    private String registerAndLoginHr() throws Exception {
        String email = "hr-catalog-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register/hr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung","phone":"0900000000"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = extractJsonField(body(login), "accessToken");
        mockMvc.perform(post("/api/hr/companies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cong ty Catalog %s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());
        return token;
    }

    private String jobJson(String title, String categoryCode, String locationCode, String workMode) {
        return "{\"title\":\"" + title + "\",\"description\":\"Mo ta cong viec\""
                + jsonField("categoryCode", categoryCode)
                + jsonField("locationCode", locationCode)
                + jsonField("workMode", workMode)
                + "}";
    }

    private MvcResult createJobRaw(String token, String categoryCode, String locationCode, String workMode)
            throws Exception {
        String request = "{\"job\":" + jobJson("Tin danh muc", categoryCode, locationCode, workMode)
                + ",\"interviewTemplate\":{\"subject\":\"Thu moi\",\"body\":\"Moi ban phong van.\","
                + "\"senderName\":\"Phong Nhan Su\"}}";
        return mockMvc.perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn();
    }

    // Tao Job DRAFT + rubric du 100% de chi con dieu kien danh muc quyet dinh viec mo tin.
    private String createOpenableJob(String token, String categoryCode, String locationCode, String workMode)
            throws Exception {
        MvcResult created = createJobRaw(token, categoryCode, locationCode, workMode);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String jobId = extractJsonField(body(created), "id");
        mockMvc.perform(post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Kinh nghiem","weight":100}
                                """))
                .andExpect(status().isCreated());
        return jobId;
    }

    private MvcResult changeStatus(String token, String jobId, String status) throws Exception {
        return mockMvc.perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andReturn();
    }

    private MvcResult update(String token, String jobId, String title, String categoryCode, String locationCode,
            String workMode) throws Exception {
        return mockMvc.perform(put("/api/hr/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson(title, categoryCode, locationCode, workMode)))
                .andReturn();
    }

    private MvcResult getMine(String token, String jobId) throws Exception {
        return mockMvc.perform(get("/api/hr/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static void assertError(MvcResult result, int status, String code, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(body(result)).contains("\"error\":\"" + code + "\"").contains("\"message\":\"" + message + "\"");
    }

    // Job "chua chuan hoa": khong ma, co gia tri cu - chi dung duoc bang repository (API khong con ghi
    // cot cu), dung trang thai du lieu cu con lai sau V9.
    private void makeLegacy(String jobId, String category, String location, JobStatus status) {
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        job.setCategoryCode(null);
        job.setLocationCode(null);
        job.setCategory(category);
        job.setLocation(location);
        job.setStatus(status);
        jobRepository.saveAndFlush(job);
    }

    // ===== 7.3 - chuyen sang OPEN =====

    @Test
    void draftToOpen_withoutCategory_returns409() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, null, "HA_NOI", "ONSITE");

        assertError(changeStatus(token, jobId, "OPEN"), 409, "JOB_CATALOG_INCOMPLETE", OPENING_MESSAGE);
        assertThat(jobRepository.findById(UUID.fromString(jobId)).orElseThrow().getStatus())
                .isEqualTo(JobStatus.DRAFT);
    }

    @Test
    void draftToOpen_onsiteWithoutLocation_returns409() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", null, "ONSITE");

        assertError(changeStatus(token, jobId, "OPEN"), 409, "JOB_CATALOG_INCOMPLETE", OPENING_MESSAGE);
    }

    @Test
    void draftToOpen_remoteWithoutLocation_succeeds() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", null, "REMOTE");

        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void draftToOpen_remoteWithoutCategory_returns409WithRemoteMessage() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, null, null, "REMOTE");

        assertError(changeStatus(token, jobId, "OPEN"), 409, "JOB_CATALOG_INCOMPLETE", OPENING_REMOTE_MESSAGE);
    }

    @Test
    void draftToOpen_withBothCodes_succeeds() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");

        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void pausedToOpen_afterCategoryRemovedWhilePaused_returns409() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
        assertThat(changeStatus(token, jobId, "PAUSED").getResponse().getStatus()).isEqualTo(200);
        // Dang PAUSED nen xoa ma duoc (R-J3 chi rang buoc tin OPEN).
        assertThat(update(token, jobId, "Tin danh muc", null, "HA_NOI", "ONSITE").getResponse().getStatus())
                .isEqualTo(200);

        assertError(changeStatus(token, jobId, "OPEN"), 409, "JOB_CATALOG_INCOMPLETE", OPENING_MESSAGE);
    }

    @Test
    void closedToOpen_afterLocationRemovedWhileClosed_returns409() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
        assertThat(changeStatus(token, jobId, "CLOSED").getResponse().getStatus()).isEqualTo(200);
        assertThat(update(token, jobId, "Tin danh muc", "IT_SOFTWARE", null, "ONSITE").getResponse().getStatus())
                .isEqualTo(200);

        assertError(changeStatus(token, jobId, "OPEN"), 409, "JOB_CATALOG_INCOMPLETE", OPENING_MESSAGE);
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        // Mo lai bi chan thi recruitment_cycle KHONG duoc tang.
        assertThat(job.getRecruitmentCycle()).isEqualTo(1);
    }

    @Test
    void legacyUnnormalizedPausedJob_cannotReopen() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        makeLegacy(jobId, "Nganh cu", "Quan 1, HCM", JobStatus.PAUSED);

        assertError(changeStatus(token, jobId, "OPEN"), 409, "JOB_CATALOG_INCOMPLETE", OPENING_MESSAGE);
    }

    @Test
    void rubricIsCheckedBeforeCatalog() throws Exception {
        String token = registerAndLoginHr();
        MvcResult created = createJobRaw(token, null, null, "ONSITE");
        String jobId = extractJsonField(body(created), "id");

        MvcResult result = changeStatus(token, jobId, "OPEN");
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result)).contains("\"error\":\"RUBRIC_INCOMPLETE\"");
    }

    // ===== 7.3 - update khi Job dang OPEN =====

    @Test
    void updateOpenJob_removingCategory_returns409_andKeepsCode() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);

        assertError(update(token, jobId, "Tieu de moi", null, "HA_NOI", "ONSITE"), 409, "JOB_CATALOG_INCOMPLETE",
                OPEN_UPDATE_MESSAGE);

        // Class test bao @Transactional: entity bi applyRequest sua van nam trong persistence context
        // (production thi transaction rollback). clear() de doc lai gia tri THAT trong DB - khong co
        // flush nao truoc khi nem loi.
        entityManager.clear();
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getCategoryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(job.getTitle()).isEqualTo("Tin danh muc");
    }

    @Test
    void updateOpenJob_remoteToOnsiteWithoutLocation_returns409() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", null, "REMOTE");
        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);

        assertError(update(token, jobId, "Tin danh muc", "IT_SOFTWARE", null, "ONSITE"), 409,
                "JOB_CATALOG_INCOMPLETE", OPEN_UPDATE_MESSAGE);
    }

    @Test
    void updateOpenJob_remoteRemovingCategory_returns409WithRemoteMessage() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", null, "REMOTE");
        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);

        assertError(update(token, jobId, "Tin danh muc", null, null, "REMOTE"), 409, "JOB_CATALOG_INCOMPLETE",
                "Tin đang mở phải giữ ngành nghề từ danh mục.");
    }

    @Test
    void updateOpenJob_keepingCodes_succeeds() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);

        MvcResult result = update(token, jobId, "Tieu de moi", "SALES", "DA_NANG", "ONSITE");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).contains("\"categoryLabel\":\"Kinh doanh - Bán hàng\"");
    }

    // R-J6: Job OPEN cu chua chuan hoa (truoc da khong thoa R-J3) van luu duoc sua doi khac, van OPEN,
    // cot cu con nguyen.
    @Test
    void legacyOpenJob_updateOtherFields_succeedsAndStaysOpen() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        makeLegacy(jobId, "Nganh cu", "Quan 1, HCM", JobStatus.OPEN);

        MvcResult result = update(token, jobId, "Tieu de sua", null, null, "ONSITE");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(JobStatus.OPEN);
        assertThat(job.getTitle()).isEqualTo("Tieu de sua");
        assertThat(job.getCategory()).isEqualTo("Nganh cu");
        assertThat(job.getLocation()).isEqualTo("Quan 1, HCM");
    }

    // ===== 7.3 - ma khong co trong danh muc =====

    @Test
    void create_withUnknownCategoryCode_returns400_andCreatesNothing() throws Exception {
        String token = registerAndLoginHr();
        long before = jobRepository.count();

        assertError(createJobRaw(token, "KHONG_CO", "HA_NOI", "ONSITE"), 400, "INVALID_CATALOG_CODE",
                "Ngành nghề không có trong danh mục.");
        assertThat(jobRepository.count()).isEqualTo(before);
    }

    @Test
    void update_withUnknownLocationCode_returns400() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");

        assertError(update(token, jobId, "Tin danh muc", "IT_SOFTWARE", "TOAN_QUOC", "ONSITE"), 400,
                "INVALID_CATALOG_CODE", "Tỉnh/thành không có trong danh mục.");
        assertThat(jobRepository.findById(UUID.fromString(jobId)).orElseThrow().getLocationCode())
                .isEqualTo("HA_NOI");
    }

    // ===== 7.9 - legacyCategory/legacyLocation o 3 trang thai =====

    @Test
    void response_withCodes_hasLabelsAndNullLegacy_evenIfLegacyColumnsStillHaveValues() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        job.setCategory("Cong nghe thong tin");
        job.setLocation("Ha Noi cu");
        jobRepository.saveAndFlush(job);

        String json = body(getMine(token, jobId));

        assertThat(json)
                .contains("\"categoryCode\":\"IT_SOFTWARE\"")
                .contains("\"categoryLabel\":\"Công nghệ thông tin - Phần mềm\"")
                .contains("\"locationCode\":\"HA_NOI\"")
                .contains("\"locationLabel\":\"Hà Nội\"")
                .contains("\"legacyCategory\":null")
                .contains("\"legacyLocation\":null");
    }

    @Test
    void response_unnormalized_hasLegacyValuesAndNullCodes() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        makeLegacy(jobId, "Nganh cu", "Quan 1, HCM", JobStatus.DRAFT);

        String json = body(getMine(token, jobId));

        assertThat(json)
                .contains("\"categoryCode\":null")
                .contains("\"categoryLabel\":null")
                .contains("\"legacyCategory\":\"Nganh cu\"")
                .contains("\"locationCode\":null")
                .contains("\"legacyLocation\":\"Quan 1, HCM\"");
    }

    @Test
    void response_missingEntirely_hasAllNull() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, null, null, null);

        String json = body(getMine(token, jobId));

        assertThat(json)
                .contains("\"categoryCode\":null")
                .contains("\"categoryLabel\":null")
                .contains("\"legacyCategory\":null")
                .contains("\"locationCode\":null")
                .contains("\"locationLabel\":null")
                .contains("\"legacyLocation\":null")
                .doesNotContain("\"category\":")
                .doesNotContain("\"location\":");
    }

    // R-J1: chon ma qua API khong ghi de cot cu.
    @Test
    void choosingCodeViaUpdate_keepsLegacyColumnsUntouched() throws Exception {
        String token = registerAndLoginHr();
        String jobId = createOpenableJob(token, "IT_SOFTWARE", "HA_NOI", "ONSITE");
        makeLegacy(jobId, "Nganh cu", "Quan 1, HCM", JobStatus.DRAFT);

        MvcResult result = update(token, jobId, "Tin danh muc", "SALES", "HO_CHI_MINH", "ONSITE");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).contains("\"legacyCategory\":null").contains("\"legacyLocation\":null");
        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getCategoryCode()).isEqualTo("SALES");
        assertThat(job.getCategory()).isEqualTo("Nganh cu");
        assertThat(job.getLocation()).isEqualTo("Quan 1, HCM");
    }
}
