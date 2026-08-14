package com.recruitment.rubric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
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

// @Transactional: moi @Test rollback rieng, giong JobOwnerIntegrationTest.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RubricOwnerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    private java.math.BigDecimal extractJsonDecimal(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":([0-9.]+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Khong tim thay field '" + field + "' trong: " + json);
        }
        return new java.math.BigDecimal(matcher.group(1));
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

    private String createJob(String token, String title) throws Exception {
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
        MvcResult result = mockMvc
                .perform(
                        post("/api/hr/jobs")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return extractJsonField(result.getResponse().getContentAsString(), "id");
    }

    private MvcResult addCriterion(String token, String jobId, String requestBody) throws Exception {
        return mockMvc
                .perform(
                        post("/api/hr/jobs/" + jobId + "/rubric/criteria")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andReturn();
    }

    @Test
    void addCriterion_withoutScaleDescription_succeeds() throws Exception {
        String token = registerAndLoginHr("hr-no-scale");
        createCompany(token, "Cong ty Khong Thang Diem");
        String jobId = createJob(token, "Backend Engineer");

        String body = """
                {"name":"Kinh nghiem Java","description":"Danh gia nam kinh nghiem","weight":40}
                """;

        MvcResult result = addCriterion(token, jobId, body);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"maxScore\":5");
    }

    // Xac nhan scale_description (JSONB) round-trip dung cau truc co dinh {level, description}[]
    // thay vi JSON tu do - day la thu D2 se doc de serialize vao prompt LLM.
    @Test
    void addCriterion_withScaleDescription_persistsStructuredLevels() throws Exception {
        String token = registerAndLoginHr("hr-with-scale");
        createCompany(token, "Cong ty Co Thang Diem");
        String jobId = createJob(token, "Senior Backend Engineer");

        String body =
                """
                {
                  "name":"Kinh nghiem Java",
                  "weight":40,
                  "scaleDescription": [
                    {"level":1,"description":"Chua co kinh nghiem"},
                    {"level":5,"description":"Chuyen gia, co the mentor nguoi khac"}
                  ]
                }
                """;

        MvcResult result = addCriterion(token, jobId, body);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("\"level\":1").contains("Chua co kinh nghiem");
        assertThat(responseBody).contains("\"level\":5").contains("Chuyen gia, co the mentor nguoi khac");
    }

    @Test
    void addCriterion_pushingTotalOver100Percent_isRejected() throws Exception {
        String token = registerAndLoginHr("hr-over");
        createCompany(token, "Cong ty Vuot Tong");
        String jobId = createJob(token, "Data Engineer");

        addCriterion(token, jobId, """
                {"name":"Tieu chi 1","weight":60}
                """);

        MvcResult result = addCriterion(token, jobId, """
                {"name":"Tieu chi 2","weight":50}
                """);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("RUBRIC_WEIGHT_EXCEEDED");
    }

    @Test
    void updateCriterion_excludesOwnOldWeight_soReSavingSameWeightSucceeds() throws Exception {
        String token = registerAndLoginHr("hr-update-self");
        createCompany(token, "Cong ty Sua Tieu Chi");
        String jobId = createJob(token, "QA Engineer");

        MvcResult created = addCriterion(token, jobId, """
                {"name":"Tieu chi A","weight":70}
                """);
        String criterionId = extractJsonField(created.getResponse().getContentAsString(), "id");

        MvcResult updated = mockMvc
                .perform(
                        put("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Tieu chi A","weight":70,"description":"cap nhat"}
                                        """))
                .andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void changeJobStatusToOpen_withRubricTotalUnder100Percent_isBlocked() throws Exception {
        String token = registerAndLoginHr("hr-under");
        createCompany(token, "Cong ty Thieu Tong");
        String jobId = createJob(token, "DevOps Engineer");

        addCriterion(token, jobId, """
                {"name":"Tieu chi A","weight":95}
                """);

        MvcResult result = mockMvc
                .perform(
                        patch("/api/hr/jobs/" + jobId + "/status")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"status":"OPEN"}
                                        """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RUBRIC_INCOMPLETE");
    }

    @Test
    void lockedRubric_rejectsCriterionMutation() throws Exception {
        String token = registerAndLoginHr("hr-locked");
        createCompany(token, "Cong ty Khoa Rubric");
        String jobId = createJob(token, "ML Engineer");

        MvcResult created = addCriterion(token, jobId, """
                {"name":"Tieu chi Khoa","weight":50}
                """);
        String criterionId = extractJsonField(created.getResponse().getContentAsString(), "id");

        Rubric rubric = rubricRepository.findByJobId(UUID.fromString(jobId)).orElseThrow();
        rubric.setLocked(true);
        rubricRepository.save(rubric);

        MvcResult updateResult = mockMvc
                .perform(
                        put("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Tieu chi Khoa","weight":80}
                                        """))
                .andReturn();
        assertThat(updateResult.getResponse().getStatus()).isEqualTo(409);
        assertThat(updateResult.getResponse().getContentAsString()).contains("RUBRIC_LOCKED");

        MvcResult addResult = addCriterion(token, jobId, """
                {"name":"Tieu chi Moi","weight":10}
                """);
        assertThat(addResult.getResponse().getStatus()).isEqualTo(409);

        MvcResult deleteResult = mockMvc
                .perform(
                        delete("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                                .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(deleteResult.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void addCriterion_withDuplicateNameInSameRubric_returnsConflict() throws Exception {
        String token = registerAndLoginHr("hr-dup-name");
        createCompany(token, "Cong ty Trung Ten");
        String jobId = createJob(token, "Frontend Engineer");

        addCriterion(token, jobId, """
                {"name":"Kinh nghiem","weight":40}
                """);

        MvcResult result = addCriterion(token, jobId, """
                {"name":"Kinh nghiem","weight":30}
                """);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RUBRIC_CRITERION_DUPLICATE_NAME");
    }

    @Test
    void updateCriterion_renamingToExistingSiblingName_returnsConflict() throws Exception {
        String token = registerAndLoginHr("hr-dup-rename");
        createCompany(token, "Cong ty Doi Ten Trung");
        String jobId = createJob(token, "Mobile Engineer");

        addCriterion(token, jobId, """
                {"name":"Tieu chi 1","weight":30}
                """);
        MvcResult created = addCriterion(token, jobId, """
                {"name":"Tieu chi 2","weight":20}
                """);
        String criterionId = extractJsonField(created.getResponse().getContentAsString(), "id");

        MvcResult result = mockMvc
                .perform(
                        put("/api/hr/jobs/" + jobId + "/rubric/criteria/" + criterionId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Tieu chi 1","weight":20}
                                        """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("RUBRIC_CRITERION_DUPLICATE_NAME");
    }

    @Test
    void hrA_addCriterionToHrBJob_returnsForbidden() throws Exception {
        String tokenA = registerAndLoginHr("hr-rubric-a");
        createCompany(tokenA, "Cong ty Rubric A");
        String jobIdA = createJob(tokenA, "Job Cua A");

        String tokenB = registerAndLoginHr("hr-rubric-b");
        createCompany(tokenB, "Cong ty Rubric B");

        MvcResult result = mockMvc
                .perform(
                        post("/api/hr/jobs/" + jobIdA + "/rubric/criteria")
                                .header("Authorization", "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"name":"Tieu chi cua B","weight":50}
                                        """))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void getRubric_returnsCriteriaAndTotalWeight() throws Exception {
        String token = registerAndLoginHr("hr-get");
        createCompany(token, "Cong ty Xem Rubric");
        String jobId = createJob(token, "Product Manager");

        addCriterion(token, jobId, """
                {"name":"Tieu chi 1","weight":30}
                """);
        addCriterion(token, jobId, """
                {"name":"Tieu chi 2","weight":25}
                """);

        MvcResult result = mockMvc
                .perform(get("/api/hr/jobs/" + jobId + "/rubric").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertThat(extractJsonDecimal(body, "totalWeight")).isEqualByComparingTo("55");
        assertThat(body).contains("Tieu chi 1");
        assertThat(body).contains("Tieu chi 2");
    }
}
