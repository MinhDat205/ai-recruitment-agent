package com.recruitment.talentpool.dto;

import com.recruitment.jobapplication.TalentPoolConsentState;
import java.util.List;
import java.util.UUID;

// FR-H15 P6, P7. consentState la trang thai U09; pools = MOI kho chua xoa cua cong ty, sap theo R-K7. contains loc dong
// y (R-M6): don khong ACTIVE luon contains = false.
public record ApplicationTalentPoolsResponse(TalentPoolConsentState consentState, List<PoolMembership> pools) {

    public ApplicationTalentPoolsResponse {
        pools = pools == null ? List.of() : List.copyOf(pools);
    }

    public record PoolMembership(UUID id, String name, boolean contains) {
    }
}
