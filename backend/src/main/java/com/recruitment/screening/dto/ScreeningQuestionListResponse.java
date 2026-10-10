package com.recruitment.screening.dto;

import java.util.List;
import java.util.UUID;

// FR-H10 S1/S5. maxQuestions de frontend khong tu hardcode 10 (muc 4.4). revision = jobs.screening_revision (R-Q11).
public record ScreeningQuestionListResponse(
        UUID jobId,
        int revision,
        int maxQuestions,
        List<ScreeningQuestionResponse> questions) {

    public ScreeningQuestionListResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
