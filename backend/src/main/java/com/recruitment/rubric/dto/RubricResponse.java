package com.recruitment.rubric.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RubricResponse(
        UUID id, UUID jobId, String name, boolean locked, BigDecimal totalWeight, List<RubricCriterionResponse> criteria) {
}
