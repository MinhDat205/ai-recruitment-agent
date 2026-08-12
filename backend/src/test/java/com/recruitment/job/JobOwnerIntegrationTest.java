package com.recruitment.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
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

    private MvcResult createJobRaw(String token, String title) throws Exception {
        String body =
                """
                {"title":"%s","description":"Mo ta cong viec"}
                """
                        .formatted(title);
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

    @Test
    void createJob_alwaysCreatesRubricInSameTransaction() throws Exception {
        String token = registerAndLoginHr("hr-rubric");
        createCompany(token, "Cong ty Rubric");

        String jobId = createJob(token, "Backend Engineer");

        assertThat(rubricRepository.findByJobId(UUID.fromString(jobId))).isPresent();
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

        MvcResult opened = changeStatus(token, jobId, "OPEN");
        assertThat(extractJsonNumber(opened.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);

        MvcResult paused = changeStatus(token, jobId, "PAUSED");
        assertThat(extractJsonNumber(paused.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);

        MvcResult resumed = changeStatus(token, jobId, "OPEN");
        assertThat(extractJsonNumber(resumed.getResponse().getContentAsString(), "recruitmentCycle")).isEqualTo(1);
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
