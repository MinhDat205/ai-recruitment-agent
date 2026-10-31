package com.recruitment.reconsideration.dto;

import com.recruitment.reconsideration.ReconsiderationStatus;
import java.time.Instant;
import java.util.UUID;

// FR-C09 muc 4.4 - mot yeu cau phia HR: khoi o E1 cua FR-H09, phan tu cua H1, response H2. Don dang an danh (R-A2):
// candidateName la nhan CandidateAlias, reason va hrResponse DA CHE - DTO khong co thanh phan nao chua van ban chua che
// (R-A2a). Khong co decidedBy.
public record HrReconsiderationResponse(
        UUID applicationId,
        String candidateName,
        boolean identityMasked,
        Instant appliedAt,
        Instant rejectedAt, // changed_at cua dong PENDING -> REJECTED (B2)
        Instant requestedAt,
        String reason, // da che khi an danh; null khi ung vien khong nhap
        ReconsiderationStatus status,
        Instant decidedAt,
        String hrResponse) {} // chi khi DECLINED; da che khi an danh
