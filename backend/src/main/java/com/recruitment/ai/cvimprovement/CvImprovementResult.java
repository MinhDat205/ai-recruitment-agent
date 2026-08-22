package com.recruitment.ai.cvimprovement;

// KHONG co tokenUsage (khac ResumeParsingResult/ScoreExplanationResult) - cv_improvement_suggestions
// (V1) khong co cot token_usage, khac resume_parsed_data/scoring_runs/score_explanations. Khong bia
// them field khong co noi ghi xuong (CLAUDE.md: khong thiet ke cho yeu cau tuong lai gia dinh).
public record CvImprovementResult(CvImprovementPayload payload, String model, String promptVersion) {
}
