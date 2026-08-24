package com.recruitment.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

// Dot 5 (chore/hardening) - dat SAU JwtAuthenticationFilter trong SecurityConfig
// (addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)) - MOT vi tri duy nhat du cho ca
// hai nhom: nhom (a) (login/register) la permitAll nen JwtAuthenticationFilter cho qua khong can
// token, filter nay van chan duoc TRUOC khi toi controller; nhom (b) (endpoint LLM) doc duoc
// Authentication/JWT subject binh thuong vi da qua JwtAuthenticationFilter truoc do trong chain.
//
// Tat qua app.rate-limit.enabled=false trong application-test.yml (mau ResumeParsingScheduler/
// app.resume-parsing.enabled da co san) - LY DO: RateLimitBucketStore la MOT map trong-nho DUY NHAT
// cho toan bo Spring context; hang chuc @SpringBootTest khac (dung chung context cache) goi that
// POST /api/auth/login qua MockMvc de lay token, TONG CONG hang tram lan trong ca bo test - neu bat
// len se lam vo hang loat test khong lien quan gi den tinh nang nay chi vi dung chung bucket. Test
// rieng cho filter nay (RateLimitFilterTest) va cho kho bucket (RateLimitBucketStoreTest) khong can
// Spring context - tu "new" instance bang tay voi MockHttpServletRequest/Response.
@Component
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // Duong dan CHINH XAC, doc that tu controller (khong doan) - xem bao cao Dot 5 muc "Kiem truoc
    // khi code": AuthController (@RequestMapping("/api/auth") + @PostMapping("/login")/("/register/*")),
    // ResumeCandidateController (@RequestMapping("/api/candidates/resumes"), upload la @PostMapping
    // KHONG them path, improvement-suggestions la @PostMapping("/{id}/improvement-suggestions")),
    // ScoringRunHrController (@RequestMapping("/api/hr/applications/{applicationId}/scoring-runs"),
    // create la @PostMapping KHONG them path).
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATTERN = "/api/auth/register/**";
    private static final String RESUME_UPLOAD_PATH = "/api/candidates/resumes";
    private static final String SCORING_RUN_CREATE_PATTERN = "/api/hr/applications/*/scoring-runs";
    private static final String CV_IMPROVEMENT_PATTERN = "/api/candidates/resumes/*/improvement-suggestions";

    private final RateLimitBucketStore bucketStore;
    private final long authCapacity;
    private final long authRefillPerMinute;
    private final long llmActionCapacity;
    private final long llmActionRefillPerMinute;

    // @Autowired bat buoc: class nay co HAI constructor (constructor nay + constructor package-private
    // duoi day danh cho test) - thieu @Autowired, Spring khong tu chon duoc constructor nao va roi
    // xuong thu constructor KHONG THAM SO (khong ton tai), nem BeanInstantiationException luc khoi
    // dong that (phat hien khi kiem thu tay Dot 5, khong phai suy doan).
    @Autowired
    public RateLimitFilter(
            @Value("${app.rate-limit.max-tracked-keys:10000}") int maxTrackedKeys,
            @Value("${app.rate-limit.auth.capacity:10}") long authCapacity,
            @Value("${app.rate-limit.auth.refill-per-minute:10}") long authRefillPerMinute,
            @Value("${app.rate-limit.llm-action.capacity:20}") long llmActionCapacity,
            @Value("${app.rate-limit.llm-action.refill-per-minute:20}") long llmActionRefillPerMinute) {
        this(
                new RateLimitBucketStore(maxTrackedKeys),
                authCapacity,
                authRefillPerMinute,
                llmActionCapacity,
                llmActionRefillPerMinute);
    }

    // Goi rieng cho test - truyen thang RateLimitBucketStore da dung TimeMeter gia (xem
    // RateLimitBucketStoreTest) de kiem refill deterministic, khong Thread.sleep that.
    RateLimitFilter(
            RateLimitBucketStore bucketStore,
            long authCapacity,
            long authRefillPerMinute,
            long llmActionCapacity,
            long llmActionRefillPerMinute) {
        this.bucketStore = bucketStore;
        this.authCapacity = authCapacity;
        this.authRefillPerMinute = authRefillPerMinute;
        this.llmActionCapacity = llmActionCapacity;
        this.llmActionRefillPerMinute = llmActionRefillPerMinute;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitTarget target = classify(request);
        if (target == null) {
            filterChain.doFilter(request, response);
            return;
        }

        ConsumptionProbe probe = bucketStore.tryConsume(target.key(), target.capacity(), target.refillPerMinute());
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Lam tron LEN giay ke tiep (khong lam tron xuong) - client cho chua du se lai bi tu choi.
        long retryAfterSeconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write(
                        "{\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Bạn đã gửi quá nhiều yêu cầu, vui lòng thử"
                                + " lại sau ít phút\"}");
        // BAT BUOC: flush ngay de response duoc COMMIT truoc khi tra ve khoi filter nay - phat hien
        // qua kiem thu tay Dot 5 (khong phai suy doan): thieu flush, Spring Boot ErrorPageFilter
        // (dat NGOAI chain cua Spring Security, boc toan bo) thay status 429 nhung response CHUA
        // commit, tu dieu huong sang xu ly loi mac dinh va NUOT MAT body vua ghi (body ve tay client
        // rong, du log server cho thay da ghi). JsonAuthenticationEntryPoint/JsonAccessDeniedHandler
        // khong gap loi nay vi than 401/403 cua chung di qua duong Spring MVC binh thuong
        // (DispatcherServlet ghi qua HttpMessageConverter, tu commit truoc khi filter chain tra ve).
        response.flushBuffer();
    }

    // Chi ap dung cho DUNG 3+2 endpoint da duyet (POST). Moi request khac (bao gom GET toi cung
    // duong dan, hoac POST toi duong dan khac) tra null - khong bi rate limit.
    private RateLimitTarget classify(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();

        if (LOGIN_PATH.equals(path) || PATH_MATCHER.match(REGISTER_PATTERN, path)) {
            return new RateLimitTarget("auth:" + request.getRemoteAddr(), authCapacity, authRefillPerMinute);
        }

        if (RESUME_UPLOAD_PATH.equals(path)
                || PATH_MATCHER.match(SCORING_RUN_CREATE_PATTERN, path)
                || PATH_MATCHER.match(CV_IMPROVEMENT_PATTERN, path)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                // Chua xac thuc (thieu/sai token) - de FilterSecurityInterceptor (chay SAU filter
                // nay trong chain) tu tra 401/403, khong ap rate limit len mot request chac chan bi
                // tu choi vi ly do khac.
                return null;
            }
            return new RateLimitTarget(
                    "llm:" + authentication.getName(), llmActionCapacity, llmActionRefillPerMinute);
        }

        return null;
    }

    private record RateLimitTarget(String key, long capacity, long refillPerMinute) {
    }
}
