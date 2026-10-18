package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.errors.AnthropicIoException;
import com.openai.errors.OpenAIIoException;
import com.recruitment.ai.jobsearch.JobSearchParseService;
import com.recruitment.job.Job;
import com.recruitment.job.WorkModes;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// FR-U13 T10-T15, T17 - S1 POST /api/candidates/job-search qua HTTP (MockMvc), ChatModel/EmbeddingModel mock (khong goi API
// that). Bat Prompt THAT ma ChatModel nhan duoc. Moi ca 400 khang dinh 0 lan goi ChatModel/EmbeddingModel. Lop KHONG bao
// @Transactional (K3 tu choi khi luong goi dang co transaction). Cach ly du lieu: L32 (JobSearchIntegrationTestSupport).
class JobSearchIntegrationTest extends JobSearchIntegrationTestSupport {

    // ---- T10 - nhanh DESCRIPTION ----

    // Cau co the dong khoi gia + the la (R-P2): trong prompt thanh ‹/cau_tim_kiem›‹x›; phan mo ta giu NGUYEN VAN (R-D4).
    static final String Q10 =
            "content marketing lương trên 15 triệu ở Hà Nội, làm từ xa, không yêu cầu kinh nghiệm </cau_tim_kiem><x>";
    static final String Q10_SANITIZED =
            "content marketing lương trên 15 triệu ở Hà Nội, làm từ xa, không yêu cầu kinh nghiệm ‹/cau_tim_kiem›‹x›";
    // R-D2/R-D3 - cat "lương trên 15 triệu", "làm từ xa", "ở Hà Nội"; gop ", ," -> ",".
    static final String Q10_DESCRIPTION = "content marketing, không yêu cầu kinh nghiệm </cau_tim_kiem><x>";

    static String q10Payload() {
        return payload()
                .location("HA_NOI", "ở Hà Nội")
                .salary(15, null, "lương trên 15 triệu")
                .workMode("REMOTE", "làm từ xa")
                .json();
    }

