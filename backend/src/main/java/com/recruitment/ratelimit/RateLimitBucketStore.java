package com.recruitment.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Dot 5 (chore/hardening) - kho bucket CO CHAN, khong dung ConcurrentHashMap tran: mot map khong
// tran cho phep ke tan cong gia nhieu IP/user khac nhau de lam phinh bo nho vo han, tu no thanh mot
// duong DoS nguoc lai dung muc dich cua rate limit. LinkedHashMap truy cap-theo-thu-tu
// (accessOrder=true, tham so thu ba cua constructor) tu dua khoa VUA DUNG len cuoi danh sach,
// removeEldestEntry() tu loai khoa LAU KHONG DUNG NHAT khi vuot tran maxTrackedKeys - khong can
// @Scheduled don dep rieng. Collections.synchronizedMap boc ngoai vi computeIfAbsent (dung trong
// tryConsume) la thao tac doc-roi-ghi (khong an toan neu nhieu luong goi dong thoi ma khong khoa) -
// Collections.SynchronizedMap ghi de ca cac default method nhu computeIfAbsent (khong chi rieng
// get/put) de dam bao moi loi goi la MOT khoi nguyen tu.
//
// GIOI HAN DA BIET (co chu dich, ghi ROADMAP): tran nay chan duoc OOM nhung khong chan duoc ke tan
// cong tao qua 10.000 khoa gia (IP gia hoac nhieu tai khoan) de day khoa CUA CHINH NO ra khoi map -
// moi lan mot bucket bi evict, han muc cua khoa do coi nhu reset ve day. Day la danh doi co chu dich
// cua rate limit in-memory khong co backend chia se (Redis...) o quy mo mot instance.
public class RateLimitBucketStore {

    private final Map<String, Bucket> buckets;
    private final TimeMeter timeMeter;

    public RateLimitBucketStore(int maxTrackedKeys) {
        this(maxTrackedKeys, TimeMeter.SYSTEM_MILLISECONDS);
    }

    // Goi rieng cho test (RateLimitBucketStoreTest/RateLimitFilterTest) - dong bo MOT dong ho gia
    // vao TAT CA bucket ma instance nay tao ra, kiem refill deterministic KHONG Thread.sleep that.
    RateLimitBucketStore(int maxTrackedKeys, TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
        this.buckets = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxTrackedKeys;
            }
        });
    }

    // Tieu thu 1 token cho key nay, tao bucket moi (theo dung capacity/refillTokensPerMinute cua
    // LAN GOI DAU TIEN cho key do) neu chua ton tai. capacity/refillTokensPerMinute doc tu
    // application.yml o tang goi (RateLimitFilter), khong hardcode o day.
    public ConsumptionProbe tryConsume(String key, long capacity, long refillTokensPerMinute) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, refillTokensPerMinute));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    // Goi rieng cho test - xac nhan tran hoat dong dung (khong vuot kich thuoc, khoa cu nhat bi
    // loai truoc).
    int trackedKeyCount() {
        return buckets.size();
    }

    boolean isTracked(String key) {
        return buckets.containsKey(key);
    }

    // Bandwidth.classic(long, Refill)/Refill.greedy(...) da deprecated trong 8.10.1 (xac nhan qua
    // javap that, khong doan) - dung API staged moi Bandwidth.builder()...refillGreedy(...).
    private Bucket newBucket(long capacity, long refillTokensPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokensPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).withCustomTimePrecision(timeMeter).build();
    }
}
