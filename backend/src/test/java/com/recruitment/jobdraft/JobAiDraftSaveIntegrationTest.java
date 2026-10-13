package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.recruitment.aigeneration.AiGenerationStamp;
import com.recruitment.aigeneration.AiGenerationTokenCodec;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-H11 dot 5 - J2 duong luu: T15 (nguyen khoi + K4 tu token), T15b (L2 - 0 tieu chi + cau hoi), T16 (trong so R-W).
// Moi ca doc lai tu DB bang JDBC (JobAiGeneration.createdAt null ngay sau save - khong dung entity vua luu).
class JobAiDraftSaveIntegrationTest extends JobDraftIntegrationTestSupport {

    private static final String DESCRIPTION = "Phát triển API với Spring Boot.";

    @Autowired
    AiGenerationTokenCodec tokenCodec;

    private static AiGenerationStamp stamp(String promptVersion, String generatedAt) {
        return new AiGenerationStamp("claude-sonnet-4-6", promptVersion, Instant.parse(generatedAt));
    }

    private UUID jobIdOf(MvcResult result) throws Exception {
        return UUID.fromString(body(result).get("id").asString());
    }

    // ---- T15 ----

    @Test
    void fullSave_createsDraftJobRubricQuestionsTemplateAndK4RowsFromTokens() throws Exception {
        String email = uniqueEmail("h11-t15");
        String token = register("hr", email);
        createCompany(token);
        UUID userId = userIdOf(email);
        AiGenerationStamp jobStamp = stamp("job-draft-job-v1", "2026-10-13T03:00:00.123Z");
        AiGenerationStamp criteriaStamp = stamp("job-draft-criteria-v1", "2026-10-13T03:01:00.456Z");
        AiGenerationStamp questionsStamp = stamp("job-draft-questions-v1", "2026-10-13T03:02:00.789Z");

        Map<String, Object> body = saveBody();
        body.put("benefits", "  Lương tháng 13  ");
        body.put("criteria", List.of(
                criterionInput("  Kinh nghiệm Java  ", "  Số năm và dự án Java  ", 30),
                criterionInput("PostgreSQL", "Thiết kế và truy vấn", 30)));
        body.put("screeningQuestions", List.of(
                questionInput("YES_NO", "Bạn có thể đi làm tại Đà Nẵng không?", List.of(), false),
                questionInput("SINGLE_CHOICE", "Số năm kinh nghiệm Java?", List.of("Dưới 2 năm", "Từ 2 năm"), true),
                questionInput("SHORT_TEXT", "Khi nào bạn có thể bắt đầu?", null, null)));
        body.put("jobToken", tokenCodec.issue(userId, JobDraftPart.JOB.tokenPurpose(), jobStamp));
        body.put("criteriaToken", tokenCodec.issue(userId, JobDraftPart.CRITERIA.tokenPurpose(), criteriaStamp));
        body.put("questionsToken", tokenCodec.issue(userId, JobDraftPart.QUESTIONS.tokenPurpose(), questionsStamp));
        // R-L3 - khoa trang thai trong JSON bi bo qua.
        body.put("status", "OPEN");

        MvcResult result = postSave(token, body);

        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(201);
        JsonNode response = body(result);
        UUID jobId = jobIdOf(result);
        assertThat(response.get("status").asString()).isEqualTo("DRAFT");
        assertThat(response.get("rubricId").asString()).isNotBlank();
        assertThat(response.get("interviewTemplateId").asString()).isNotBlank();
        // R-P7 - ghep xac dinh o backend.
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM jobs WHERE id = ?", String.class, jobId))
                .isEqualTo(DESCRIPTION + "\n\nQuyền lợi:\nLương tháng 13");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId))
                .isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("SELECT category_code FROM jobs WHERE id = ?", String.class, jobId))
                .isEqualTo("IT_SOFTWARE");

        // R-L6, L7 - tieu chi dung thu tu, ten/mo ta da strip, trong so dung.
        List<Map<String, Object>> criteria = jdbcTemplate.queryForList(
                "SELECT c.name, c.description, c.weight, c.display_order FROM rubric_criteria c "
                        + "JOIN rubrics r ON r.id = c.rubric_id WHERE r.job_id = ? ORDER BY c.display_order", jobId);
        assertThat(criteria).hasSize(2);
        assertThat(criteria.get(0).get("name")).isEqualTo("Kinh nghiệm Java");
        assertThat(criteria.get(0).get("description")).isEqualTo("Số năm và dự án Java");
        assertThat((BigDecimal) criteria.get(0).get("weight")).isEqualByComparingTo("30");
        assertThat(criteria.get(0).get("display_order")).isEqualTo(0);
        assertThat(criteria.get(1).get("name")).isEqualTo("PostgreSQL");
        assertThat(criteria.get(1).get("display_order")).isEqualTo(1);

        // FR-H10 tu danh display_order 1..3, required dung request, revision = so cau.
        List<Map<String, Object>> questions = jdbcTemplate.queryForList(
                "SELECT question_type, question_text, is_required, display_order FROM job_screening_questions "
                        + "WHERE job_id = ? AND deleted_at IS NULL ORDER BY display_order", jobId);
        assertThat(questions).extracting(q -> q.get("question_type"))
                .containsExactly("YES_NO", "SINGLE_CHOICE", "SHORT_TEXT");
        assertThat(questions).extracting(q -> q.get("is_required")).containsExactly(false, true, false);
        assertThat(questions).extracting(q -> q.get("display_order")).containsExactly(1, 2, 3);
        assertThat(revisionOf(jobId)).isEqualTo(3);

        Map<String, Object> template = jdbcTemplate.queryForMap(
                "SELECT subject, body, sender_name FROM interview_templates WHERE job_id = ?", jobId);
        assertThat(template.get("subject")).isEqualTo("Thư mời phỏng vấn");
        assertThat(template.get("body")).isEqualTo("Kính mời bạn tham dự phỏng vấn.");
        assertThat(template.get("sender_name")).isEqualTo("Phòng Nhân sự");

        // R-K4-2 - gia tri K4 bang dung gia tri trong token; doc lai tu DB (created_at do DB dien).
        List<Map<String, Object>> k4 = jdbcTemplate.queryForList(
                "SELECT part, ai_model, ai_prompt_version, ai_generated_at, created_at FROM job_ai_generations "
                        + "WHERE job_id = ? ORDER BY part", jobId);
        assertThat(k4).extracting(r -> r.get("part")).containsExactly("CRITERIA", "JOB", "QUESTIONS");
        assertK4Row(k4.get(0), criteriaStamp);
        assertK4Row(k4.get(1), jobStamp);
        assertK4Row(k4.get(2), questionsStamp);
        verifyNoInteractions(chatModel);
    }

    private static void assertK4Row(Map<String, Object> row, AiGenerationStamp expected) {
        assertThat(row.get("ai_model")).isEqualTo(expected.model());
        assertThat(row.get("ai_prompt_version")).isEqualTo(expected.promptVersion());
        assertThat(((Timestamp) row.get("ai_generated_at")).toInstant()).isEqualTo(expected.generatedAt());
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void blankBenefits_descriptionUnchanged_andNoTokens_noK4Rows() throws Exception {
        String token = hrWithCompany("h11-t15-ben");
        Map<String, Object> body = saveBody();
        body.put("benefits", "   ");

        MvcResult result = postSave(token, body);

        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(201);
        UUID jobId = jobIdOf(result);
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM jobs WHERE id = ?", String.class, jobId))
                .isEqualTo(DESCRIPTION);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM job_ai_generations WHERE job_id = ?", Long.class, jobId))
                .isZero();
    }

    // ---- T15b (L2) - 0 tieu chi + 2 cau hoi: INSERT Job phai chay truoc incrementRevision ----

    @Test
    void noCriteria_twoQuestions_allRowsWritten_revisionTwo() throws Exception {
        String token = hrWithCompany("h11-t15b");
        Map<String, Object> body = saveBody();
        body.put("screeningQuestions", List.of(
                questionInput("YES_NO", "Bạn có thể đi làm tại Đà Nẵng không?", List.of(), true),
                questionInput("SHORT_TEXT", "Khi nào bạn có thể bắt đầu?", List.of(), false)));

        MvcResult result = postSave(token, body);

        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(201);
        UUID jobId = jobIdOf(result);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM jobs WHERE id = ?", Long.class, jobId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM interview_templates WHERE job_id = ?", Long.class, jobId))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM job_screening_questions WHERE job_id = ?", Long.class, jobId))
                .isEqualTo(2);
        assertThat(revisionOf(jobId)).isEqualTo(2);
    }

    // ---- T16 - trong so (R-W) ----

    @Test
    void missingWeight_rejectedWithIndex_criterionNotDropped_nothingWritten() throws Exception {
        String token = hrWithCompany("h11-t16-null");
        Map<String, Object> body = saveBody();
        body.put("criteria", List.of(criterionInput("Java", "a", 30), criterionInput("PostgreSQL", "b", null)));
        Map<String, Long> before = rowCounts();

        assertError(postSave(token, body), 400, "INVALID_JOB_DRAFT_SAVE", "Tiêu chí 2: vui lòng nhập trọng số.");
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void weightBoundaries_perCriterion() throws Exception {
        String token = hrWithCompany("h11-t16-range");
        String outOfRange = "Tiêu chí 1: trọng số phải lớn hơn 0 và tối đa 100.";
        Map<String, Long> before = rowCounts();
        assertError(postSave(token, withCriteria(criterionInput("Java", null, 0))), 400, "INVALID_JOB_DRAFT_SAVE",
                outOfRange);
        assertError(postSave(token, withCriteria(criterionInput("Java", null, -5))), 400, "INVALID_JOB_DRAFT_SAVE",
                outOfRange);
        assertError(postSave(token, withCriteria(criterionInput("Java", null, new BigDecimal("100.01")))), 400,
                "INVALID_JOB_DRAFT_SAVE", outOfRange);
        assertThat(rowCounts()).as("moi ca 400 khong ghi gi").isEqualTo(before);

        assertThat(postSave(token, withCriteria(criterionInput("Java", null, new BigDecimal("0.01"))))
                        .getResponse().getStatus())
                .isEqualTo(201);
        assertThat(postSave(token, withCriteria(criterionInput("Java", null, 100))).getResponse().getStatus())
                .isEqualTo(201);
    }

    @Test
    void totalWeightBoundaries() throws Exception {
        String token = hrWithCompany("h11-t16-total");
        assertThat(postSave(token, withCriteria(criterionInput("A", null, 60), criterionInput("B", null, 40)))
                        .getResponse().getStatus())
                .isEqualTo(201);

        Map<String, Long> before = rowCounts();
        MvcResult over = postSave(token, withCriteria(
                criterionInput("A", null, 60), criterionInput("B", null, new BigDecimal("40.01"))));
        assertError(over, 400, "RUBRIC_WEIGHT_EXCEEDED", null);
        assertThat(rowCounts()).isEqualTo(before);
    }

    // R-W4 - tong < 100 van luu nhap; rang buoc du 100% khi mo tin khong doi (b5).
    @Test
    void totalBelow100_savedAsDraft_butOpeningStillRequiresFullRubric() throws Exception {
        String token = hrWithCompany("h11-t16-open");
        MvcResult result = postSave(token, withCriteria(criterionInput("A", null, 30), criterionInput("B", null, 30)));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        UUID jobId = jobIdOf(result);

        MvcResult open = mockMvc.perform(patch("/api/hr/jobs/" + jobId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andReturn();

        assertError(open, 409, "RUBRIC_INCOMPLETE", null);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM jobs WHERE id = ?", String.class, jobId))
                .isEqualTo("DRAFT");
    }

    @SafeVarargs
    private static Map<String, Object> withCriteria(Map<String, Object>... criteria) {
        Map<String, Object> body = saveBody();
        body.put("criteria", new ArrayList<>(List.of(criteria)));
        return body;
    }

    private Integer revisionOf(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, jobId);
    }
}