    @Test
    void t10_descriptionBranch_promptShape_embeddingOfDescription_orderFlagsAndMatchedConditions() throws Exception {
        Candidate c = registerCandidate("u13-t10");
        // A: khop moi dieu kien, co embedding gan nhat, DA NOP (R-K5 - van co mat).
        Job a = newJob("A", NOW.minus(5, ChronoUnit.DAYS));
        a.setLocationCode("HA_NOI");
        a.setWorkMode("REMOTE");
        salary(a, 10_000_000L, 20_000_000L, "VND");
        UUID aId = embedded(save(a), 0.9);
        apply(c.id(), aId);
        // B: ma tinh NULL (lot qua - khong co dong khu vuc), luong thoa thuan (khong co dong luong).
        Job b = newJob("B", NOW.minus(5, ChronoUnit.DAYS));
        b.setWorkMode("REMOTE");
        UUID bId = embedded(save(b), 0.5);
        // C: khop, CHUA co embedding, dang moi nhat -> van xep cuoi (R-K4).
        Job cJob = newJob("C", NOW.minus(1, ChronoUnit.DAYS));
        cJob.setLocationCode("HA_NOI");
        cJob.setWorkMode("REMOTE");
        salary(cJob, 16_000_000L, 18_000_000L, "VND");
        UUID cId = save(cJob);
        // Bi loai boi dieu kien du gan hon: tinh khac, hinh thuc khac, luong duoi moc.
        Job otherProvince = newJob("Da Nang", NOW);
        otherProvince.setLocationCode("DA_NANG");
        otherProvince.setWorkMode("REMOTE");
        embedded(save(otherProvince), 0.99);
        Job onsite = newJob("Onsite", NOW);
        onsite.setLocationCode("HA_NOI");
        onsite.setWorkMode("ONSITE");
        embedded(save(onsite), 0.95);
        Job lowSalary = newJob("9-14 trieu", NOW);
        lowSalary.setLocationCode("HA_NOI");
        lowSalary.setWorkMode("REMOTE");
        salary(lowSalary, 9_000_000L, 14_000_000L, "VND");
        embedded(save(lowSalary), 0.95);

        stubOutputs(q10Payload());
        stubEmbedding();

        MvcResult result = search(c.token(), Q10);

        assertStatus(result, 200);
        JsonNode root = body(result);
        assertThat(root.get("ranking").asString()).isEqualTo("DESCRIPTION");
        JsonNode interpretation = root.get("interpretation");
        assertThat(texts(interpretation.get("locationCodes"))).containsExactly("HA_NOI");
        assertThat(texts(interpretation.get("industryCodes"))).isEmpty();
        assertThat(interpretation.get("salaryMin").asInt()).isEqualTo(15);
        assertThat(interpretation.get("salaryMax").isNull()).isTrue();
        assertThat(texts(interpretation.get("workModes"))).containsExactly("REMOTE");
        assertThat(interpretation.get("description").asString()).isEqualTo(Q10_DESCRIPTION);
        assertThat(interpretation.get("personalInfoIgnored").asBoolean()).isFalse();

        assertThat(itemIds(root)).containsExactly(aId, bId, cId);
        assertThat(rankedFlags(root)).containsExactly(true, true, false);
        String hanoi = catalogRegistry.provinceLabel("HA_NOI");
        String remote = WorkModes.LABELS.get("REMOTE");
        assertThat(matched(root.get("items").get(0))).containsExactly(hanoi, remote, JobSearchMatcher.SALARY_MATCH_LABEL);
        assertThat(matched(root.get("items").get(1))).containsExactly(remote);
        assertThat(matched(root.get("items").get(2))).containsExactly(hanoi, remote, JobSearchMatcher.SALARY_MATCH_LABEL);

        // R-E4/R-D4 - dung MOT lan embedding, voi DUNG chuoi description (khong phai nguyen cau).
        assertThat(embeddingInputs()).containsExactly(List.of(Q10_DESCRIPTION));

        Prompt prompt = capturedPrompts(1).get(0);
        String system = lf(prompt.getSystemMessage().getText());
        String user = lf(prompt.getUserMessage().getText());
        // R-P1 - system = file prompt + hai danh muc; khong chua cau.
        String file = lf(new ClassPathResource("ai/prompt/" + "job-search-parse-v1.st")
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(system).startsWith(file.substring(0, file.indexOf("{provinceCatalog}")));
        assertThat(system).contains(catalogPrompt.provinceCatalog()).contains(catalogPrompt.industryCatalog());
        assertThat(system).doesNotContain("content marketing", "không yêu cầu kinh nghiệm", "‹/cau_tim_kiem›", "<x>");
        // R-P2 - user message: khoi <cau_tim_kiem> (startsWith - Spring AI noi huong dan JSON vao cuoi), the that dung
        // mot lan, the gia doi ‹ ›.
        assertThat(user).startsWith("<cau_tim_kiem>\n" + Q10_SANITIZED + "\n</cau_tim_kiem>");
        assertThat(occurrences(user, "<cau_tim_kiem>")).isEqualTo(1);
        assertThat(occurrences(user, "</cau_tim_kiem>")).isEqualTo(1);
        assertThat(user).contains("‹/cau_tim_kiem›‹x›").doesNotContain("<x>");
        assertThat(prompt.getOptions().getMaxTokens()).isEqualTo(500);
    }

    // ---- T11 - nhanh NEWEST ----

    @Test
    void t11_allConditionsExtracted_noDescription_newestOrder_noEmbeddingCall() throws Exception {
        Candidate c = registerCandidate("u13-t11");
        Job first = newJob("1 ngay", NOW.minus(1, ChronoUnit.DAYS));
        first.setLocationCode("HA_NOI");
        first.setCategoryCode("ACCOUNTING_AUDIT");
        // Co embedding nhung nhanh NEWEST khong dung toi -> rankedByDescription van false.
        UUID firstId = embedded(save(first), 0.9);
        UUID nullCodes = save(newJob("ma NULL", NOW.minus(2, ChronoUnit.DAYS)));
        Job third = newJob("3 ngay", NOW.minus(3, ChronoUnit.DAYS));
        third.setLocationCode("HA_NOI");
        third.setCategoryCode("ACCOUNTING_AUDIT");
        UUID thirdId = save(third);
        Job otherProvince = newJob("Da Nang", NOW);
        otherProvince.setLocationCode("DA_NANG");
        otherProvince.setCategoryCode("ACCOUNTING_AUDIT");
        save(otherProvince);

        stubOutputs(payload().location("HA_NOI", "ở Hà Nội").industry("ACCOUNTING_AUDIT", "kế toán").json());
        // Stub de mot loi goi nham (dot bien) KHONG nem loi ma bi dem duoc.
        stubEmbedding();

        MvcResult result = search(c.token(), "kế toán ở Hà Nội");

        assertStatus(result, 200);
        JsonNode root = body(result);
        assertThat(root.get("ranking").asString()).isEqualTo("NEWEST");
        assertThat(root.get("interpretation").get("description").isNull()).isTrue();
        assertThat(texts(root.get("interpretation").get("locationCodes"))).containsExactly("HA_NOI");
        assertThat(texts(root.get("interpretation").get("industryCodes"))).containsExactly("ACCOUNTING_AUDIT");
        assertThat(embeddingCalls()).isZero();
        assertThat(itemIds(root)).containsExactly(firstId, nullCodes, thirdId);
        assertThat(rankedFlags(root)).containsOnly(false);
        // Thu tu = dung truy van U07 sap moi nhat, trang 0 co 50, cung dieu kien.
        List<UUID> expected = jobRepository
                .searchPublicJobsSortedByNewest(
                        null, null, null,
                        true, List.of("ACCOUNTING_AUDIT"),
                        true, List.of("HA_NOI"),
                        null, null, false, false, List.of("__NONE__"), null,
                        PageRequest.of(0, 50))
                .getContent().stream().map(Job::getId).toList();
        assertThat(itemIds(root)).isEqualTo(expected);
    }

    @Test
    void t11_personalInfoOnly_ignored_noConditions_newest_noEmbeddingCall() throws Exception {
        Candidate c = registerCandidate("u13-t11-pi");
        UUID older = save(newJob("cu", NOW.minus(2, ChronoUnit.DAYS)));
        UUID newer = save(newJob("moi", NOW.minus(1, ChronoUnit.DAYS)));
        stubOutputs(payload().personalInfo("nữ dưới 30 tuổi").json());
        stubEmbedding();

        MvcResult result = search(c.token(), "nữ dưới 30 tuổi");

        assertStatus(result, 200);
        JsonNode root = body(result);
        JsonNode interpretation = root.get("interpretation");
        assertThat(interpretation.get("personalInfoIgnored").asBoolean()).isTrue();
        assertThat(interpretation.get("description").isNull()).isTrue();
        assertThat(texts(interpretation.get("locationCodes"))).isEmpty();
        assertThat(texts(interpretation.get("industryCodes"))).isEmpty();
        assertThat(texts(interpretation.get("workModes"))).isEmpty();
        assertThat(interpretation.get("salaryMin").isNull()).isTrue();
        assertThat(interpretation.get("salaryMax").isNull()).isTrue();
        assertThat(root.get("ranking").asString()).isEqualTo("NEWEST");
        assertThat(embeddingCalls()).isZero();
        assertThat(itemIds(root)).containsExactly(newer, older);
    }

    // ---- T12 - thu lai va loi K3 ----

    @Test
    void t12_firstOutputNotVerbatim_secondValid_returns200_twoChatCalls() throws Exception {
        Candidate c = registerCandidate("u13-t12-retry");
        save(newJob("viec", NOW));
        stubOutputs(payload().location("HA_NOI", "ở Ha Noi").json(), payload().location("HA_NOI", "ở Hà Nội").json());
        stubEmbedding();

        MvcResult result = search(c.token(), "kế toán ở Hà Nội");

        assertStatus(result, 200);
        assertThat(texts(body(result).get("interpretation").get("locationCodes"))).containsExactly("HA_NOI");
        capturedPrompts(2);
        assertThat(embeddingInputs()).containsExactly(List.of("kế toán"));
    }

    @Test
    void t12_bothOutputsInvalid_returns502_noEmbeddingCall() throws Exception {
        Candidate c = registerCandidate("u13-t12-502");
        stubOutputs(payload().location("HA_NOI", "ở Ha Noi").json(), "khong phai JSON");
        stubEmbedding();

        MvcResult result = search(c.token(), "kế toán ở Hà Nội");

        assertError(result, 502, "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        capturedPrompts(2);
        assertThat(embeddingCalls()).isZero();
    }

    @Test
    void t12_chatProviderIoError_returns503_oneChatCall_noEmbeddingCall() throws Exception {
        Candidate c = registerCandidate("u13-t12-503-llm");
        doThrow(new AnthropicIoException("BI-MAT-KHONG-DUOC-LO-RA")).when(chatModel).call(any(Prompt.class));
        stubEmbedding();

        MvcResult result = search(c.token(), "kế toán ở Hà Nội");

        assertError(result, 503, "AI_UNAVAILABLE", UNAVAILABLE_MESSAGE);
        assertThat(rawBody(result)).doesNotContain("BI-MAT-KHONG-DUOC-LO-RA");
        capturedPrompts(1);
        assertThat(embeddingCalls()).isZero();
    }

    // R-E5 - loi embedding tra loi K3, KHONG tra danh sach moi nhat thay the (du co viec OPEN de tra).
    @Test
    void t12_embeddingFails_returns503_noFallbackToNewest() throws Exception {
        Candidate c = registerCandidate("u13-t12-503-emb");
        save(newJob("viec OPEN", NOW));
        stubOutputs(payload().location("HA_NOI", "ở Hà Nội").json());
        doThrow(new OpenAIIoException("BI-MAT-NHUNG")).when(embeddingModel).embedForResponse(anyList());

        MvcResult result = search(c.token(), "kế toán ở Hà Nội");

        assertError(result, 503, "AI_UNAVAILABLE", UNAVAILABLE_MESSAGE);
        assertThat(rawBody(result)).doesNotContain("BI-MAT-NHUNG", "items", "NEWEST");
        capturedPrompts(1);
        assertThat(embeddingInputs()).containsExactly(List.of("kế toán"));
    }

    // Profile test: app.ai-sync.embedding-timeout-ms = 2000. Stub cho tren latch (toi da 10 s) thay vi ngu mot khoang co
    // dinh (khuon U12 T19): request phai xong (504) sau ~2 s, bien < 8 s. finally mo latch de luong stub thoat ngay.
    @Test
    void t12_embeddingSlowerThanTimeout_returns504_oneEmbeddingCall() throws Exception {
        Candidate c = registerCandidate("u13-t12-504");
        save(newJob("viec OPEN", NOW));
        stubOutputs(payload().location("HA_NOI", "ở Hà Nội").json());
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    release.await(10, TimeUnit.SECONDS);
                    return embeddingResponse(queryVector());
                })
                .when(embeddingModel)
                .embedForResponse(anyList());

        try {
            long start = System.nanoTime();
            MvcResult result = search(c.token(), "kế toán ở Hà Nội");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertError(result, 504, "AI_TIMEOUT", TIMEOUT_MESSAGE);
            assertThat(elapsedMs).isLessThan(8000);
            capturedPrompts(1);
            assertThat(embeddingInputs()).containsExactly(List.of("kế toán"));
        } finally {
            release.countDown();
        }
    }

