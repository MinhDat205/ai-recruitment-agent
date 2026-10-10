package com.recruitment.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.TimeMeter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

// Test THUAN Java (khong @SpringBootTest) - OncePerRequestFilter.doFilter() goi duoc truc tiep tren
// mot instance "new" bang tay voi MockHttpServletRequest/Response/FilterChain (spring-test, da co
// san qua spring-boot-starter-webmvc-test), khong can trien khai qua servlet container that. Day la
// LY DO duy nhat filter nay kiem duoc day du ma khong dung chung bucket voi 20 @SpringBootTest khac
// dang goi that /api/auth/login (xem RateLimitFilter/app.rate-limit.enabled).
class RateLimitFilterTest {

    // Dong ho gia CO DINH (khong can tien len - refill deterministic da kiem rieng o
    // RateLimitBucketStoreTest) - o day chi can mot TimeMeter hop le de dung chung mot
    // RateLimitBucketStore giua cac request trong CUNG mot test.
    private static final class FixedTimeMeter implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            return 0L;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    @AfterEach
    void clearSecurityContext() {
        // SecurityContextHolder la ThreadLocal tinh - phai don sau MOI test, khong thi mot test set
        // Authentication se ro sang test chay sau tren cung thread.
        SecurityContextHolder.clearContext();
    }

    private RateLimitFilter newFilter(
            long authCapacity,
            long authRefill,
            long llmCapacity,
            long llmRefill,
            long messageCapacity,
            long messageRefill,
            long aiSyncCapacity,
            long aiSyncRefill) {
        return new RateLimitFilter(
                new RateLimitBucketStore(1000, new FixedTimeMeter()),
                authCapacity,
                authRefill,
                llmCapacity,
                llmRefill,
                messageCapacity,
                messageRefill,
                aiSyncCapacity,
                aiSyncRefill);
    }

    private void authenticateAs(String userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
    }

    // Request thu N+1 (N=capacity) tren CUNG mot IP toi /api/auth/login -> 429 kem header Retry-After.
    @Test
    void doFilter_loginRequestExceedingCapacity_returns429WithRetryAfterHeader() throws Exception {
        RateLimitFilter filter = newFilter(3, 3, 20, 20, 20, 20, 20, 20);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            doFilter(filter, request, response);
            assertThat(response.getStatus()).as("request thu %d phai qua duoc", i + 1).isEqualTo(200);
        }

        MockHttpServletRequest rejectedRequest = new MockHttpServletRequest("POST", "/api/auth/login");
        rejectedRequest.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        doFilter(filter, rejectedRequest, rejectedResponse);

