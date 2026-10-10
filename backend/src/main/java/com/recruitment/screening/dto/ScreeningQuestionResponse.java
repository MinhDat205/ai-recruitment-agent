package com.recruitment.screening.dto;

import com.recruitment.screening.ScreeningQuestionType;
import java.util.List;
import java.util.UUID;

// options rong (khong null) voi SHORT_TEXT/YES_NO (CLAUDE.md §4). Ten "options", khong phai "labels" (CLAUDE.md §7).
public record ScreeningQuestionResponse(
        UUID id,
        ScreeningQuestionType questionType,
        String questionText,
        List<String> options,
        boolean required,
        int displayOrder) {

    public ScreeningQuestionResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