    // ---- T13 - dau vao (R-I), chuoi bien muc 13.8 viet bang escape ----

    // "ke toan" co dau: k + U+1EBF (e mu sac) + " to" + U+00E1 (a sac) + "n".
    static final String KE_TOAN = "kế toán";

    @Test
    void t13_lengthBelowAndAboveLimits_measuredAfterNormalization_rejected_noAiCall() throws Exception {
        Candidate c = registerCandidate("u13-t13-len");

        // nguong-1
        assertError(search(c.token(), "a"), 400, "INVALID_JOB_SEARCH_REQUEST", TOO_SHORT_MESSAGE);
        // nguong+1
        assertError(search(c.token(), "a".repeat(301)), 400, "INVALID_JOB_SEARCH_REQUEST", TOO_LONG_MESSAGE);
        // gop trang: 303 truoc -> 301 sau
        assertError(search(c.token(), "a".repeat(298) + "\r\n\t" + "bb"), 400, "INVALID_JOB_SEARCH_REQUEST",
                TOO_LONG_MESSAGE);
        // NFC: 302 truoc (a + U+0300 la hai ky tu) -> 301 sau
        assertError(search(c.token(), "a".repeat(300) + "à"), 400, "INVALID_JOB_SEARCH_REQUEST",
                TOO_LONG_MESSAGE);
        assertNoAiCall();
    }

