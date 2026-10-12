package com.recruitment.ai.jobdraft;

import java.util.List;

// FR-H11 R-C1 - schema JSON co dinh cua output AI phan CRITERIA: chi ten + mo ta. KHONG trong so, diem toi da, thang
// diem, muc uu tien (R-C3): model tu them "weight" thi khoa do bi bo qua khi parse, khong co cho chua.
public record CriteriaDraftPayload(List<CriterionDraft> criteria) {

    public CriteriaDraftPayload {
        criteria = criteria == null ? List.of() : criteria;
    }

    public record CriterionDraft(String name, String description) {
    }
}
