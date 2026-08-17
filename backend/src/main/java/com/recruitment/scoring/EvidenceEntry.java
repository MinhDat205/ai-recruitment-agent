package com.recruitment.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Khop dung cau truc JSONB da mo ta trong V1__init_schema.sql (criterion_scores.evidence):
// [{ "quote": "...", "section": "..." }, ...]. section phai la mot trong 6 khoi cua schema CV D1
// (contact/education/experience/skills/certifications/projects) - kiem tra gia tri nay la viec cua
// CriterionScoringService (Dot 3), khong phai record nay.
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenceEntry(String quote, String section) {
}
