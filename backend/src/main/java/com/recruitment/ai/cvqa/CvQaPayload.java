package com.recruitment.ai.cvqa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// ai/cvqa/ - output tho cua LLM. Field so/boolean dung wrapper (CLAUDE.md muc 4).
// FR-C08 R-O1 - schema co dinh cho BeanOutputConverter (constructor 1 tham so). Kiem R-O2/R-O3 o
// CvQaService.isValidPayload. Annotation Jackson o package com.fasterxml la DUNG (CLAUDE.md muc 3b).
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvQaPayload(String answer, List<Citation> citations, Boolean notFound, String refused) {

    public CvQaPayload {
        citations = citations == null ? List.of() : citations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Citation(String quote) {
    }
}
