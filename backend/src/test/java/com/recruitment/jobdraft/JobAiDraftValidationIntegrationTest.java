package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

// FR-H11 dot 4 - J1: T5/T7/T8 phan HTTP (output hong -> thu lai 1 lan -> o FAILED AI_INVALID_OUTPUT, loi parse enum),
// T9 (dau vao R-N2/R-N3), T10 phan J1 (quyen), T11 phan J1 (body hong - pham vi advice, khoa cam trong response).
class JobAiDraftValidationIntegrationTest extends JobDraftIntegrationTestSupport {

    private static final String DESCRIPTION = "Cần tuyển lập trình viên Java tại Đà Nẵng, lương 15 - 20 triệu.";
    private static final String INVALID_OUTPUT_MESSAGE = "AI trả về kết quả không hợp lệ, vui lòng thử lại.";
    // Muc 4.4 - so nguyen ten khoa, moi cap.
    private static final List<String> FORBIDDEN_KEYS = List.of(
            "weight", "weights", "score", "maxScore", "totalScore", "rank", "priority", "model", "promptVersion",
            "prompt", "required", "expectedAnswer", "verdict", "label", "isQualified", "passed", "recommendation");

    // ---- T5 - phan JOB qua HTTP ----

    @Test
    void job_invalidFirstThenValid_succeedsWithTwoCalls() throws Exception {
        String token = hrWithCompany("h11-t5-retry");
        Map<String, Object> invalid = fullJob();
        invalid.put("salaryMin", 20000000);
        invalid.put("salaryMax", 15000000);
        String valid = jobJson(fullJob());
        stubParts(Map.of(JobDraftPart.JOB, call -> chatResponse(call == 1 ? jobJson(invalid) : valid)));

        JsonNode cell = jobCell(token);

        assertThat(cell.get("status").asString()).isEqualTo("SUCCEEDED");
        assertThat(callsOf(JobDraftPart.JOB)).isEqualTo(2);
    }

    @Test
    void job_invalidTwice_failedInvalidOutput_twoCalls() throws Exception {
        String token = hrWithCompany("h11-t5-fail");
        Map<String, Object> invalid = fullJob();
        invalid.put("title", "a".repeat(201));
        stubParts(Map.of(JobDraftPart.JOB, always(jobJson(invalid))));

        JsonNode cell = jobCell(token);

        assertFailed(cell, "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        assertThat(callsOf(JobDraftPart.JOB)).isEqualTo(2);
    }

    @Test
    void job_unknownWorkMode_isParseFailure() throws Exception {
        String token = hrWithCompany("h11-t5-enum");
        Map<String, Object> invalid = fullJob();
        invalid.put("workMode", "SPACE");
        stubParts(Map.of(JobDraftPart.JOB, always(jobJson(invalid))));

        assertFailed(jobCell(token), "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        assertThat(callsOf(JobDraftPart.JOB)).isEqualTo(2);
    }

    // ---- T7 - phan CRITERIA qua HTTP ----

    @Test
    void criteria_sixSucceed_sevenFail_zeroSucceedEmpty() throws Exception {
        String token = hrWithCompany("h11-t7");
        assertCriteria(token, criteriaJson(criteria(6)), "SUCCEEDED", 6);
        assertCriteria(token, criteriaJson(criteria(7)), "FAILED", -1);
        assertCriteria(token, criteriaJson(List.of()), "SUCCEEDED", 0);
        assertCriteria(token, criteriaJson(List.of(criterion("Java", "a"), criterion(" JAVA ", "b"))), "FAILED", -1);
    }

    private void assertCriteria(String token, String json, String status, int size) throws Exception {
        resetChatModelMock();
        stubParts(Map.of(JobDraftPart.CRITERIA, always(json)));
        MvcResult result = postGenerate(token, DESCRIPTION, JobDraftPart.CRITERIA);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode cell = body(result).get("criteria");
        assertThat(cell.get("status").asString()).as(json).isEqualTo(status);
        if (size >= 0) {
            assertThat(cell.get("data").get("criteria")).hasSize(size);
        } else {
            assertThat(callsOf(JobDraftPart.CRITERIA)).as("thu lai 1 lan").isEqualTo(2);
        }
    }

    private static List<Map<String, Object>> criteria(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> criterion("Tiêu chí " + i, "Mô tả " + i)).toList();
    }

    // ---- T8 - phan QUESTIONS qua HTTP ----

    @Test
    void questions_fiveSucceed_sixFail_unknownTypeFail_noRequiredKey() throws Exception {
        String token = hrWithCompany("h11-t8");
        List<Map<String, Object>> six = IntStream.rangeClosed(1, 6)
                .mapToObj(i -> question("YES_NO", "Câu hỏi số " + i + "?", List.of()))
                .toList();

        MvcResult five = assertQuestions(token, questionsJson(six.subList(0, 5)), "SUCCEEDED");
        assertThat(keysAnywhere(body(five))).doesNotContain("required");
        assertQuestions(token, questionsJson(six), "FAILED");
        assertQuestions(token, questionsJson(List.of(question("RATING", "Đánh giá bản thân?", List.of()))), "FAILED");
        assertQuestions(token, questionsJson(List.of(question("SINGLE_CHOICE", "Chọn một?", List.of("Có")))), "FAILED");
    }

    private MvcResult assertQuestions(String token, String json, String status) throws Exception {
        resetChatModelMock();
        stubParts(Map.of(JobDraftPart.QUESTIONS, always(json)));
        MvcResult result = postGenerate(token, DESCRIPTION, JobDraftPart.QUESTIONS);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("questions").get("status").asString()).as(json).isEqualTo(status);
        if ("FAILED".equals(status)) {
            assertThat(callsOf(JobDraftPart.QUESTIONS)).as("thu lai 1 lan").isEqualTo(2);
        }
        return result;
    }

