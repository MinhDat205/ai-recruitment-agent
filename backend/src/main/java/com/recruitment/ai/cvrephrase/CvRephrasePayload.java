package com.recruitment.ai.cvrephrase;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// ai/cvrephrase/ - output tho cua LLM (FR-U12 muc 5). Schema co dinh cho BeanOutputConverter (constructor 1 tham so).
// Kiem R-A4 o CvRephraseValidator. Annotation Jackson o package com.fasterxml la DUNG (CLAUDE.md muc 3b).
@JsonIgnoreProperties(ignoreUnknown = true)
public record CvRephrasePayload(String suggestion) {
}
