package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricRepository;
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

// @Transactional: moi @Test rollback rieng, tranh du lieu cua test nay lam sai lech test khac
// (vd jobRepository.count() trong test xoa mem).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobOwnerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private InterviewTemplateRepository interviewTemplateRepository;

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String extractJsonField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return matcher.group(1);
    }

    private long extractJsonNumber(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String registerAndLoginHr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Nha Tuyen Dung","phone":"0900000000"}
                """
                        .formatted(email);
        mockMvc
                .perform(post("/api/auth/register/hr").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        return extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
    }

    private void createCompany(String token, String name) throws Exception {
        String body = """
                {"name":"%s"}
                """.formatted(name);
        mockMvc
                .perform(
                        post("/api/hr/companies")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated());
    }

    // Tao Job va tao Mau giay moi phong van la mot request duy nhat (JobCreateRequest) - khong co
    // duong nao goi API tao Job ma thieu du lieu template hop le.
    private MvcResult createJobRaw(String token, String title) throws Exception {
        String body =
                """
                {
                  "job": {"title":"%s","description":"Mo ta cong viec"},
                  "interviewTemplate": {
                    "subject":"Thu moi phong van vi tri %s",
                    "body":"Kinh chao ung vien, chung toi moi ban tham gia phong van.",
                    "senderName":"Phong Nhan Su"
                  }
                }
                """
                        .formatted(title, title);
        return mockMvc
                .perform(
                        post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    // Thieu han khoi "interviewTemplate" trong request - phai bi chan boi Bean Validation
    // (@NotNull tren JobCreateRequest.interviewTemplate) truoc khi cham toi tang service.
    private MvcResult createJobRawMissingTemplate(String token, String title) throws Exception {
        String body = """
                {"job": {"title":"%s","description":"Mo ta cong viec"}}
                """.formatted(title);
        return mockMvc
                .perform(
                        post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    private String createJob(String token, String title) throws Exception {
        MvcResult result = createJobRaw(token, title);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private MvcResult changeStatus(String token, String jobId, String status) throws Exception {
        String body = """
                {"status":"%s"}
                """.formatted(status);
        return mockMvc
                .perform(
                        patch("/api/hr/jobs/" + jobId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
    }

    // B3: DRAFT -> OPEN bi chan neu rubric chua du 100% trong so. Cac test cua B2 mo job de
    // kiem tra recruitment_cycle/publishedAt can rubric du 100% truoc, khong lien quan gi den
    // logic dang kiem tra nhung van phai thoa dieu kien nay moi mo duoc job.
    private void fillRubricToFullWeight(String token, String jobId) throws Exception {
        String body = """
                {"name":"Kinh nghiem","weight":100}
                """;
        mockMvc
                .perform(
                        post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated());
    }

    private String addCriterion(String token, String jobId, String name, int weight) throws Exception {
        String body = """
                {"name":"%s","weight":%d}
                """.formatted(name, weight);
        MvcResult result = mockMvc
                .perform(
                        post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private void deleteCriterion(String token, String jobId, String criterionId) throws Exception {
        mockMvc
                .perform(
                        delete("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void createJob_alwaysCreatesRubricAndInterviewTemplateInSameTransaction() throws Exception {
        String token = registerAndLoginHr("hr-rubric");
        createCompany(token, "Cong ty Rubric");

        String jobId = createJob(token, "Backend Engineer");

        assertThat(rubricRepository.findByJobId(UUID.fromString(jobId))).isPresent();
        assertThat(interviewTemplateRepository.findByJobId(UUID.fromString(jobId))).isPresent();
    }

    // Chung minh khong the tao Job thieu template: thieu han khoi "interviewTemplate" trong
    // request bi chan o Bean Validation (400) TRUOC khi vao service - Job khong duoc tao ra.
    @Test
    void createJob_withoutInterviewTemplate_returnsBadRequestAndCreatesNothing() throws Exception {
        String token = registerAndLoginHr("hr-no-template");
        createCompany(token, "Cong ty Khong Template");
        long jobCountBefore = jobRepository.count();

        MvcResult result = createJobRawMissingTemplate(token, "Job Thieu Template");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(jobRepository.count()).isEqualTo(jobCountBefore);
    }

    // Chung minh THUC NGHIEM (khong chi suy luan tu cau hinh Jackson mac dinh) rang hinh dang
    // request PHANG cua luot 1 (truoc khi co JobCreateRequest) khong con tao duoc Job nua.
    // Jackson mac dinh cua Spring Boot khong bat fail-on-unknown-properties, nen "title"/
    // "description" o cap ngoai bi bo qua lang le thay vi bao loi ro rang - neu khong co test
    // nay, kha nang "endpoint van am tham nhan JobRequest phang" se khong bi phat hien.
    @Test
    void createJob_withOldFlatBodyShape_returnsBadRequestAndCreatesNothing() throws Exception {
        String token = registerAndLoginHr("hr-old-shape");
        createCompany(token, "Cong ty Old Shape");
        long jobCountBefore = jobRepository.count();

        String oldFlatBody = """
                {"title":"Job Kieu Cu","description":"Mo ta cong viec"}
                """;
        MvcResult result = mockMvc
                .perform(
                        post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(oldFlatBody))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(jobRepository.count()).isEqualTo(jobCountBefore);
    }

    @Test
    void createJob_withoutCompany_returnsNotFound() throws Exception {
        String token = registerAndLoginHr("hr-no-company");

        MvcResult result = createJobRaw(token, "Job khong co cong ty");

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(result.getResponse().getContentAsString()).contains("COMPANY_NOT_FOUND");
    }

    @Test
    void deleteJob_softDeletes_rowStillExistsAndCountUnchanged() throws Exception {
        String token = registerAndLoginHr("hr-delete");
        createCompany(token, "Cong ty Xoa");
        String jobId = createJob(token, "Job Se Bi Xoa");

        long countBefore = jobRepository.count();

        mockMvc
                .perform(delete("/api/hr/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        long countAfter = jobRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);

        Job job = jobRepository.findById(UUID.fromString(jobId)).orElseThrow();
        assertThat(job.getDeletedAt()).isNotNull();
    }

    @Test
    void reopenClosedJob_incrementsRecruitmentCycle() throws Exception {
        String token = registerAndLoginHr("hr-reopen");
        createCompany(token, "Cong ty Reopen");
        String jobId = createJob(token, "Job Mo Lai");
        fillRubricToFullWeight(token, jobId);

        MvcResult openedFirstTime = changeStatus(token, jobId, "OPEN");
        assertThat(openedFirstTime.getResponse().getStatus()).isEqualTo(200);
        assertThat(extractJsonNumber(openedFirstTime.getResponse().getContentAsString(), "recruitmentCycle"))
                .isEqualTo(1);

        MvcResult closed = changeStatus(token, jobId, "CLOSED");
        assertThat(extractJsonNumber(closed.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);

        MvcResult reopened = changeStatus(token, jobId, "OPEN");
        assertThat(extractJsonNumber(reopened.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(2);
    }

    // Ve phu dinh: chi CLOSED -> OPEN moi tang cycle. PAUSED chi la tam dung trong CUNG mot dot
    // tuyen dung, khong phai mo lai - tang nham se lam sai uq_application_per_cycle o C2.
    @Test
    void pauseThenReopen_doesNotIncrementRecruitmentCycle() throws Exception {
        String token = registerAndLoginHr("hr-pause");
        createCompany(token, "Cong ty Pause");
        String jobId = createJob(token, "Job Tam Dung");
        fillRubricToFullWeight(token, jobId);

        MvcResult opened = changeStatus(token, jobId, "OPEN");
        assertThat(extractJsonNumber(opened.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);

        MvcResult paused = changeStatus(token, jobId, "PAUSED");
        assertThat(extractJsonNumber(paused.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);

        MvcResult resumed = changeStatus(token, jobId, "OPEN");
        assertThat(extractJsonNumber(resumed.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);
    }

    // Sua loi: PAUSED -> OPEN gio cung phai kiem tong trong so neu rubric CHUA khoa, giong
    // DRAFT/CLOSED -> OPEN. Truoc day bo qua ca truong hop nay vi tuong rubric co the da khoa,
    // nhung job chua tung OPEN (hoac chua co luot cham) thi rubric khong the bi khoa duoc.
    @Test
    void pauseThenReopen_withRubricBelowFullWeight_isBlocked() throws Exception {
        String token = registerAndLoginHr("hr-pause-under");
        createCompany(token, "Cong ty Pause Duoi Nguong");
        String jobId = createJob(token, "Job Tam Dung Roi Mo Lai");
        String criterionAId = addCriterion(token, jobId, "Tieu chi A", 60);
        addCriterion(token, jobId, "Tieu chi B", 40);

        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
        assertThat(changeStatus(token, jobId, "PAUSED").getResponse().getStatus()).isEqualTo(200);

        deleteCriterion(token, jobId, criterionAId);

        MvcResult resumed = changeStatus(token, jobId, "OPEN");
        assertThat(resumed.getResponse().getStatus()).isEqualTo(409);
        assertThat(resumed.getResponse().getContentAsString()).contains("RUBRIC_INCOMPLETE");
    }

    // Doi xung: rubric DA khoa (is_locked=true, mo phong da co luot cham dau tien) thi PAUSED ->
    // OPEN van duoc qua du duoi 100%, vi HR khong con cach nao sua lai rubric nua. Phai dua rubric
    // ve du 100% truoc khi OPEN lan dau (guard moi kiem ca lan dau tien), roi moi xoa bot tieu chi
    // va khoa rubric sau do - de co lap dung bien can kiem la is_locked, khong phai tinh co rubric
    // chua tung du 100%.
    @Test
    void pauseThenReopen_withLockedRubricBelowFullWeight_stillSucceeds() throws Exception {
        String token = registerAndLoginHr("hr-pause-locked");
        createCompany(token, "Cong ty Pause Da Khoa");
        String jobId = createJob(token, "Job Tam Dung Rubric Da Khoa");
        addCriterion(token, jobId, "Tieu chi A", 60);
        String criterionBId = addCriterion(token, jobId, "Tieu chi B", 40);

        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
        assertThat(changeStatus(token, jobId, "PAUSED").getResponse().getStatus()).isEqualTo(200);

        // Rubric con lai 60% - neu chua khoa se bi chan (giong test tren). Khoa rubric de mo phong
        // tinh huong da co luot cham dau tien, chi con bien is_locked khac biet.
        deleteCriterion(token, jobId, criterionBId);
        Rubric rubric = rubricRepository.findByJobId(UUID.fromString(jobId)).orElseThrow();
        rubric.setLocked(true);
        rubricRepository.save(rubric);

        MvcResult resumed = changeStatus(token, jobId, "OPEN");
        assertThat(resumed.getResponse().getStatus()).isEqualTo(200);
    }

    // Doi xung voi test tren: CLOSED -> OPEN (mo lai that su, tang recruitment_cycle) PHAI kiem
    // tong trong so, khac PAUSED -> OPEN.
    @Test
    void reopenClosedJob_withRubricBelowFullWeight_isBlocked() throws Exception {
        String token = registerAndLoginHr("hr-reopen-under");
        createCompany(token, "Cong ty Reopen Duoi Nguong");
        String jobId = createJob(token, "Job Dong Roi Mo Lai");
        String criterionAId = addCriterion(token, jobId, "Tieu chi A", 60);
        addCriterion(token, jobId, "Tieu chi B", 40);

        assertThat(changeStatus(token, jobId, "OPEN").getResponse().getStatus()).isEqualTo(200);
        assertThat(changeStatus(token, jobId, "CLOSED").getResponse().getStatus()).isEqualTo(200);

        deleteCriterion(token, jobId, criterionAId);

        MvcResult reopened = changeStatus(token, jobId, "OPEN");
        assertThat(reopened.getResponse().getStatus()).isEqualTo(409);
        assertThat(reopened.getResponse().getContentAsString()).contains("RUBRIC_INCOMPLETE");
    }

    @Test
    void hrA_updateHrBJob_returnsForbidden() throws Exception {
        String tokenA = registerAndLoginHr("hr-job-a");
        createCompany(tokenA, "Cong ty A");
        String jobIdA = createJob(tokenA, "Job Cua A");

        String tokenB = registerAndLoginHr("hr-job-b");
        createCompany(tokenB, "Cong ty B");

        String updateBody = """
                {"title":"Bi HR khac doi ten","description":"Mo ta moi"}
                """;
        mockMvc
                .perform(
                        put("/api/hr/jobs/" + jobIdA)
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrA_deleteHrBJob_returnsForbidden() throws Exception {
        String tokenA = registerAndLoginHr("hr-del-a");
        createCompany(tokenA, "Cong ty Del A");
        String jobIdA = createJob(tokenA, "Job Del A");

        String tokenB = registerAndLoginHr("hr-del-b");
        createCompany(tokenB, "Cong ty Del B");

        mockMvc
                .perform(delete("/api/hr/jobs/" + jobIdA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateToken_callingHrJobsEndpoint_returnsForbidden() throws Exception {
        String email = uniqueEmail("candidate");
        String registerBody =
                """
                {"email":"%s","password":"password123","fullName":"Ung Vien"}
                """
                        .formatted(email);
        mockMvc
                .perform(
                        post("/api/auth/register/candidate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerBody))
                .andExpect(status().isCreated());
        String loginBody = """
                {"email":"%s","password":"password123"}
                """.formatted(email);
        MvcResult loginResult =
                mockMvc
                        .perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                        .andExpect(status().isOk())
                        .andReturn();
        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");

        mockMvc
                .perform(get("/api/hr/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
