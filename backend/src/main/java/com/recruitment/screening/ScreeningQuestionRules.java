package com.recruitment.screening;

import com.recruitment.common.exception.InvalidScreeningQuestionException;
import com.recruitment.common.exception.ScreeningQuestionViolation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// FR-H10 R-Q3, R-Q4, R-Q4b - quy tac kiem MOT cau hoi sang loc, tach khoi ScreeningQuestionOwnerService (FR-H11 Q15)
// de FR khac dung lai ma khong chep so 10/300/100: validator goi y AI cua FR-H11 (R-Q2), kiem truoc transaction cua J2
// (R-L4). Lop THUAN, khong Spring, khong repository. Hanh vi giu nguyen tung buoc cua normalize cu.
public final class ScreeningQuestionRules {

    public static final int MAX_QUESTIONS = 10;
    public static final int MAX_QUESTION_TEXT_LENGTH = 300;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;
    public static final int MAX_OPTION_LENGTH = 100;

    private ScreeningQuestionRules() {
    }

    // Kiem null TRUOC khi trim (khong bao gio NullPointerException), trim TRUOC khi do do dai, kiem theo dung thu tu
    // bang R-Q4b va dung o loi dau tien. Do dai dem theo code point de khop VARCHAR cua Postgres (String.length() dem
    // UTF-16, ky tu ngoai BMP bi dem 2). options null duoc coi nhu rong; type null di nhanh "khong phai SINGLE_CHOICE"
    // (noi goi tu chan type null truoc - FR-H10 bang @NotNull, FR-H11 bang validator rieng).
    public static NormalizedQuestion normalize(ScreeningQuestionType type, String questionText, List<String> rawOptions) {
        String text = questionText == null ? "" : questionText.strip();
        if (text.isEmpty()) {
            throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.QUESTION_TEXT_EMPTY);
        }
        if (codePoints(text) > MAX_QUESTION_TEXT_LENGTH) {
            throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.QUESTION_TEXT_TOO_LONG);
        }

        List<String> raw = rawOptions == null ? List.of() : rawOptions;
        List<String> options = null;
        if (type == ScreeningQuestionType.SINGLE_CHOICE) {
            if (raw.size() < MIN_OPTIONS || raw.size() > MAX_OPTIONS) {
                throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_COUNT);
            }
            options = new ArrayList<>(raw.size());
            for (String value : raw) {
                String option = value == null ? "" : value.strip();
                if (option.isEmpty()) {
                    throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_EMPTY);
                }
                options.add(option);
            }
            for (String option : options) {
                if (codePoints(option) > MAX_OPTION_LENGTH) {
                    throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_TOO_LONG);
                }
            }
            Set<String> seen = new HashSet<>();
            for (String option : options) {
                if (!seen.add(option.toLowerCase(Locale.ROOT))) {
                    throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTION_DUPLICATE);
                }
            }
        } else if (!raw.isEmpty()) {
            throw new InvalidScreeningQuestionException(ScreeningQuestionViolation.OPTIONS_NOT_ALLOWED);
        }
        return new NormalizedQuestion(type, text, options);
    }

    public static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    // options = null voi SHORT_TEXT/YES_NO (khop chk_screening_question_options).
    public record NormalizedQuestion(ScreeningQuestionType type, String text, List<String> options) {
    }
}
