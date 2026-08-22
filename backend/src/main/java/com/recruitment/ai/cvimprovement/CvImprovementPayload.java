package com.recruitment.ai.cvimprovement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Schema co dinh cho output LLM khi sinh goi y cai thien CV (F2, FR-U05) - dung BeanOutputConverter,
// khong Map tu do. @JsonIgnoreProperties: LLM co the them field thua ngoai y muon, khong duoc lam vo
// deserialize. Mau y het ScoreExplanationPayload (ai/explanation).
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvImprovementPayload(
        List<String> missingKeywords, List<SectionSuggestion> sectionSuggestions, List<LearningPathItem> learningPath) {

    public CvImprovementPayload {
        missingKeywords = missingKeywords == null ? List.of() : missingKeywords;
        sectionSuggestions = sectionSuggestions == null ? List.of() : sectionSuggestions;
        learningPath = learningPath == null ? List.of() : learningPath;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectionSuggestion(String section, String suggestion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LearningPathItem(String topic, String reason) {
    }
}
