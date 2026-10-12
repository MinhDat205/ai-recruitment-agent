package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.common.exception.InvalidScreeningQuestionException;
import com.recruitment.common.exception.ScreeningQuestionViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

// FR-H11 T20 phan ScreeningQuestionRules (unit, khong Spring): lap lai cac bien T2/T3 cua FR-H10 o muc lop dung chung.
// Hanh vi qua HTTP cua FR-H10 van do ScreeningQuestionOwnerIntegrationTest kiem (khong sua).
class ScreeningQuestionRulesTest {

    private static final ScreeningQuestionType SINGLE = ScreeningQuestionType.SINGLE_CHOICE;

    private static ScreeningQuestionViolation violationOf(
            ScreeningQuestionType type, String text, List<String> options) {
        try {
            ScreeningQuestionRules.normalize(type, text, options);
        } catch (InvalidScreeningQuestionException e) {
            return e.violation();
        }
        throw new AssertionError("Mong doi InvalidScreeningQuestionException");
    }

    private static List<String> distinctOptions(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Phuong an " + i).toList();
    }

    @Test
    void limitsMatchFrH10() {
        assertThat(ScreeningQuestionRules.MAX_QUESTIONS).isEqualTo(10);
        assertThat(ScreeningQuestionRules.MAX_QUESTION_TEXT_LENGTH).isEqualTo(300);
        assertThat(ScreeningQuestionRules.MIN_OPTIONS).isEqualTo(2);
        assertThat(ScreeningQuestionRules.MAX_OPTIONS).isEqualTo(10);
        assertThat(ScreeningQuestionRules.MAX_OPTION_LENGTH).isEqualTo(100);
        assertThat(ScreeningQuestionOwnerService.MAX_QUESTIONS).isEqualTo(ScreeningQuestionRules.MAX_QUESTIONS);
    }

    // T2 - noi dung cau hoi
    @Test
    void questionText_nullEmptyOrBlank_rejectedAsEmpty() {
        for (String text : Arrays.asList(null, "", "   ", "\n\t ")) {
            assertThat(violationOf(ScreeningQuestionType.YES_NO, text, List.of()))
                    .isEqualTo(ScreeningQuestionViolation.QUESTION_TEXT_EMPTY);
        }
    }

    @Test
    void questionText_lengthBoundaries_afterStrip() {
        assertThat(ScreeningQuestionRules.normalize(ScreeningQuestionType.SHORT_TEXT, "a".repeat(299), List.of()).text())
                .hasSize(299);
        assertThat(ScreeningQuestionRules.normalize(ScreeningQuestionType.SHORT_TEXT, "a".repeat(300), List.of()).text())
                .hasSize(300);
        assertThat(ScreeningQuestionRules.normalize(
                        ScreeningQuestionType.SHORT_TEXT, " " + "a".repeat(300) + " ", List.of()).text())
                .isEqualTo("a".repeat(300));
        assertThat(violationOf(ScreeningQuestionType.SHORT_TEXT, "a".repeat(301), List.of()))
                .isEqualTo(ScreeningQuestionViolation.QUESTION_TEXT_TOO_LONG);
        assertThat(violationOf(ScreeningQuestionType.SHORT_TEXT, " " + "a".repeat(301), List.of()))
                .isEqualTo(ScreeningQuestionViolation.QUESTION_TEXT_TOO_LONG);
    }

    // Dem theo code point: 300 ky tu ngoai BMP (600 don vi UTF-16) van hop le.
    @Test
    void questionText_countedInCodePoints() {
        String emoji = "😀";
        assertThat(ScreeningQuestionRules.normalize(ScreeningQuestionType.YES_NO, emoji.repeat(300), List.of()).text())
                .hasSize(600);
        assertThat(violationOf(ScreeningQuestionType.YES_NO, emoji.repeat(301), List.of()))
                .isEqualTo(ScreeningQuestionViolation.QUESTION_TEXT_TOO_LONG);
    }

