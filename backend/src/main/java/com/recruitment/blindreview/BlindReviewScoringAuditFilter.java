package com.recruitment.blindreview;

import com.recruitment.scoring.ScoringAuditTextFilter;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

// FR-H16 L4 - cai dat ScoringAuditTextFilter cho audit cham diem (/api/hr/candidates/{id}/audit/scoring-runs): don dang
// an danh (BlindReviewResolver) -> che bang IdentityMasker cua chinh don do (R-M4); khong an danh -> nguyen van, khong
// chay cau nap gia tri danh tinh nao.
@Component
public class BlindReviewScoringAuditFilter implements ScoringAuditTextFilter {

    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public BlindReviewScoringAuditFilter(BlindReviewResolver blindReviewResolver, IdentityMaskSource identityMaskSource) {
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
    }

    @Override
    public UnaryOperator<String> forApplication(UUID applicationId) {
        if (!blindReviewResolver.isMasked(applicationId)) {
            return UnaryOperator.identity();
        }
        IdentityMasker masker = identityMaskSource.load(applicationId);
        return masker::mask;
    }
}