        assertThat(rejectedResponse.getStatus()).isEqualTo(429);
        assertThat(rejectedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(Long.parseLong(rejectedResponse.getHeader("Retry-After"))).isGreaterThan(0);
        assertThat(rejectedResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    // Mot IP khac KHONG bi anh huong boi viec IP kia da het han muc - dung yeu cau "IP/user khac
    // khong bi anh huong" cua Dot 5.
    @Test
    void doFilter_differentIpNotAffectedByOtherIpExhaustingLimit() throws Exception {
        RateLimitFilter filter = newFilter(3, 3, 20, 20, 20, 20, 20, 20);
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.0.0.1");
            doFilter(filter, request, new MockHttpServletResponse());
        }
        // IP 10.0.0.1 da het han muc.
        MockHttpServletRequest exhausted = new MockHttpServletRequest("POST", "/api/auth/login");
        exhausted.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse exhaustedResponse = new MockHttpServletResponse();
        doFilter(filter, exhausted, exhaustedResponse);
        assertThat(exhaustedResponse.getStatus()).isEqualTo(429);

        MockHttpServletRequest otherIp = new MockHttpServletRequest("POST", "/api/auth/login");
        otherIp.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse otherIpResponse = new MockHttpServletResponse();
        doFilter(filter, otherIp, otherIpResponse);

        assertThat(otherIpResponse.getStatus()).isEqualTo(200);
    }

    // Nhom (b) (LLM-action) khoa theo userId, KHONG theo IP - hai request tu CUNG IP nhung KHAC
    // user khong duoc dung chung han muc.
    @Test
    void doFilter_llmActionEndpoint_ratelimitedByUserIdNotByIp() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 2, 2, 20, 20, 20, 20);
        authenticateAs("11111111-1111-1111-1111-111111111111");
        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/candidates/resumes");
            request.setRemoteAddr("10.0.0.1");
            doFilter(filter, request, new MockHttpServletResponse());
        }
        MockHttpServletRequest exhausted = new MockHttpServletRequest("POST", "/api/candidates/resumes");
        exhausted.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse exhaustedResponse = new MockHttpServletResponse();
        doFilter(filter, exhausted, exhaustedResponse);
        assertThat(exhaustedResponse.getStatus()).isEqualTo(429);

        // User KHAC, CUNG IP - khong duoc dung chung han muc voi user tren.
        SecurityContextHolder.clearContext();
        authenticateAs("22222222-2222-2222-2222-222222222222");
        MockHttpServletRequest otherUser = new MockHttpServletRequest("POST", "/api/candidates/resumes");
        otherUser.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse otherUserResponse = new MockHttpServletResponse();
        doFilter(filter, otherUser, otherUserResponse);

        assertThat(otherUserResponse.getStatus()).isEqualTo(200);
    }

    // Endpoint LLM-action nhung CHUA xac thuc (thieu/sai token, JwtAuthenticationFilter khong set
    // Authentication) - phai cho qua (KHONG rate limit), de FilterSecurityInterceptor (chay sau
    // trong chain that) tu tra 401/403 dung ly do.
    @Test
    void doFilter_llmActionEndpointWithoutAuthentication_passesThroughUnaffected() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 1, 1, 20, 20, 20, 20);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/candidates/resumes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doFilter(filter, request, response);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // GET toi cung duong dan KHONG bi rate limit - chi POST toi 5 endpoint da duyet moi bi ap.
    @Test
    void doFilter_getRequestToLoginPath_notRateLimited() throws Exception {
        RateLimitFilter filter = newFilter(1, 1, 20, 20, 20, 20, 20, 20);
        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/auth/login");
        doFilter(filter, first, new MockHttpServletResponse());
        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/auth/login");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        doFilter(filter, second, secondResponse);

        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    // Duong dan khong lien quan (vd GET danh sach CV cua chinh minh) khong bi rate limit du la cung
    // tien to /api/candidates/resumes.
    @Test
    void doFilter_unrelatedPath_passesThroughWithoutRateLimiting() throws Exception {
        RateLimitFilter filter = newFilter(1, 1, 1, 1, 20, 20, 20, 20);
        authenticateAs("11111111-1111-1111-1111-111111111111");
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/candidates/resumes");
            MockHttpServletResponse response = new MockHttpServletResponse();
            doFilter(filter, request, response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private void doFilter(RateLimitFilter filter, MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        filter.doFilter(request, response, new MockFilterChain());
    }

    // FR-C05 R-R3 - trich xuat lai CV thuoc nhom llm-action theo userId: vuot han muc -> 429, va dung
    // CHUNG bucket voi cac endpoint ton LLM khac cua cung user.
    @Test
    void doFilter_resumeReparseEndpoint_rateLimitedByUserId() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 2, 2, 20, 20, 20, 20);
        authenticateAs("33333333-3333-3333-3333-333333333333");
        String path = "/api/candidates/resumes/44444444-4444-4444-4444-444444444444/reparse";
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            doFilter(filter, new MockHttpServletRequest("POST", path), response);
            assertThat(response.getStatus()).as("request thu %d phai qua duoc", i + 1).isEqualTo(200);
        }

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", path), rejected);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void doFilter_resumeReparseSharesBucketWithOtherLlmActions() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 1, 1, 20, 20, 20, 20);
        authenticateAs("55555555-5555-5555-5555-555555555555");
        doFilter(
                filter,
                new MockHttpServletRequest("POST", "/api/candidates/resumes/66666666-6666-6666-6666-666666666666/improvement-suggestions"),
                new MockHttpServletResponse());

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        doFilter(
                filter,
                new MockHttpServletRequest("POST", "/api/candidates/resumes/66666666-6666-6666-6666-666666666666/reparse"),
                rejected);

        assertThat(rejected.getStatus()).isEqualTo(429);
    }

    // ---- FR-C06 T23 - nhom "message" (R-L1): POST hai mau duong dan M2, theo userId, nguong 20 ----

    private static final String HR_MESSAGES_PATH =
            "/api/hr/applications/77777777-7777-7777-7777-777777777777/messages";
    private static final String CANDIDATE_MESSAGES_PATH =
            "/api/candidates/applications/88888888-8888-8888-8888-888888888888/messages";

    @Test
    void doFilter_messageSend_20thPasses_21stRejected_onBothPaths() throws Exception {
        for (String path : List.of(HR_MESSAGES_PATH, CANDIDATE_MESSAGES_PATH)) {
            RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 20, 20);
            SecurityContextHolder.clearContext();
            authenticateAs("99999999-9999-9999-9999-999999999999");
            for (int i = 0; i < 20; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                doFilter(filter, new MockHttpServletRequest("POST", path), response);
                assertThat(response.getStatus()).as("%s - request thu %d phai qua duoc", path, i + 1).isEqualTo(200);
            }

            MockHttpServletResponse rejected = new MockHttpServletResponse();
            doFilter(filter, new MockHttpServletRequest("POST", path), rejected);
            assertThat(rejected.getStatus()).as("%s - request thu 21", path).isEqualTo(429);
            assertThat(rejected.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        }
    }

    @Test
    void doFilter_messageSend_isLimitedPerUser_andHrAndCandidatePathsShareTheUserBucket() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 2, 2, 20, 20);
        authenticateAs("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        doFilter(filter, new MockHttpServletRequest("POST", HR_MESSAGES_PATH), new MockHttpServletResponse());
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_MESSAGES_PATH), new MockHttpServletResponse());
        MockHttpServletResponse exhausted = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", HR_MESSAGES_PATH), exhausted);
        assertThat(exhausted.getStatus()).isEqualTo(429);

        SecurityContextHolder.clearContext();
        authenticateAs("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        MockHttpServletResponse otherUser = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", HR_MESSAGES_PATH), otherUser);
        assertThat(otherUser.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_messageGetAndMarkReadAndAttachment_areNotLimited() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 1, 1, 20, 20);
        authenticateAs("cccccccc-cccc-cccc-cccc-cccccccccccc");
        for (int i = 0; i < 5; i++) {
            for (MockHttpServletRequest request : List.of(
                    new MockHttpServletRequest("GET", HR_MESSAGES_PATH),
                    new MockHttpServletRequest("PATCH", HR_MESSAGES_PATH + "/read"),
                    new MockHttpServletRequest("PATCH", CANDIDATE_MESSAGES_PATH + "/read"),
                    new MockHttpServletRequest("GET", CANDIDATE_MESSAGES_PATH + "/dddddddd-dddd-dddd-dddd-dddddddddddd/attachment"),
                    new MockHttpServletRequest("GET", "/api/hr/messages/conversations"))) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                doFilter(filter, request, response);
                assertThat(response.getStatus()).as("%s %s", request.getMethod(), request.getRequestURI()).isEqualTo(200);
            }
        }
    }

    @Test
    void doFilter_messageBucketIsSeparateFromLlmActionBucket() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 20, 20);
        authenticateAs("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        for (int i = 0; i < 20; i++) {
            doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_MESSAGES_PATH), new MockHttpServletResponse());
        }
        MockHttpServletResponse messageExhausted = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_MESSAGES_PATH), messageExhausted);
        assertThat(messageExhausted.getStatus()).isEqualTo(429);

        // Cung userId, nhom llm-action (khoa llm:) van con nguyen han muc.
        MockHttpServletResponse llmAction = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", "/api/candidates/resumes"), llmAction);
        assertThat(llmAction.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_messageSendWithoutAuthentication_passesThroughUnaffected() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 1, 1, 20, 20);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            doFilter(filter, new MockHttpServletRequest("POST", HR_MESSAGES_PATH), response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    // ---- FR-C07 T19 - nhom "ai-sync" (R-K3-8): POST hai mau A2, theo userId, suc chua 5 ----

    private static final String HR_AI_DRAFT_PATH =
            "/api/hr/applications/77777777-7777-7777-7777-777777777777/messages/ai-draft";
    private static final String CANDIDATE_AI_DRAFT_PATH =
            "/api/candidates/applications/88888888-8888-8888-8888-888888888888/messages/ai-draft";

    @Test
    void doFilter_aiDraft_5thPasses_6thRejected_onBothPaths() throws Exception {
        for (String path : List.of(HR_AI_DRAFT_PATH, CANDIDATE_AI_DRAFT_PATH)) {
            RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 5, 2);
            SecurityContextHolder.clearContext();
            authenticateAs("f1111111-1111-1111-1111-111111111111");
            for (int i = 0; i < 5; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                doFilter(filter, new MockHttpServletRequest("POST", path), response);
                assertThat(response.getStatus()).as("%s - request thu %d phai qua duoc", path, i + 1).isEqualTo(200);
            }

            MockHttpServletResponse rejected = new MockHttpServletResponse();
            doFilter(filter, new MockHttpServletRequest("POST", path), rejected);
            assertThat(rejected.getStatus()).as("%s - request thu 6", path).isEqualTo(429);
            assertThat(rejected.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        }
    }

    @Test
    void doFilter_aiDraft_isLimitedPerUser_andHrAndCandidatePathsShareTheUserBucket() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 2, 2);
        authenticateAs("f2222222-2222-2222-2222-222222222222");
        doFilter(filter, new MockHttpServletRequest("POST", HR_AI_DRAFT_PATH), new MockHttpServletResponse());
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_AI_DRAFT_PATH), new MockHttpServletResponse());
        MockHttpServletResponse exhausted = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", HR_AI_DRAFT_PATH), exhausted);
        assertThat(exhausted.getStatus()).isEqualTo(429);

        SecurityContextHolder.clearContext();
        authenticateAs("f3333333-3333-3333-3333-333333333333");
        MockHttpServletResponse otherUser = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", HR_AI_DRAFT_PATH), otherUser);
        assertThat(otherUser.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilter_aiDraftScenariosGet_isNotLimited() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 1, 1);
        authenticateAs("f4444444-4444-4444-4444-444444444444");
        for (int i = 0; i < 5; i++) {
            for (String path : List.of(HR_AI_DRAFT_PATH + "/scenarios", CANDIDATE_AI_DRAFT_PATH + "/scenarios")) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                doFilter(filter, new MockHttpServletRequest("GET", path), response);
                assertThat(response.getStatus()).as("GET %s", path).isEqualTo(200);
            }
        }
    }

    @Test
    void doFilter_aiSyncBucketExhausted_messageAndLlmActionStillPass() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 1, 1);
        authenticateAs("f5555555-5555-5555-5555-555555555555");
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_AI_DRAFT_PATH), new MockHttpServletResponse());
        MockHttpServletResponse aiSyncExhausted = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_AI_DRAFT_PATH), aiSyncExhausted);
        assertThat(aiSyncExhausted.getStatus()).isEqualTo(429);

        MockHttpServletResponse message = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_MESSAGES_PATH), message);
        assertThat(message.getStatus()).as("nhom message").isEqualTo(200);

        MockHttpServletResponse llmAction = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", "/api/candidates/resumes"), llmAction);
        assertThat(llmAction.getStatus()).as("nhom llm-action").isEqualTo(200);
    }

    @Test
    void doFilter_messageAndLlmActionBucketsExhausted_aiDraftStillPasses() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 1, 1, 1, 1, 5, 2);
        authenticateAs("f6666666-6666-6666-6666-666666666666");
        doFilter(filter, new MockHttpServletRequest("POST", HR_MESSAGES_PATH), new MockHttpServletResponse());
        doFilter(filter, new MockHttpServletRequest("POST", "/api/candidates/resumes"), new MockHttpServletResponse());
        MockHttpServletResponse messageExhausted = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", HR_MESSAGES_PATH), messageExhausted);
        assertThat(messageExhausted.getStatus()).isEqualTo(429);
        MockHttpServletResponse llmExhausted = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", "/api/candidates/resumes"), llmExhausted);
        assertThat(llmExhausted.getStatus()).isEqualTo(429);

        MockHttpServletResponse aiDraft = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", HR_AI_DRAFT_PATH), aiDraft);
        assertThat(aiDraft.getStatus()).as("nhom ai-sync").isEqualTo(200);
    }

    // M2 (POST .../messages) van thuoc nhom message, khong roi vao ai-sync: het luot message thi 429 du ai-sync con
    // nguyen.
    @Test
    void doFilter_messageSendPath_staysInMessageGroup_notAiSync() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 1, 1, 20, 20);
        authenticateAs("f7777777-7777-7777-7777-777777777777");
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_MESSAGES_PATH), new MockHttpServletResponse());
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        doFilter(filter, new MockHttpServletRequest("POST", CANDIDATE_MESSAGES_PATH), rejected);
        assertThat(rejected.getStatus()).isEqualTo(429);
    }

    @Test
    void doFilter_aiDraftWithoutAuthentication_passesThroughUnaffected() throws Exception {
        RateLimitFilter filter = newFilter(20, 20, 20, 20, 20, 20, 1, 1);
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            doFilter(filter, new MockHttpServletRequest("POST", HR_AI_DRAFT_PATH), response);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
