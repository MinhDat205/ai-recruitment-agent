package com.recruitment.ai.embedding;

// vector la float[] (khong phai wrapper) - day KHONG phai schema JSON parse tu output LLM (quy uoc
// wrapper cho field so o CLAUDE.md chi ap dung cho loai do), chi la DTO noi bo mang ket qua tho ra
// khoi tang goi EmbeddingModel.
public record EmbeddingResult(float[] vector, String model) {
}
