package com.recruitment.ai.jobdraft;

import com.recruitment.screening.ScreeningQuestionType;
import java.util.List;

// FR-H11 R-Q1 - schema JSON co dinh cua output AI phan QUESTIONS. KHONG required (HR tu quyet), khong dap an mong muon.
// questionType ngoai 3 gia tri cua FR-H10 -> parse hong -> output hong (R-Q2). List null -> List.of(); KHONG
// List.copyOf vi phan tu null phai den duoc validator (tra false) thay vi NullPointerException luc parse.
public record QuestionsDraftPayload(List<QuestionDraft> questions) {

    public QuestionsDraftPayload {
        questions = questions == null ? List.of() : questions;
    }

    public record QuestionDraft(ScreeningQuestionType questionType, String questionText, List<String> options) {

        public QuestionDraft {
            options = options == null ? List.of() : options;
        }
    }
}
