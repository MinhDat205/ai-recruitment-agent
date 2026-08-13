package com.recruitment.rubric.dto;

import com.recruitment.rubric.ScaleLevelDescription;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RubricCriterionResponse(
        UUID id,
        UUID rubricId,
        String name,
        String description,
        List<ScaleLevelDescription> scaleDescription,
        BigDecimal weight,
        int maxScore,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt) {
}
