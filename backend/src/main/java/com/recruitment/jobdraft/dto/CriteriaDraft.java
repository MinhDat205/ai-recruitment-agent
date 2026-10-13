package com.recruitment.jobdraft.dto;

import java.util.List;

// FR-H11 phan CRITERIA (muc 4.4). KHONG trong so (R-C3, R-W1).
public record CriteriaDraft(List<CriterionSuggestion> criteria) {

    public record CriterionSuggestion(String name, String description) {
    }
}
