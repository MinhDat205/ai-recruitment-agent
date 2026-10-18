package com.recruitment.blindreview;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

// FR-H16 R-S1, R-S2 - noi DUY NHAT tra loi "don nay co dang an danh khong". Moi service HR goi lop nay, khong viet dieu
// kien thu hai. Tinh tai moi request, khong cache (R-S4). Khong @Transactional: doc thuan, dung duoc trong transaction
// cua noi goi (vd gui giay moi - R-I2) lan ngoai transaction (facade C07/C08 khong mo transaction).
@Component
public class BlindReviewResolver {

    private final JobBlindReviewChangeRepository repository;

    public BlindReviewResolver(JobBlindReviewChangeRepository repository) {
        this.repository = repository;
    }

    // Ban mot don: chay dung cau SQL cua ban theo tap.
    public boolean isMasked(UUID applicationId) {
        return !maskedAmong(Set.of(applicationId)).isEmpty();
    }

    // Ban theo tap id: MOT cau SQL cho ca danh sach/trang - man danh sach goi ham nay, khong goi isMasked trong vong lap.
    public Set<UUID> maskedAmong(Collection<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findMaskedApplicationIds(Set.copyOf(applicationIds)));
    }
}
