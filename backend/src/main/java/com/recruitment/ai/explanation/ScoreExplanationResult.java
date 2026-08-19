package com.recruitment.ai.explanation;

public record ScoreExplanationResult(ScoreExplanationPayload payload, String model, Integer tokenUsage, String promptVersion) {
}
