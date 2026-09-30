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

    private RateLimitFilter newFilter(long authCapacity, long authRefill, long llmCapacity, long llmRefill) {
        return new RateLimitFilter(
                new RateLimitBucketStore(1000, new FixedTimeMeter()), authCapacity, authRefill, llmCapacity, llmRefill);
    }

    private void authenticateAs(String userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
    }

    // Request thu N+1 (N=capacity) tren CUNG mot IP toi /api/auth/login -> 429 kem header Retry-After.
    @Test
    void doFilter_loginRequestExceedingCapacity_returns429WithRetryAfterHeader() throws Exception {
        RateLimitFilter filter = newFilter(3, 3, 20, 20);
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
        RateLimitFilter filter = newFilter(3, 3, 20, 20);
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
        RateLimitFilter filter = newFilter(20, 20, 2, 2);
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
        RateLimitFilter filter = newFilter(20, 20, 1, 1);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/candidates/resumes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        doFilter(filter, request, response);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // GET toi cung duong dan KHONG bi rate limit - chi POST toi 5 endpoint da duyet moi bi ap.
    @Test
    void doFilter_getRequestToLoginPath_notRateLimited() throws Exception {
        RateLimitFilter filter = newFilter(1, 1, 20, 20);
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
        RateLimitFilter filter = newFilter(1, 1, 1, 1);
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
        RateLimitFilter filter = newFilter(20, 20, 2, 2);
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
        RateLimitFilter filter = newFilter(20, 20, 1, 1);
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
}