    // T3 - phuong an SINGLE_CHOICE
    @Test
    void singleChoice_optionCountBoundaries() {
        assertThat(violationOf(SINGLE, "Cau hoi", distinctOptions(1))).isEqualTo(ScreeningQuestionViolation.OPTION_COUNT);
        assertThat(ScreeningQuestionRules.normalize(SINGLE, "Cau hoi", distinctOptions(2)).options()).hasSize(2);
        assertThat(ScreeningQuestionRules.normalize(SINGLE, "Cau hoi", distinctOptions(10)).options()).hasSize(10);
        assertThat(violationOf(SINGLE, "Cau hoi", distinctOptions(11))).isEqualTo(ScreeningQuestionViolation.OPTION_COUNT);
        assertThat(violationOf(SINGLE, "Cau hoi", null)).isEqualTo(ScreeningQuestionViolation.OPTION_COUNT);
        assertThat(violationOf(SINGLE, "Cau hoi", List.of())).isEqualTo(ScreeningQuestionViolation.OPTION_COUNT);
    }

    @Test
    void singleChoice_optionLengthBoundaries_afterStrip() {
        assertThat(ScreeningQuestionRules.normalize(SINGLE, "Cau hoi", List.of("a".repeat(99), "b")).options())
                .containsExactly("a".repeat(99), "b");
        assertThat(ScreeningQuestionRules.normalize(SINGLE, "Cau hoi", List.of("a".repeat(100), "b")).options())
                .containsExactly("a".repeat(100), "b");
        assertThat(ScreeningQuestionRules.normalize(SINGLE, "Cau hoi", List.of(" " + "a".repeat(100) + " ", "b"))
                        .options())
                .containsExactly("a".repeat(100), "b");
        assertThat(violationOf(SINGLE, "Cau hoi", List.of("a".repeat(101), "b")))
                .isEqualTo(ScreeningQuestionViolation.OPTION_TOO_LONG);
    }

    @Test
    void singleChoice_emptyOrNullOption_rejected() {
        List<String> withNull = new ArrayList<>(List.of("Co"));
        withNull.add(null);
        assertThat(violationOf(SINGLE, "Cau hoi", withNull)).isEqualTo(ScreeningQuestionViolation.OPTION_EMPTY);
        assertThat(violationOf(SINGLE, "Cau hoi", List.of("Co", ""))).isEqualTo(ScreeningQuestionViolation.OPTION_EMPTY);
        assertThat(violationOf(SINGLE, "Cau hoi", List.of("Co", "   ")))
                .isEqualTo(ScreeningQuestionViolation.OPTION_EMPTY);
    }

    @Test
    void singleChoice_duplicateAfterStripIgnoringCase_rejected() {
        assertThat(violationOf(SINGLE, "Cau hoi", List.of("Hà Nội", " hà nội ")))
                .isEqualTo(ScreeningQuestionViolation.OPTION_DUPLICATE);
    }

    @Test
    void shortTextOrYesNo_withOptions_rejected_withoutOptions_optionsNull() {
        assertThat(violationOf(ScreeningQuestionType.SHORT_TEXT, "Cau hoi", List.of("x")))
                .isEqualTo(ScreeningQuestionViolation.OPTIONS_NOT_ALLOWED);
        assertThat(violationOf(ScreeningQuestionType.YES_NO, "Cau hoi", List.of("Co", "Khong")))
                .isEqualTo(ScreeningQuestionViolation.OPTIONS_NOT_ALLOWED);
        assertThat(ScreeningQuestionRules.normalize(ScreeningQuestionType.YES_NO, " Cau hoi ", null))
                .isEqualTo(new ScreeningQuestionRules.NormalizedQuestion(ScreeningQuestionType.YES_NO, "Cau hoi", null));
    }

    // Dung o loi dau tien theo thu tu bang R-Q4b: cau rong duoc bao truoc loi phuong an.
    @Test
    void stopsAtFirstViolationInDeclaredOrder() {
        assertThat(violationOf(SINGLE, " ", distinctOptions(11))).isEqualTo(ScreeningQuestionViolation.QUESTION_TEXT_EMPTY);
        assertThat(violationOf(SINGLE, "Cau hoi", List.of("", "a".repeat(101))))
                .isEqualTo(ScreeningQuestionViolation.OPTION_EMPTY);
    }
}
