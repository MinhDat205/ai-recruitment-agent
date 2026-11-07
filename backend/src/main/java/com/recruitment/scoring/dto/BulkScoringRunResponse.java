package com.recruitment.scoring.dto;

import com.recruitment.scoring.BulkScoringSkipReason;
import java.util.List;
import java.util.UUID;

// FR-H04 mo rong (REQUIREMENT muc 4.3) - ban tom tat cua B1, khong tra id luot da tao, ten hay truong danh tinh nao
// (R-G3). created/remaining la int: luon co gia tri, 0 la 0 that.
//
// remaining: so don du dieu kien KHONG duoc thu vi vuot tran (R-C1, R-C2). skipped: don trong tap xet R-E1 khong duoc
// tao luot, sap applied_at ASC (R-E6); don chu ky cu va don vuot tran khong nam o day.
public record BulkScoringRunResponse(int created, int remaining, List<SkippedApplication> skipped) {

    public BulkScoringRunResponse {
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    public record SkippedApplication(UUID applicationId, BulkScoringSkipReason reason) {
    }
}
