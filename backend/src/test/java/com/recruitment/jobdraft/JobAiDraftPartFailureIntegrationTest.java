package com.recruitment.jobdraft;

import static com.recruitment.jobdraft.JobAiDraftValidationIntegrationTest.assertFailed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.RateLimitException;
import com.recruitment.jobdraft.dto.JobAiDraftRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

// FR-H11 dot 4 - T12 (loi theo phan, R-N6, R-K3H-5) qua HTTP va T22 phan transaction (R-K3H-3) tren bean facade that
// voi TransactionTemplate that. Exception khong phai cua K3 (500 ca request) kiem o JobAiDraftFacadeTest (khong Spring -
// can JobAiDraftService gia, khong dung @MockitoSpyBean de khong tach context rieng).
class JobAiDraftPartFailureIntegrationTest extends JobDraftIntegrationTestSupport {

    private static final String DESCRIPTION = "Cần tuyển lập trình viên Java tại Đà Nẵng, lương 15 - 20 triệu.";
    private static final String SECRET = "BI-MAT-KHONG-DUOC-LO-RA";

    @Autowired
    JobAiDraftFacade facade;

    @Autowired
    TransactionTemplate transactionTemplate;

    private MvcResult generateAllWithJob(String token, PartAnswer jobAnswer) throws Exception {
        stubParts(Map.of(
                JobDraftPart.JOB, jobAnswer,
                JobDraftPart.CRITERIA, always(criteriaJson(List.of(criterion("Kinh nghiệm Java", "Số năm")))),
                JobDraftPart.QUESTIONS, always(questionsJson(List.of(
                        question("YES_NO", "Bạn có thể đi làm tại Đà Nẵng không?", List.of()))))));
        MvcResult result = postGenerate(token, DESCRIPTION, JobDraftPart.JOB, JobDraftPart.CRITERIA,
                JobDraftPart.QUESTIONS);
        assertThat(result.getResponse().getStatus()).as(rawBody(result)).isEqualTo(200);
        return result;
    }

    // Hai phan con lai van SUCCEEDED co du lieu + token; o FAILED khong lo thong diep goc hay output tho.
    private static void assertOthersSucceeded(MvcResult result) throws Exception {
        JsonNode root = body(result);
        for (String part : List.of("criteria", "questions")) {
            assertThat(root.get(part).get("status").asString()).as(part).isEqualTo("SUCCEEDED");
            assertThat(root.get(part).get("data").isNull()).as(part).isFalse();
            assertThat(root.get(part).get("generationToken").asString()).as(part).isNotBlank();
        }
        assertThat(rawBody(result)).doesNotContain(SECRET).doesNotContain("Exception");
    }

    // (a) JOB hong ca 2 lan.
    @Test
    void jobInvalidTwice_jobFailedInvalidOutput_othersSucceed() throws Exception {
        String token = hrWithCompany("h11-t12a");

        MvcResult result = generateAllWithJob(token, always("khong phai JSON " + SECRET));

        assertFailed(body(result).get("job"), "AI_INVALID_OUTPUT", "AI trả về kết quả không hợp lệ, vui lòng thử lại.");
        assertOthersSucceeded(result);
        assertThat(callsOf(JobDraftPart.JOB)).isEqualTo(2);
    }

    // (b) Loi nha cung cap o lan 1 -> AI_UNAVAILABLE, khong thu lai (K3).
    @Test
    void jobRateLimitedByProvider_jobFailedUnavailable_oneCall_othersSucceed() throws Exception {
        String token = hrWithCompany("h11-t12b");

        MvcResult result = generateAllWithJob(token, call -> {
            throw RateLimitException.builder()
                    .headers(Headers.builder().build())
                    .body(JsonValue.from(Map.of("message", SECRET)))
                    .build();
        });

        assertFailed(body(result).get("job"), "AI_UNAVAILABLE",
                "Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau.");
        assertOthersSucceeded(result);
        assertThat(callsOf(JobDraftPart.JOB)).isEqualTo(1);
    }

    // (c) Mock JOB cho 3 s > han 2000 ms cua test -> AI_TIMEOUT; request khong cho het 3 s.
    @Test
    void jobSlowerThanTimeout_jobFailedTimeout_othersSucceed() throws Exception {
        String token = hrWithCompany("h11-t12c");
        long start = System.nanoTime();

        MvcResult result = generateAllWithJob(token, call -> {
            Thread.sleep(3000);
            return chatResponse(jobJson(fullJob()));
        });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFailed(body(result).get("job"), "AI_TIMEOUT", "AI phản hồi quá lâu, vui lòng thử lại.");
        assertOthersSucceeded(result);
        assertThat(callsOf(JobDraftPart.JOB)).isEqualTo(1);
        assertThat(elapsedMs).isLessThan(3000);
    }

    // Moi phan deu loi -> van 200, ba o FAILED.
    @Test
    void allPartsFail_stillHttp200_threeFailedCells() throws Exception {
        String token = hrWithCompany("h11-t12-all");
        stubParts(Map.of(
                JobDraftPart.JOB, call -> {
                    throw new AnthropicIoException(SECRET);
                },
                JobDraftPart.CRITERIA, always("{}{" + SECRET),
                JobDraftPart.QUESTIONS, call -> {
                    throw new AnthropicIoException(SECRET);
                }));

        MvcResult result = postGenerate(token, DESCRIPTION, JobDraftPart.JOB, JobDraftPart.CRITERIA,
                JobDraftPart.QUESTIONS);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode root = body(result);
        assertThat(root.get("job").get("status").asString()).isEqualTo("FAILED");
        assertThat(root.get("criteria").get("status").asString()).isEqualTo("FAILED");
        assertThat(root.get("questions").get("status").asString()).isEqualTo("FAILED");
        assertThat(rawBody(result)).doesNotContain(SECRET);
    }

    // ---- T22 phan transaction (R-K3H-3) ----

    @Test
    void facadeInsideTransaction_throwsIllegalState_noModelCall() throws Exception {
        String email = uniqueEmail("h11-t22-tx");
        String token = register("hr", email);
        createCompany(token);
        UUID ownerId = userIdOf(email);
        stubAllValid();
        JobAiDraftRequest request = new JobAiDraftRequest(DESCRIPTION, List.of(JobDraftPart.JOB, JobDraftPart.CRITERIA,
                JobDraftPart.QUESTIONS));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> facade.generate(ownerId, request)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R-K3H-3");
        verifyNoInteractions(chatModel);
    }
}
