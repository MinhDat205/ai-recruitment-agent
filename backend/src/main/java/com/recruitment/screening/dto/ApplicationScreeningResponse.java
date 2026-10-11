package com.recruitment.screening.dto;

import com.recruitment.screening.ScreeningCaptureState;
import com.recruitment.screening.ScreeningQuestionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FR-H10 S6 - ban chup cau tra loi cua mot don. capturedAt null khi NOT_CAPTURED; items rong tru khi CAPTURED (R-A3).
// Item khong co questionId (muc 4.4) va khong co field verdict/label/isQualified/passed/recommendation (CLAUDE.md §7).
public record ApplicationScreeningResponse(
        UUID applicationId, ScreeningCaptureState state, Instant capturedAt, List<Item> items) {

    public ApplicationScreeningResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    // answerText: SHORT_TEXT hoac chu cua phuong an da chon; null = khong tra loi. answerYesNo: YES_NO; null = khong
    // tra loi. options rong (khong null) voi SHORT_TEXT/YES_NO.
    public record Item(
            int displayOrder,
            ScreeningQuestionType questionType,
            String questionText,
            List<String> options,
            boolean required,
            String answerText,
            Boolean answerYesNo) {

        public Item {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
