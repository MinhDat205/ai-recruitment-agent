package com.recruitment.screening.dto;

import java.util.List;
import java.util.UUID;

// FR-U09 F1 - bo cau hoi dang hoat dong cua tin + revision (R-F1). questions dung lai ScreeningQuestionResponse cua
// FR-H10 (khong field nao chi danh cho HR); KHONG co maxQuestions (khai niem phia HR).
public record ScreeningFormResponse(UUID jobId, int revision, List<ScreeningQuestionResponse> questions) {

    public ScreeningFormResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
