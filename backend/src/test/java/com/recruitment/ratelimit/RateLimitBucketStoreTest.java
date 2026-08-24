package com.recruitment.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

// Test THUAN Java, khong can Spring context - RateLimitBucketStore khong phu thuoc gi ngoai
// bucket4j-core. Dung dong ho gia (MutableTimeMeter) de kiem refill DETERMINISTIC, KHONG
// Thread.sleep that (nhanh Dot 5 nay vua bo mot cho Thread.sleep o Dot 4, khong them lai - xem
// CLAUDE.md/bao cao Dot 4).
class RateLimitBucketStoreTest {

    // TimeMeter gia dieu khien bang tay - interface va constructor xac nhan qua javap that tren
    // bucket4j-core-8.10.1.jar (khong doan theo tai lieu, CLAUDE.md muc 3b): currentTimeNanos(),
    // isWallClockBased(), inject qua LocalBucketBuilder.withCustomTimePrecision(TimeMeter).
    private static final class MutableTimeMeter implements TimeMeter {
        private long nanos;

        private MutableTimeMeter(long startNanos) {
            this.nanos = startNanos;
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        private void advance(long amount, TimeUnit unit) {
            nanos += unit.toNanos(amount);
        }
    }

    @Test
    void tryConsume_withinCapacity_consumesSuccessfully() {
        RateLimitBucketStore store = new RateLimitBucketStore(100, new MutableTimeMeter(0));

        ConsumptionProbe probe = store.tryConsume("key-a", 3, 3);

        assertThat(probe.isConsumed()).isTrue();
    }

    // Bien: dung DUNG capacity (request thu N, o day N=3) van thanh cong.
    @Test
    void tryConsume_exactlyAtCapacity_stillConsumesSuccessfully() {
        RateLimitBucketStore store = new RateLimitBucketStore(100, new MutableTimeMeter(0));

        for (int i = 0; i < 3; i++) {
            assertThat(store.tryConsume("key-boundary", 3, 3).isConsumed())
                    .as("request thu %d trong %d", i + 1, 3)
                    .isTrue();
        }
    }

    // Bien: request thu N+1 (thu 4, vuot capacity=3) bi tu choi, kem so nano-giay phai cho > 0 -
    // day la gia tri RateLimitFilter dung de tinh header Retry-After.
    @Test
    void tryConsume_requestNPlusOne_rejectsWithPositiveNanosToWaitForRefill() {
        RateLimitBucketStore store = new RateLimitBucketStore(100, new MutableTimeMeter(0));
        for (int i = 0; i < 3; i++) {
            store.tryConsume("key-b", 3, 3);
        }

        ConsumptionProbe rejected = store.tryConsume("key-b", 3, 3);

        assertThat(rejected.isConsumed()).isFalse();
        assertThat(rejected.getNanosToWaitForRefill()).isGreaterThan(0);
    }

    // Mot key (vd mot IP/user) da het han muc KHONG duoc anh huong toi key khac - dung yeu cau cua
    // Dot 5 "IP/user khac khong bi anh huong".
    @Test
    void tryConsume_differentKeys_areIndependent() {
        RateLimitBucketStore store = new RateLimitBucketStore(100, new MutableTimeMeter(0));
        for (int i = 0; i < 3; i++) {
            store.tryConsume("key-c", 3, 3);
        }

        ConsumptionProbe otherKey = store.tryConsume("key-d", 3, 3);

        assertThat(otherKey.isConsumed()).isTrue();
    }

    // Refill dung DONG HO GIA - tien len dung 1 phut (bang chu ky refill da cau hinh), xac nhan
    // token duoc cap lai MA KHONG can cho that.
    @Test
    void tryConsume_afterFakeClockAdvancesPastRefillPeriod_allowsConsumptionAgain() {
        MutableTimeMeter clock = new MutableTimeMeter(0);
        RateLimitBucketStore store = new RateLimitBucketStore(100, clock);
        for (int i = 0; i < 3; i++) {
            store.tryConsume("key-e", 3, 3);
        }
        assertThat(store.tryConsume("key-e", 3, 3).isConsumed()).isFalse();

        clock.advance(1, TimeUnit.MINUTES);

        assertThat(store.tryConsume("key-e", 3, 3).isConsumed()).isTrue();
    }

    // Doi chung: dong ho gia HAU NHU KHONG tien (1 milli-giay) - van phai bi tu choi. Luu y:
    // refillGreedy la refill LIEN TUC MUOT (nho giot deu trong ca chu ky), KHONG PHAI "cap lai
    // theo mot lo duy nhat cuoi chu ky" (do la refillIntervally, khac ham) - voi capacity=3/60s,
    // tien 30s (nua chu ky) THAT SU da du nho giot du 1 token moi (~20s/token), nen KHONG dung 30s
    // lam moc "chua du" duoc. Dung 1ms de chac chan 0 token da nho vao, bat ke ty le refill nao.
    @Test
    void tryConsume_fakeClockBarelyAdvances_stillRejected() {
        MutableTimeMeter clock = new MutableTimeMeter(0);
        RateLimitBucketStore store = new RateLimitBucketStore(100, clock);
        for (int i = 0; i < 3; i++) {
            store.tryConsume("key-f", 3, 3);
        }

        clock.advance(1, TimeUnit.MILLISECONDS);

        assertThat(store.tryConsume("key-f", 3, 3).isConsumed()).isFalse();
    }

    // Tran max-tracked-keys: tao qua tran so khoa, xac nhan map KHONG VUOT kich thuoc cau hinh va
    // khoa CU NHAT (khong duoc dung gan day nhat) bi loai TRUOC - accessOrder=true cua LinkedHashMap.
    @Test
    void tryConsume_exceedingMaxTrackedKeys_evictsLeastRecentlyUsedKeyFirst() {
        RateLimitBucketStore store = new RateLimitBucketStore(3, new MutableTimeMeter(0));
        store.tryConsume("key-1", 10, 10);
        store.tryConsume("key-2", 10, 10);
        store.tryConsume("key-3", 10, 10);
        assertThat(store.trackedKeyCount()).isEqualTo(3);

        // Truy cap lai key-1 de dua no len "gan day nhat" - key-2 gio la khoa CU NHAT trong map.
        store.tryConsume("key-1", 10, 10);
        store.tryConsume("key-4", 10, 10);

        assertThat(store.trackedKeyCount()).isEqualTo(3);
        assertThat(store.isTracked("key-2")).as("key-2 la cu nhat, phai bi loai").isFalse();
        assertThat(store.isTracked("key-1")).isTrue();
        assertThat(store.isTracked("key-3")).isTrue();
        assertThat(store.isTracked("key-4")).isTrue();
    }
}
