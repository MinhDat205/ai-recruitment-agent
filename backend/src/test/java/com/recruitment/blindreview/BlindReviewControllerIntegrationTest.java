package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// FR-H16 T4 - B1 PATCH /api/hr/jobs/{id}/blind-review (R-B1..R-B4) + GET/PUT /api/hr/jobs/{id}. @Transactional: moi @Test
// rollback rieng. HR dang ky + dang nhap qua API (can JWT that); cong ty, tin dung bang repository (khuon
// TalentPoolOwnerControllerIntegrationTest).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BlindReviewControllerIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MSG_INVALID = "Yêu cầu bật/tắt chế độ ẩn danh không hợp lệ.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    private record Hr(String token, UUID userId, UUID companyId) {
    }

    private record Change(boolean enabled, UUID changedBy) {
    }

    // ---- Du lieu ----

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String register(String role, String email) throws Exception {
        String extra = role.equals("hr") ? ",\"phone\":\"0900000000\"" : "";
        mockMvc
                .perform(post("/api/auth/register/" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"Nguoi Dung\"%s}"
                                .formatted(email, extra)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc
                .perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return body(login).get("accessToken").asString();
    }

    private Hr hr() throws Exception {
        String email = uniqueEmail("h16b-hr");
        String token = register("hr", email);
        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        Company company = new Company();
        company.setOwnerId(userId);
        company.setName("Cong ty " + UUID.randomUUID());
        return new Hr(token, userId, companyRepository.saveAndFlush(company).getId());
    }

    private UUID job(Hr hr, JobStatus status) {
        Job job = new Job();
        job.setCompanyId(hr.companyId());
        job.setCreatedBy(hr.userId());
        job.setTitle("Tin H16");
        job.setDescription("Mo ta cong viec");
        job.setStatus(status);
        job.setRecruitmentCycle(1);
        return jobRepository.saveAndFlush(job).getId();
    }

    private UUID job(Hr hr) {
        return job(hr, JobStatus.OPEN);
    }

    private List<Change> changes(UUID jobId) {
        return jdbcTemplate.query(
                "SELECT enabled, changed_by FROM job_blind_review_changes WHERE job_id = ? ORDER BY changed_at, id",
                (rs, i) -> new Change(rs.getBoolean("enabled"), rs.getObject("changed_by", UUID.class)),
                jobId);
    }

    // Test @Transactional dung chung persistence context voi request: flush de JDBC thay INSERT/UPDATE chua ghi.
    private boolean columnValue(UUID jobId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT blind_review_enabled FROM jobs WHERE id = ?", Boolean.class, jobId);
    }

    // ---- HTTP ----

    private MvcResult call(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult toggle(String token, UUID jobId, String json) throws Exception {
        return call(patch("/api/hr/jobs/" + jobId + "/blind-review"), token, json);
    }

    private MvcResult toggle(String token, UUID jobId, boolean enabled) throws Exception {
        return toggle(token, jobId, "{\"enabled\":" + enabled + "}");
    }

    private static void assertInvalidRequest(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode error = body(result);
        assertThat(error.get("error").asString()).isEqualTo("INVALID_BLIND_REVIEW_REQUEST");
        assertThat(error.get("message").asString()).isEqualTo(MSG_INVALID);
    }

    // ---- B1 thanh cong ----

    @Test
    void enableReturnsStateAndRecordsOneChange() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        MvcResult result = toggle(hr.token(), jobId, true);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode json = body(result);
        assertThat(json.get("jobId").asString()).isEqualTo(jobId.toString());
        assertThat(json.get("enabled").asBoolean()).isTrue();
        assertThat(json.get("changedAt").isNull()).isFalse();
        assertThat(columnValue(jobId)).isTrue();
        assertThat(changes(jobId)).containsExactly(new Change(true, hr.userId()));
    }

    @Test
    void sendingSameValueAgainDoesNotRecordChange() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);
        String firstChangedAt = body(toggle(hr.token(), jobId, true)).get("changedAt").asString();

        MvcResult again = toggle(hr.token(), jobId, true);

        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(again).get("enabled").asBoolean()).isTrue();
        assertThat(body(again).get("changedAt").asString()).isEqualTo(firstChangedAt);
        assertThat(changes(jobId)).hasSize(1);
    }

    @Test
    void disablingWhenNeverEnabledRecordsNothingAndChangedAtIsNull() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        MvcResult result = toggle(hr.token(), jobId, false);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("enabled").asBoolean()).isFalse();
        assertThat(body(result).get("changedAt").isNull()).isTrue();
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void disableThenReEnableRecordsEachChange() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        toggle(hr.token(), jobId, true);
        MvcResult off = toggle(hr.token(), jobId, false);
        MvcResult on = toggle(hr.token(), jobId, true);

        assertThat(off.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(off).get("enabled").asBoolean()).isFalse();
        assertThat(on.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(on).get("enabled").asBoolean()).isTrue();
        assertThat(columnValue(jobId)).isTrue();
        assertThat(changes(jobId))
                .containsExactly(
                        new Change(true, hr.userId()), new Change(false, hr.userId()), new Change(true, hr.userId()));
    }

    @Test
    void everyJobStatusCanBeToggled() throws Exception {
        Hr hr = hr();
        for (JobStatus jobStatus : JobStatus.values()) {
            UUID jobId = job(hr, jobStatus);
            assertThat(toggle(hr.token(), jobId, true).getResponse().getStatus()).as("%s", jobStatus).isEqualTo(200);
            assertThat(columnValue(jobId)).as("%s", jobStatus).isTrue();
        }
    }

    // ---- B1 quyen va ton tai ----

    @Test
    void jobOfAnotherCompanyIsForbidden() throws Exception {
        Hr owner = hr();
        Hr other = hr();
        UUID jobId = job(owner);

        MvcResult result = toggle(other.token(), jobId, true);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(columnValue(jobId)).isFalse();
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        Hr hr = hr();

        MvcResult result = toggle(hr.token(), UUID.randomUUID(), true);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("JOB_NOT_FOUND");
    }

    @Test
    void softDeletedJobIsNotFound() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);
        jdbcTemplate.update("UPDATE jobs SET deleted_at = now() WHERE id = ?", jobId);
        // Bo ban Job da nap (deleted_at = null) khoi persistence context dung chung cua test: request that luon doc moi.
        entityManager.clear();

        MvcResult result = toggle(hr.token(), jobId, true);

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(result).get("error").asString()).isEqualTo("JOB_NOT_FOUND");
        assertThat(columnValue(jobId)).isFalse();
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void candidateTokenIsForbidden() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);
        String candidateToken = register("candidate", uniqueEmail("h16b-uv"));

        assertThat(toggle(candidateToken, jobId, true).getResponse().getStatus()).isEqualTo(403);
        assertThat(columnValue(jobId)).isFalse();
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        assertThat(toggle(null, jobId, true).getResponse().getStatus()).isEqualTo(401);
        assertThat(columnValue(jobId)).isFalse();
    }

    // ---- B1 body sai ----

    @Test
    void missingBodyIsInvalid() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        MvcResult result = call(
                patch("/api/hr/jobs/" + jobId + "/blind-review").contentType(MediaType.APPLICATION_JSON),
                hr.token(),
                null);

        assertInvalidRequest(result);
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void missingOrNullEnabledIsInvalid() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        assertInvalidRequest(toggle(hr.token(), jobId, "{}"));
        assertInvalidRequest(toggle(hr.token(), jobId, "{\"enabled\":null}"));
        assertThat(columnValue(jobId)).isFalse();
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void nonBooleanEnabledIsInvalidAndNotEchoed() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        MvcResult result = toggle(hr.token(), jobId, "{\"enabled\":\"abcXYZ-echo\"}");

        assertInvalidRequest(result);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("abcXYZ-echo");
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void malformedJsonIsInvalidAndNotEchoed() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        MvcResult result = toggle(hr.token(), jobId, "{\"enabled\": tru-echo-me");

        assertInvalidRequest(result);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("echo-me");
        assertThat(changes(jobId)).isEmpty();
    }

    // ---- GET/PUT /api/hr/jobs/{id} (R-B1, R-B2) ----

    @Test
    void getJobExposesBlindReviewEnabled() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr);

        JsonNode before = body(call(get("/api/hr/jobs/" + jobId), hr.token(), null));
        toggle(hr.token(), jobId, true);
        JsonNode after = body(call(get("/api/hr/jobs/" + jobId), hr.token(), null));

        assertThat(before.get("blindReviewEnabled").asBoolean()).isFalse();
        assertThat(after.get("blindReviewEnabled").asBoolean()).isTrue();
    }

    @Test
    void putJobDoesNotResetBlindReview() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, JobStatus.DRAFT);
        toggle(hr.token(), jobId, true);

        MvcResult result = call(
                put("/api/hr/jobs/" + jobId),
                hr.token(),
                JSON.writeValueAsString(Map.of("title", "Tin H16 sua", "description", "Mo ta moi")));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("title").asString()).isEqualTo("Tin H16 sua");
        assertThat(body(result).get("blindReviewEnabled").asBoolean()).isTrue();
        assertThat(columnValue(jobId)).isTrue();
        assertThat(changes(jobId)).hasSize(1);
    }

    @Test
    void putJobCannotEnableBlindReviewThroughBody() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, JobStatus.DRAFT);

        MvcResult result = call(
                put("/api/hr/jobs/" + jobId),
                hr.token(),
                JSON.writeValueAsString(
                        Map.of("title", "Tin H16", "description", "Mo ta", "blindReviewEnabled", true)));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(columnValue(jobId)).isFalse();
        assertThat(changes(jobId)).isEmpty();
    }

    @Test
    void createdJobDefaultsToDisabled() throws Exception {
        Hr hr = hr();

        MvcResult created = call(
                post("/api/hr/jobs"),
                hr.token(),
                """
                {"job": {"title":"Tin H16 moi","description":"Mo ta cong viec"},
                 "interviewTemplate": {"subject":"Thu moi","body":"Kinh chao ung vien.","senderName":"Phong Nhan Su"}}
                """);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(created).get("blindReviewEnabled").asBoolean()).isFalse();
        assertThat(columnValue(UUID.fromString(body(created).get("id").asString()))).isFalse();
    }
}
