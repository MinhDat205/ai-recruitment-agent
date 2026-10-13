package com.recruitment.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.aigeneration.AiGenerationStamp;
import com.recruitment.aigeneration.AiGenerationTokenCodec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MvcResult;

// FR-H11 dot 5 - J2 bi tu choi TRUOC transaction, khong ghi dong nao (dem 6 bang truoc/sau): T17 (cau hoi FR-H10, tieu
// chi trung/qua so luong, token K4 sai/het han/cua HR khac/sai o), T10 phan J2 (quyen), T11 phan J2 (body hong).
class JobAiDraftSaveRejectionIntegrationTest extends JobDraftIntegrationTestSupport {

    private static final String INVALID_TOKEN_MESSAGE =
            "Thông tin phiên tạo bằng AI không hợp lệ hoặc đã hết hạn. Vui lòng tạo lại gợi ý.";

    @Autowired
    AiGenerationTokenCodec tokenCodec;

    @Value("${app.jwt.secret}")
    String jwtSecret;

    private static final AiGenerationStamp STAMP =
            new AiGenerationStamp("claude-sonnet-4-6", "job-draft-job-v1", Instant.parse("2026-10-13T03:00:00Z"));

    private record Hr(String token, UUID userId) {
    }

    private Hr hr(String prefix) throws Exception {
        String email = uniqueEmail(prefix);
        String token = register("hr", email);
        createCompany(token);
        return new Hr(token, userIdOf(email));
    }

    // Moi ca: dung loi, dung cau, khong ghi dong nao.
    private void assertRejectedWithoutWrites(String token, Map<String, Object> body, int status, String code,
            String message) throws Exception {
        Map<String, Long> before = rowCounts();
        assertError(postSave(token, body), status, code, message);
        assertThat(rowCounts()).as("khong ghi gi").isEqualTo(before);
    }

    // ---- T17 - cau hoi (quy tac FR-H10 qua ScreeningQuestionRules) ----

