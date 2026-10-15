package com.recruitment.ai.citation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// T1 (FR-C08, R-K2-2, R-K2-3). Test thuan Java, khong Spring context.
// Hai ca normalizeWhitespace dau tien dung DUNG dau vao/ky vong cua CriterionScoringServiceTest:95-108
// - tuong duong bac cau voi lop chuyen tiep CriterionScoringService.normalizeWhitespace (T2, L4).
class VerbatimQuoteVerifierTest {

    private static final String SOURCE =
            "Kinh nghiệm\n  Lập trình viên Java tại Hà Nội,\t3 năm\n\nKỹ năng: Docker, Spring Boot";

    @Test
    void normalizeWhitespace_collapsesConsecutiveWhitespaceIncludingTabsAndNewlines_andTrims() {
        String messy = "  Docker\tva\n  Kubernetes  ";

        String result = VerbatimQuoteVerifier.normalizeWhitespace(messy);

        assertThat(result).isEqualTo("Docker va Kubernetes");
    }

    @Test
    void normalizeWhitespace_doesNotStripVietnameseDiacritics() {
        String text = "Có 3 năm kinh nghiệm";

        String result = VerbatimQuoteVerifier.normalizeWhitespace(text);

        assertThat(result).isEqualTo("Có 3 năm kinh nghiệm");
    }

    @Test
    void isVerbatim_messyWhitespaceOnBothSides_returnsTrue() {
        String quote = "  Lập   trình viên\tJava  tại\nHà Nội, ";

        assertThat(VerbatimQuoteVerifier.isVerbatim(quote, SOURCE)).isTrue();
    }

    @Test
    void isVerbatim_vietnameseWithDiacriticsExactMatch_returnsTrue() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("Lập trình viên Java tại Hà Nội", SOURCE)).isTrue();
    }

    @Test
    void isVerbatim_differentCase_returnsFalse() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("Lập trình viên java", SOURCE)).isFalse();
    }

    @Test
    void isVerbatim_missingOneDiacritic_returnsFalse() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("Java tại Hà Nôi", SOURCE)).isFalse();
    }

    @Test
    void isVerbatim_quoteNotInSource_returnsFalse() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("5 năm kinh nghiệm Kubernetes", SOURCE)).isFalse();
    }

    @Test
    void isVerbatim_quoteSpanningLineBreak_returnsTrue() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("3 năm Kỹ năng: Docker", SOURCE)).isTrue();
    }

    @Test
    void isVerbatim_nullQuote_returnsFalse() {
        assertThat(VerbatimQuoteVerifier.isVerbatim(null, SOURCE)).isFalse();
    }

    @Test
    void isVerbatim_emptyQuote_returnsTrue_giuHanhViH04() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("", SOURCE)).isTrue();
    }

    @Test
    void isVerbatim_blankQuote_returnsTrue_giuHanhViH04() {
        assertThat(VerbatimQuoteVerifier.isVerbatim("   ", SOURCE)).isTrue();
    }

    @Test
    void isVerbatim_nullSource_throwsNullPointerException() {
        assertThatThrownBy(() -> VerbatimQuoteVerifier.isVerbatim("Docker", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isVerbatim_nullSourceAndNullQuote_throwsNullPointerException() {
        assertThatThrownBy(() -> VerbatimQuoteVerifier.isVerbatim(null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
