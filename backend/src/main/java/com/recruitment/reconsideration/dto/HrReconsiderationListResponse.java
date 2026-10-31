package com.recruitment.reconsideration.dto;

import java.util.List;
import java.util.UUID;

// FR-C09 H1 - openCount dem yeu cau OPEN cua tin, KHONG phu thuoc bo loc; items theo requested_at ASC, application_id ASC.
public record HrReconsiderationListResponse(UUID jobId, int openCount, List<HrReconsiderationResponse> items) {

    public HrReconsiderationListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