    @Test
    void thirdQuestionBreaksFrH10Rule_rejectedWithIndexAndViolationMessage() throws Exception {
        Hr hr = hr("h11-t17-q3");
        Map<String, Object> body = saveBody();
        body.put("criteria", List.of(criterionInput("Java", null, 30)));
        body.put("screeningQuestions", List.of(
                questionInput("YES_NO", "Câu một?", List.of(), false),
                questionInput("YES_NO", "Câu hai?", List.of(), false),
                questionInput("SINGLE_CHOICE", "Câu ba?", List.of("Chỉ một"), false)));

        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE",
                "Câu hỏi 3: Câu hỏi một lựa chọn cần từ 2 đến 10 phương án.");
    }

    @Test
    void questionWithoutType_rejectedWithIndex() throws Exception {
        Hr hr = hr("h11-t17-qtype");
        Map<String, Object> body = saveBody();
        body.put("screeningQuestions", List.of(questionInput(null, "Câu một?", List.of(), false)));

        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE",
                "Câu hỏi 1: vui lòng chọn loại câu hỏi.");
    }

    @Test
    void elevenQuestions_rejected_tenAccepted() throws Exception {
        Hr hr = hr("h11-t17-q11");
        List<Map<String, Object>> eleven = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> questionInput("YES_NO", "Câu số " + i + "?", List.of(), false))
                .toList();
        Map<String, Object> body = saveBody();
        body.put("screeningQuestions", eleven);
        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE", "Tối đa 10 câu hỏi sàng lọc.");

        body.put("screeningQuestions", eleven.subList(0, 10));
        assertThat(postSave(hr.token(), body).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- T17 - tieu chi ----

    @Test
    void duplicateCriterionNameAfterStrip_rejected() throws Exception {
        Hr hr = hr("h11-t17-dup");
        Map<String, Object> body = saveBody();
        body.put("criteria", List.of(criterionInput("Java", null, 20), criterionInput("  Java ", null, 20)));

        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE",
                "Tiêu chí 2: tên tiêu chí bị trùng.");
    }

    @Test
    void criterionNameEmptyOrTooLong_rejectedWithIndex() throws Exception {
        Hr hr = hr("h11-t17-name");
        Map<String, Object> body = saveBody();
        body.put("criteria", List.of(criterionInput("   ", null, 20)));
        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE",
                "Tiêu chí 1: vui lòng nhập tên tiêu chí.");

        body.put("criteria", List.of(criterionInput("A", null, 10), criterionInput("a".repeat(201), null, 10)));
        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE",
                "Tiêu chí 2: tên tiêu chí tối đa 200 ký tự.");

        body.put("criteria", List.of(criterionInput("a".repeat(200), null, 10)));
        assertThat(postSave(hr.token(), body).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void elevenCriteria_rejected_tenAccepted() throws Exception {
        Hr hr = hr("h11-t17-c11");
        List<Map<String, Object>> eleven = new ArrayList<>(IntStream.rangeClosed(1, 11)
                .mapToObj(i -> criterionInput("Tiêu chí " + i, null, 5))
                .toList());
        Map<String, Object> body = saveBody();
        body.put("criteria", eleven);
        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_JOB_DRAFT_SAVE", "Tối đa 10 tiêu chí.");

        body.put("criteria", eleven.subList(0, 10));
        assertThat(postSave(hr.token(), body).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- T17 - token K4 (R-K4-4) ----

    @Test
    void tamperedToken_rejected_nothingWritten() throws Exception {
        Hr hr = hr("h11-t17-tamper");
        String token = tokenCodec.issue(hr.userId(), JobDraftPart.JOB.tokenPurpose(), STAMP);
        // Doi mot ky tu GIUA chu ky - ky tu cuoi chi mang bit dem (xem AiGenerationTokenCodecTest.verify_rejectsTamperedToken).
        int middle = token.lastIndexOf('.') + 20;
        char original = token.charAt(middle);
        Map<String, Object> body = saveBody();
        body.put("criteria", List.of(criterionInput("Java", null, 30)));
        body.put("jobToken", token.substring(0, middle) + (original == 'A' ? 'B' : 'A') + token.substring(middle + 1));

        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_AI_GENERATION_TOKEN", INVALID_TOKEN_MESSAGE);
    }

    @Test
    void tokenOfAnotherHr_rejected() throws Exception {
        Hr owner = hr("h11-t17-owner");
        Hr other = hr("h11-t17-other");
        Map<String, Object> body = saveBody();
        body.put("jobToken", tokenCodec.issue(owner.userId(), JobDraftPart.JOB.tokenPurpose(), STAMP));

        assertRejectedWithoutWrites(other.token(), body, 400, "INVALID_AI_GENERATION_TOKEN", INVALID_TOKEN_MESSAGE);
    }

    @Test
    void jobTokenInCriteriaSlot_rejected() throws Exception {
        Hr hr = hr("h11-t17-slot");
        Map<String, Object> body = saveBody();
        body.put("criteriaToken", tokenCodec.issue(hr.userId(), JobDraftPart.JOB.tokenPurpose(), STAMP));

        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_AI_GENERATION_TOKEN", INVALID_TOKEN_MESSAGE);
    }

    @Test
    void accessTokenInTokenSlot_rejected() throws Exception {
        Hr hr = hr("h11-t17-access");
        Map<String, Object> body = saveBody();
        body.put("questionsToken", hr.token());

        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_AI_GENERATION_TOKEN", INVALID_TOKEN_MESSAGE);
    }

    // Han 7 ngay: token ky luc (bay gio - 7 ngay - 1 s) da het han; ky luc (bay gio - 7 ngay + 60 s) con han. Bien dung
    // tung giay (7 ngay -1 s / dung 7 ngay / +1 s) da kiem bang Clock co dinh o AiGenerationTokenCodecTest - o day bien
    // con han lui 60 s de khong phu thuoc toc do chay test.
    @Test
    void expiredTokenRejected_stillValidTokenAccepted() throws Exception {
        Hr hr = hr("h11-t17-exp");
        Instant now = Instant.now();
        AiGenerationTokenCodec expiredSigner = new AiGenerationTokenCodec(
                jwtSecret, Clock.fixed(now.minus(Duration.ofDays(7)).minusSeconds(1), ZoneOffset.UTC));
        AiGenerationTokenCodec validSigner = new AiGenerationTokenCodec(
                jwtSecret, Clock.fixed(now.minus(Duration.ofDays(7)).plusSeconds(60), ZoneOffset.UTC));
        Map<String, Object> body = saveBody();
        body.put("jobToken", expiredSigner.issue(hr.userId(), JobDraftPart.JOB.tokenPurpose(), STAMP));
        assertRejectedWithoutWrites(hr.token(), body, 400, "INVALID_AI_GENERATION_TOKEN", INVALID_TOKEN_MESSAGE);

        body.put("jobToken", validSigner.issue(hr.userId(), JobDraftPart.JOB.tokenPurpose(), STAMP));
        MvcResult saved = postSave(hr.token(), body);
        assertThat(saved.getResponse().getStatus()).as(rawBody(saved)).isEqualTo(201);
    }

    @Test
    void allTokensNull_saved_noK4Rows() throws Exception {
        Hr hr = hr("h11-t17-null");
        long k4Before = jdbcTemplate.queryForObject("SELECT count(*) FROM job_ai_generations", Long.class);

        MvcResult result = postSave(hr.token(), saveBody());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM job_ai_generations", Long.class))
                .isEqualTo(k4Before);
    }

    // ---- T10 phan J2 - quyen ----

    @Test
    void candidateForbidden_noTokenUnauthenticated_hrWithoutCompanyNotFound_nothingWritten() throws Exception {
        String candidateToken = register("candidate", uniqueEmail("h11-t10s-uv"));
        Map<String, Long> before = rowCounts();
        assertThat(postSave(candidateToken, saveBody()).getResponse().getStatus()).isEqualTo(403);
        assertError(postSave(null, saveBody()), 401, "UNAUTHENTICATED", null);
        String noCompany = register("hr", uniqueEmail("h11-t10s-nocomp"));
        assertError(postSave(noCompany, saveBody()), 404, "COMPANY_NOT_FOUND", "HR chưa tạo hồ sơ công ty");
        assertThat(rowCounts()).isEqualTo(before);
    }

    // ---- T11 phan J2 - body hong / enum la -> advice cua FR-H11 ----

    @Test
    void brokenJsonOrUnknownQuestionType_invalidJobDraftRequest() throws Exception {
        Hr hr = hr("h11-t11s");
        Map<String, Long> before = rowCounts();
        assertError(postSave(hr.token(), "{bad"), 400, "INVALID_JOB_DRAFT_REQUEST",
                "Yêu cầu tạo tin bằng AI không hợp lệ.");
        Map<String, Object> body = saveBody();
        body.put("screeningQuestions", List.of(questionInput("FOO", "Câu một?", List.of(), false)));
        assertError(postSave(hr.token(), body), 400, "INVALID_JOB_DRAFT_REQUEST",
                "Yêu cầu tạo tin bằng AI không hợp lệ.");
        assertThat(rowCounts()).isEqualTo(before);
    }

    // R-L4 (3) - @Valid cua job/interviewTemplate nhu POST /api/hr/jobs: 400 dang map truong.
    @Test
    void missingTitle_returnsFieldMap() throws Exception {
        Hr hr = hr("h11-t17-valid");
        Map<String, Object> body = saveBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> job = (Map<String, Object>) body.get("job");
        job.put("title", "");
        Map<String, Long> before = rowCounts();

        MvcResult result = postSave(hr.token(), body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).has("job.title")).as(rawBody(result)).isTrue();
        assertThat(rowCounts()).isEqualTo(before);
    }
}
