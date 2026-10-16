package com.recruitment.talentpool.dto;

// FR-H15 P2, P4. Khong @Valid: chuan hoa va kiem do dai o TalentPoolNameRules (R-K2), null -> INVALID_TALENT_POOL_NAME.
public record TalentPoolNameRequest(String name) {
}