    // ---- T9 - dau vao J1 ----

    @Test
    void description_nullEmptyBlank_rejected_withoutCallingAi() throws Exception {
        String token = hrWithCompany("h11-t9-empty");
        for (String description : Arrays.asList(null, "", "   \n\t")) {
            assertError(postGenerate(token, generateJson(description, List.of("JOB"))), 400,
                    "INVALID_JOB_DRAFT_REQUEST", "Vui lòng nhập mô tả vị trí cần tuyển.");
        }
        verifyNoInteractions(chatModel);
    }

    @Test
    void description_lengthBoundaries_countedAfterCrlfNormalization() throws Exception {
        String token = hrWithCompany("h11-t9-len");
        stubAllValid();
        assertThat(postGenerate(token, "a".repeat(2999), JobDraftPart.CRITERIA).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(postGenerate(token, "a".repeat(3000), JobDraftPart.CRITERIA).getResponse().getStatus())
                .isEqualTo(200);
        // 2999 + CRLF = 3001 don vi tho, 3000 sau khi doi -> hop le.
        assertThat(postGenerate(token, "a".repeat(2999) + "\r\n", JobDraftPart.CRITERIA).getResponse().getStatus())
                .isEqualTo(200);
        int callsBefore = callsOf(JobDraftPart.CRITERIA);
        assertError(postGenerate(token, "a".repeat(3001), JobDraftPart.CRITERIA), 400, "INVALID_JOB_DRAFT_REQUEST",
                "Mô tả tối đa 3000 ký tự.");
        assertThat(callsOf(JobDraftPart.CRITERIA)).as("400 khong goi AI").isEqualTo(callsBefore);
    }

    @Test
    void parts_missingNullEmptyDuplicateNullElementUnknown_rejected_withoutCallingAi() throws Exception {
        String token = hrWithCompany("h11-t9-parts");
        String noParts = "Vui lòng chọn ít nhất một phần cần tạo.";
        String invalidParts = "Danh sách phần cần tạo không hợp lệ hoặc bị trùng.";

        assertError(postGenerate(token, "{\"description\":\"" + DESCRIPTION + "\"}"), 400,
                "INVALID_JOB_DRAFT_REQUEST", noParts);
        assertError(postGenerate(token, generateJson(DESCRIPTION, null)), 400, "INVALID_JOB_DRAFT_REQUEST", noParts);
        assertError(postGenerate(token, generateJson(DESCRIPTION, List.of())), 400, "INVALID_JOB_DRAFT_REQUEST",
                noParts);
        assertError(postGenerate(token, generateJson(DESCRIPTION, List.of("JOB", "JOB"))), 400,
                "INVALID_JOB_DRAFT_REQUEST", invalidParts);
        List<String> withNull = new ArrayList<>(List.of("JOB"));
        withNull.add(null);
        assertError(postGenerate(token, generateJson(DESCRIPTION, withNull)), 400, "INVALID_JOB_DRAFT_REQUEST",
                invalidParts);
        assertError(postGenerate(token, generateJson(DESCRIPTION, List.of("FOO"))), 400,
                "INVALID_JOB_DRAFT_REQUEST", "Yêu cầu tạo tin bằng AI không hợp lệ.");
        verifyNoInteractions(chatModel);
    }

    // ---- T10 phan J1 - quyen ----

    @Test
    void candidateForbidden_noTokenUnauthenticated_hrWithoutCompanyNotFound_noAiCall() throws Exception {
        String body = generateJson(DESCRIPTION, List.of("JOB", "CRITERIA", "QUESTIONS"));
        String candidateToken = register("candidate", uniqueEmail("h11-t10-uv"));
        assertThat(postGenerate(candidateToken, body).getResponse().getStatus()).isEqualTo(403);

        assertError(postGenerate(null, body), 401, "UNAUTHENTICATED", null);

        String hrWithoutCompany = register("hr", uniqueEmail("h11-t10-nocomp"));
        Map<String, Long> before = rowCounts();
        assertError(postGenerate(hrWithoutCompany, body), 404, "COMPANY_NOT_FOUND", "HR chưa tạo hồ sơ công ty");
        assertThat(rowCounts()).isEqualTo(before);
        verifyNoInteractions(chatModel);
    }

    // ---- T11 phan J1 - body hong, pham vi advice, khoa cam ----

    @Test
    void brokenJson_returnsInvalidJobDraftRequest() throws Exception {
        String token = hrWithCompany("h11-t11-bad");
        assertError(postGenerate(token, "{bad"), 400, "INVALID_JOB_DRAFT_REQUEST",
                "Yêu cầu tạo tin bằng AI không hợp lệ.");
        assertThat(rawBody(postGenerate(token, "{bad"))).doesNotContain("Exception").doesNotContain("bad");
        verifyNoInteractions(chatModel);
    }

    // Hoi quy pham vi advice: POST /api/hr/jobs giu 400 mac dinh, khong INVALID_JOB_DRAFT_REQUEST.
    @Test
    void brokenJsonToManualJobCreate_keepsDefault400() throws Exception {
        String token = hrWithCompany("h11-t11-jobs");
        MvcResult result = mockMvc.perform(post("/api/hr/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(rawBody(result)).doesNotContain("INVALID_JOB_DRAFT_REQUEST");
    }

    @Test
    void successResponse_hasNoForbiddenKeysAtAnyLevel() throws Exception {
        String token = hrWithCompany("h11-t11-keys");
        Map<String, Object> job = fullJob();
        job.put("weight", 50);
        job.put("model", "gia-mao");
        Map<String, Object> crit = new LinkedHashMap<>(criterion("Java", "Số năm"));
        crit.put("weight", 40);
        crit.put("priority", 1);
        Map<String, Object> q = new LinkedHashMap<>(question("YES_NO", "Đi làm Đà Nẵng?", List.of()));
        q.put("required", true);
        q.put("expectedAnswer", "Có");
        stubParts(Map.of(
                JobDraftPart.JOB, always(jobJson(job)),
                JobDraftPart.CRITERIA, always(criteriaJson(List.of(crit))),
                JobDraftPart.QUESTIONS, always(questionsJson(List.of(q)))));

        MvcResult result = postGenerate(token, DESCRIPTION, JobDraftPart.JOB, JobDraftPart.CRITERIA,
                JobDraftPart.QUESTIONS);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        List<String> keys = keysAnywhere(body(result));
        for (String forbidden : FORBIDDEN_KEYS) {
            assertThat(keys).as(forbidden).doesNotContain(forbidden);
        }
        assertThat(rawBody(result)).doesNotContain("gia-mao");
    }

    // ---- tien ich ----

    private JsonNode jobCell(String token) throws Exception {
        MvcResult result = postGenerate(token, DESCRIPTION, JobDraftPart.JOB);
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(200);
        return body(result).get("job");
    }

    static void assertFailed(JsonNode cell, String error, String message) {
        assertThat(cell.get("status").asString()).isEqualTo("FAILED");
        assertThat(cell.get("error").asString()).isEqualTo(error);
        assertThat(cell.get("message").asString()).isEqualTo(message);
        assertThat(cell.get("data").isNull()).isTrue();
        assertThat(cell.get("generationToken").isNull()).isTrue();
    }

    private static List<String> keysAnywhere(JsonNode node) {
        return JobAiDraftGenerateIntegrationTest.keysAnywhere(node);
    }
}
