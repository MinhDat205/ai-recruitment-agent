package com.recruitment.ai.criterion;

public record CriterionScoringResult(CriterionScorePayload payload, String model, Integer tokenUsage, String promptVersion) {
}
