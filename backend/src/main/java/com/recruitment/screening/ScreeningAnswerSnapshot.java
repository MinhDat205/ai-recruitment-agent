package com.recruitment.screening;

import java.util.List;
import java.util.UUID;

// FR-H14 (REQUIREMENT muc 4.2) - ban doc mot dong ban chup sang loc CO questionId, de bang so sanh ghep hang theo
// question_id + noi dung (R-Q1). CHI dung noi bo backend: questionId KHONG ra API (giu quyet dinh FR-H10 muc 4.4 -
// ApplicationScreeningResponse.Item khong co questionId). questionId null = cau goc da bi xoa (ON DELETE SET NULL).
// Chi chep cot *_snapshot (R-S3 cua FR-H10), khong doc lai cau hoi goc.
public record ScreeningAnswerSnapshot(
        UUID questionId,
        int displayOrder,
        ScreeningQuestionType questionType,
        String questionText,
        List<String> options,
        boolean required,
        String answerText,
        Boolean answerYesNo) {

    static ScreeningAnswerSnapshot from(ApplicationScreeningAnswer row) {
        return new ScreeningAnswerSnapshot(
                row.getQuestionId(),
                row.getDisplayOrder(),
                row.getQuestionTypeSnapshot(),
                row.getQuestionTextSnapshot(),
                row.getOptionsSnapshot(),
                row.isRequiredSnapshot(),
                row.getAnswerText(),
                row.getAnswerYesNo());
    }
}
