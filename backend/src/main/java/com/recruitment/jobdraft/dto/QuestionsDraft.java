package com.recruitment.jobdraft.dto;

import com.recruitment.screening.ScreeningQuestionType;
import java.util.List;

// FR-H11 phan QUESTIONS (muc 4.4). KHONG required (HR tu quyet - R-Q1). options rong voi SHORT_TEXT/YES_NO.
public record QuestionsDraft(List<QuestionSuggestion> questions) {

    public record QuestionSuggestion(ScreeningQuestionType questionType, String questionText, List<String> options) {
    }
}