    @Test
    void t13_boundaryInputsAccepted_normalizedQuerySentToAiAndEmbedding() throws Exception {
        Candidate c = registerCandidate("u13-t13-ok");
        stubOutputs(payload().json());
        stubEmbedding();
        String nfcTail = "a".repeat(299) + "à";
        // {dau vao, q sau R-I2}
        List<String[]> cases = List.of(
                new String[] {"ab", "ab"},                                         // dung nguong duoi
                new String[] {"a".repeat(300), "a".repeat(300)},                   // dung nguong tren
                new String[] {"a".repeat(297) + "\r\n\t" + "bb", "a".repeat(297) + " bb"}, // 302 -> 300
                new String[] {"  ab  ", "ab"},                                     // 6 -> 2
                new String[] {"a".repeat(299) + "à", nfcTail});              // 301 -> 300 (NFC)
        assertThat(cases.get(2)[0]).hasSize(302);
        assertThat(cases.get(4)[0]).hasSize(301);
        assertThat(nfcTail).hasSize(300);

        for (String[] testCase : cases) {
            assertStatus(search(c.token(), testCase[0]), 200);
        }

        List<Prompt> prompts = capturedPrompts(cases.size());
        List<List<String>> expectedEmbeddingInputs = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            String q = cases.get(i)[1];
            assertThat(lf(prompts.get(i).getUserMessage().getText())).startsWith("<cau_tim_kiem>\n" + q + "\n</cau_tim_kiem>");
            // Payload rong -> phan mo ta = ca cau da chuan hoa.
            expectedEmbeddingInputs.add(List.of(q));
        }
        assertThat(embeddingInputs()).isEqualTo(expectedEmbeddingInputs);
    }

    @Test
    void t13_nullEmptyBlank_rejected_noAiCall() throws Exception {
        Candidate c = registerCandidate("u13-t13-empty");

        for (String query : new String[] {null, "", "   "}) {
            assertError(search(c.token(), query), 400, "INVALID_JOB_SEARCH_REQUEST", EMPTY_MESSAGE);
        }
        // Thieu khoa query -> query null.
        assertError(postJson(c.token(), "{}"), 400, "INVALID_JOB_SEARCH_REQUEST", EMPTY_MESSAGE);
        assertNoAiCall();
    }

    @Test
    void t13_formatAndControlCharacters_rejected_noAiCall() throws Exception {
        Candidate c = registerCandidate("u13-t13-char");

        // U+200B o cuoi, U+FEFF giua cau, U+0007 giua cau (L14 - trim() cat ky tu dieu khien o hai dau).
        for (String query : new String[] {KE_TOAN + "​", "kế﻿toán", "ab\u0007cd"}) {
            assertError(search(c.token(), query), 400, "INVALID_JOB_SEARCH_REQUEST", CHARACTER_MESSAGE);
        }
        assertNoAiCall();
    }

    @Test
    void t13_unreadableBody_brokenJsonOrArrayQuery_rejected_noAiCall() throws Exception {
        Candidate c = registerCandidate("u13-t13-body");

        assertError(postJson(c.token(), "{\"query\":"), 400, "INVALID_JOB_SEARCH_REQUEST", UNREADABLE_MESSAGE);
        assertError(postJson(c.token(), "{\"query\":[\"a\"]}"), 400, "INVALID_JOB_SEARCH_REQUEST", UNREADABLE_MESSAGE);
        assertNoAiCall();
    }

    // L34 - Jackson ep so thanh chuoi: 5 -> "5" (1 ky tu) -> R-I3, khong phai body hong.
    @Test
    void t13_numericQuery_coercedToString_rejectedAsTooShort_noAiCall() throws Exception {
        Candidate c = registerCandidate("u13-t13-number");

        assertError(postJson(c.token(), "{\"query\":5}"), 400, "INVALID_JOB_SEARCH_REQUEST", TOO_SHORT_MESSAGE);
        assertNoAiCall();
    }

    // ---- T14 - quyen ----

    @Test
    void t14_hrForbidden_noTokenUnauthenticated_getMethodNotAllowed_noAiCall() throws Exception {
        String hrToken = register("hr", uniqueEmail("u13-t14-hr"));
        Candidate c = registerCandidate("u13-t14");

        assertStatus(search(hrToken, "kế toán ở Hà Nội"), 403);
        assertError(search(null, "kế toán ở Hà Nội"), 401, "UNAUTHENTICATED", "Cần đăng nhập để truy cập tài nguyên này");
        // L4 - MockMvc khong co error dispatch: 405 mac dinh cua Spring (server that tra 401 - A7 o 7.2).
        MvcResult getResult = mockMvc
                .perform(get(PATH).header("Authorization", "Bearer " + c.token()))
                .andReturn();
        assertThat(getResult.getResponse().getStatus()).isEqualTo(405);
        assertNoAiCall();
    }

    // ---- T15 - khong tac dung phu ----

    static final String MARKER = "ZQXU13LOGMARK";

    @Test
    void t15_noRowsWritten_onSuccessAndOn502() throws Exception {
        Candidate c = registerCandidate("u13-t15-rows");
        Job job = newJob("viec", NOW);
        job.setLocationCode("HA_NOI");
        embedded(save(job), 0.7);
        List<Long> before = rowCounts();

        stubOutputs(payload().location("HA_NOI", "ở Hà Nội").json());
        stubEmbedding();
        assertStatus(search(c.token(), "kế toán ở Hà Nội"), 200);
        assertThat(rowCounts()).isEqualTo(before);

        stubOutputs(payload().location("HA_NOI", "ở Ha Noi").json());
        assertError(search(c.token(), "kế toán ở Hà Nội"), 502, "AI_INVALID_OUTPUT", INVALID_OUTPUT_MESSAGE);
        assertThat(rowCounts()).isEqualTo(before);
    }

    @Test
    void t15_serviceControllerAndParseService_areNotTransactional() {
        for (Class<?> type : List.of(JobSearchService.class, JobSearchCandidateController.class,
                JobSearchParseService.class)) {
            assertThat(type.isAnnotationPresent(Transactional.class)).as(type.getSimpleName()).isFalse();
            assertThat(type.isAnnotationPresent(jakarta.transaction.Transactional.class))
                    .as(type.getSimpleName())
                    .isFalse();
            for (Method method : type.getDeclaredMethods()) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName())
                        .isFalse();
                assertThat(method.isAnnotationPresent(jakarta.transaction.Transactional.class))
                        .as(type.getSimpleName() + "." + method.getName())
                        .isFalse();
            }
        }
    }

    // R-L2 - log muc info/warn/error cua mot luot 200 va mot luot 503 (loi embedding - cau chua chuoi danh dau nam trong
    // phan mo ta, di qua SyncEmbeddingCaller/EmbeddingService) khong chua chuoi danh dau. Doi chung duong: luot 503 CO log
    // warn "ma=AI_UNAVAILABLE" (appender bat duoc log that).
    @Test
    void t15_infoWarnErrorLogs_doNotContainQueryMarker_on200And503() throws Exception {
        Candidate c = registerCandidate("u13-t15-log");
        save(newJob("viec", NOW));
        String query = "kế toán " + MARKER + " ở Hà Nội";
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        try {
            stubOutputs(payload().location("HA_NOI", "ở Hà Nội").json());
            stubEmbedding();
            assertStatus(search(c.token(), query), 200);
            assertThat(embeddingInputs()).containsExactly(List.of("kế toán " + MARKER));

            doThrow(new OpenAIIoException("mang")).when(embeddingModel).embedForResponse(anyList());
            assertError(search(c.token(), query), 503, "AI_UNAVAILABLE", UNAVAILABLE_MESSAGE);
        } finally {
            root.detachAppender(appender);
        }

        List<String> logged = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (!event.getLevel().isGreaterOrEqual(Level.INFO)) {
                continue;
            }
            logged.add(event.getFormattedMessage());
            for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
                logged.add(String.valueOf(t.getMessage()));
            }
        }
        assertThat(logged).noneMatch(line -> line.contains(MARKER));
        assertThat(logged).anyMatch(line -> line.contains("ma=AI_UNAVAILABLE"));
    }

    // ---- T17 - dieu kien AI khong hop le bi bo, chu o lai ----

    @Test
    void t17_unknownProvinceOtherIndustryAndInvertedSalary_dropped_textStaysInDescriptionAndEmbedding() throws Exception {
        Candidate c = registerCandidate("u13-t17");
        save(newJob("viec", NOW));
        String query = "kế toán ở Sao Hoả, ngành khác, lương 20 đến 10 triệu";
        stubOutputs(payload()
                .location("SAO_HOA", "ở Sao Hoả")
                .industry("OTHER", "ngành khác")
                .salary(20, 10, "lương 20 đến 10 triệu")
                .json());
        stubEmbedding();

        MvcResult result = search(c.token(), query);

        assertStatus(result, 200);
        JsonNode interpretation = body(result).get("interpretation");
        assertThat(texts(interpretation.get("locationCodes"))).isEmpty();
        assertThat(texts(interpretation.get("industryCodes"))).isEmpty();
        assertThat(interpretation.get("salaryMin").isNull()).isTrue();
        assertThat(interpretation.get("salaryMax").isNull()).isTrue();
        assertThat(interpretation.get("description").asString()).isEqualTo(query);
        assertThat(body(result).get("ranking").asString()).isEqualTo("DESCRIPTION");
        assertThat(embeddingInputs()).containsExactly(List.of(query));
    }

    // R-D1 - doan dung chung boi mot dieu kien NHAN (HA_NOI) va mot dieu kien BO (SAO_HOA) -> giu nguyen trong mo ta.
    @Test
    void t17_sharedSourceTextAcceptedAndDropped_staysInDescription_acceptedCodeKept() throws Exception {
        Candidate c = registerCandidate("u13-t17-shared");
        save(newJob("viec", NOW));
        String query = "kế toán ở Hà Nội hoặc Sao Hoả";
        stubOutputs(payload()
                .location("HA_NOI", "ở Hà Nội hoặc Sao Hoả")
                .location("SAO_HOA", "ở Hà Nội hoặc Sao Hoả")
                .json());
        stubEmbedding();

        MvcResult result = search(c.token(), query);

        assertStatus(result, 200);
        JsonNode interpretation = body(result).get("interpretation");
        assertThat(texts(interpretation.get("locationCodes"))).containsExactly("HA_NOI");
        assertThat(interpretation.get("description").asString()).isEqualTo(query);
        assertThat(embeddingInputs()).containsExactly(List.of(query));
    }

    // ---- helper ----

    private List<Long> rowCounts() {
        return List.of(
                countRows("jobs"), countRows("job_embeddings"), countRows("job_applications"), countRows("notifications"));
    }

    private static String lf(String text) {
        return text.replace("\r\n", "\n");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
