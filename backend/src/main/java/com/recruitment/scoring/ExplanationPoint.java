package com.recruitment.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Khop dung cau truc JSONB cua score_explanations.strengths/weaknesses (V1):
// [{ "criterionName": "...", "point": "..." }, ...]. criterionName PHAI thuoc tap ten tieu chi da
// gui cho LLM (kiem o ScoreExplanationService, Dot 2) - moi luan diem dan ve dung MOT tieu chi cu
// the (Q3, ke hoach D4), khong phai nhan xet chung chung khong gan voi tieu chi nao. Mau EvidenceEntry
// (Dot 3, ke hoach D2/D3).
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExplanationPoint(String criterionName, String point) {
}
