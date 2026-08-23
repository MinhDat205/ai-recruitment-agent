package com.recruitment.common;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Cau hinh dung CHUNG cho co che retry-with-backoff cua loi LLM tam thoi (chore/hardening Dot 4,
// dung boi ca ResumeParsingStateService (D1) va ScoringRunStateService (D2), Dot 4e - nhip sau).
// Dat o common/ (khong phai resume/ hay scoring/), khac voi extractStatusCode (D1/D2) co CHU DICH
// giu rieng vi moi noi doc mot loai exception SDK khac nhau (Anthropic o D1/D2, OpenAI o F1) - o
// day ca D1 va D2 dung CHUNG dung mot bo gia tri cau hinh (app.hardening.llm.*), khong co ly do
// tach rieng hai ban gan giong het nhau: mot cho validate() la du, khong phai hai.
@Component
public class LlmRetryPolicy {

    private final int maxAttempts;
    private final List<Long> backoffMs;

    public LlmRetryPolicy(
            @Value("${app.hardening.llm.max-attempts}") int maxAttempts,
            @Value("${app.hardening.llm.backoff-ms}") List<Long> backoffMs) {
        // Validate NGAY luc khoi dong (constructor cua mot @Component chay khi Spring tao bean) -
        // KHONG de sai cau hinh am tham gay IndexOutOfBoundsException luc runtime that (Dot 4e).
        // Dung max-attempts - 1 (KHONG PHAI max-attempts): lan thu CUOI cung that bai la FAILED
        // ngay, khong co lan cho backoff nao sau no.
        if (backoffMs.size() != maxAttempts - 1) {
            throw new IllegalStateException(
                    "app.hardening.llm.backoff-ms phai co dung " + (maxAttempts - 1)
                            + " phan tu (= max-attempts - 1, max-attempts=" + maxAttempts + "), hien co "
                            + backoffMs.size() + " phan tu: " + backoffMs);
        }
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    // attemptNumber la so thu tu lan thu VUA that bai (1-based, vd lan dau that bai truyen 1) -
    // tra ve so mili giay cho truoc khi thu lai. Chi goi khi attemptNumber < maxAttempts (con luot
    // thu) - StateService (Dot 4e) chiu trach nhiem kiem dieu kien do truoc khi goi ham nay.
    public long backoffMillisAfterAttempt(int attemptNumber) {
        return backoffMs.get(attemptNumber - 1);
    }
}
