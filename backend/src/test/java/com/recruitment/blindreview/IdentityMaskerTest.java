package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// FR-H16 T2 - IdentityMasker (R-M2, R-M3): ham thuan, khong Spring context.
class IdentityMaskerTest {

    private static final String HIDDEN = "[đã ẩn]";

    private static IdentityMasker masker(String... values) {
        return IdentityMasker.of(Arrays.asList(values));
    }

    @Test
    void replacementIsFixedString() {
        assertThat(IdentityMasker.REPLACEMENT).isEqualTo("[đã ẩn]");
    }

    @Test
    void masksCaseInsensitivelyWithUnicode() {
        IdentityMasker m = masker("Quốc Huy");
        assertThat(m.mask("Ứng viên QUỐC HUY có 5 năm kinh nghiệm")).isEqualTo("Ứng viên " + HIDDEN + " có 5 năm kinh nghiệm");
        assertThat(m.mask("quốc huy")).isEqualTo(HIDDEN);
    }

    @Test
    void masksUppercaseSourceValueInMixedCaseText() {
        // Gia tri contact.fullName trong CV thuong viet hoa (seed R3: "QUỐC HUY").
        assertThat(masker("QUỐC HUY").mask("Chào Quốc Huy")).isEqualTo("Chào " + HIDDEN);
    }

    @Test
    void masksNfdInputText() {
        String nfdText = Normalizer.normalize("Chào Quốc Huy nhé", Normalizer.Form.NFD);
        assertThat(nfdText).isNotEqualTo("Chào Quốc Huy nhé");
        assertThat(masker("Quốc Huy").mask(nfdText)).isEqualTo("Chào " + HIDDEN + " nhé");
    }

    @Test
    void masksWhenSourceValueIsNfd() {
        String nfdValue = Normalizer.normalize("Quốc Huy", Normalizer.Form.NFD);
        assertThat(masker(nfdValue).mask("Chào Quốc Huy")).isEqualTo("Chào " + HIDDEN);
    }

    @Test
    void valueOfTwoCodePointsIsNotMasked() {
        assertThat(masker("An").mask("Anh An đã gửi")).isEqualTo("Anh An đã gửi");
    }

    @Test
    void valueOfThreeCodePointsIsMasked() {
        assertThat(masker("Huy").mask("anh Huy")).isEqualTo("anh " + HIDDEN);
    }

    @Test
    void valueOfFourCodePointsIsMasked() {
        assertThat(masker("Hùng").mask("anh Hùng")).isEqualTo("anh " + HIDDEN);
    }

    @Test
    void thresholdCountsCodePointsAfterStrip() {
        // "  An  " sau strip() con 2 code point -> bo qua.
        assertThat(masker("  An  ").mask("An An")).isEqualTo("An An");
        // "  Huy " sau strip() con 3 code point -> che, khong che khoang trang xung quanh.
        assertThat(masker("  Huy ").mask("a Huy b")).isEqualTo("a " + HIDDEN + " b");
    }

    @Test
    void nullEmptyAndBlankValuesAreIgnored() {
        IdentityMasker m = IdentityMasker.of(Arrays.asList(null, "", "   ", "\t\n"));
        assertThat(m.mask("Nội dung   giữ nguyên")).isEqualTo("Nội dung   giữ nguyên");
    }

    @Test
    void longerValueIsReplacedFirst() {
        IdentityMasker m = masker("quochuy", "quochuy@gmail.com");
        assertThat(m.mask("Email: quochuy@gmail.com, tên quochuy")).isEqualTo("Email: " + HIDDEN + ", tên " + HIDDEN);
    }

    @Test
    void longerValueFirstRegardlessOfInputOrder() {
        IdentityMasker m = masker("Quốc Huy", "Nguyễn Quốc Huy");
        assertThat(m.mask("Họ tên: Nguyễn Quốc Huy")).isEqualTo("Họ tên: " + HIDDEN);
    }

    @Test
    void masksEveryOccurrence() {
        assertThat(masker("Quốc Huy").mask("Quốc Huy, Quốc Huy và quốc huy"))
                .isEqualTo(HIDDEN + ", " + HIDDEN + " và " + HIDDEN);
    }

    @Test
    void regexSpecialCharactersAreQuoted() {
        IdentityMasker m = masker("+84 (90) 000.111");
        assertThat(m.mask("Gọi +84 (90) 000.111 nhé")).isEqualTo("Gọi " + HIDDEN + " nhé");
        // Dau '.' khong phai ky tu dai dien.
        assertThat(m.mask("+84 (90) 000x111")).isEqualTo("+84 (90) 000x111");
    }

    @Test
    void valueContainingReplacementSyntaxIsLiteral() {
        assertThat(masker("a$1b\\c").mask("x a$1b\\c y")).isEqualTo("x " + HIDDEN + " y");
    }

    @Test
    void nullTextReturnsNull() {
        assertThat(masker("Quốc Huy").mask(null)).isNull();
        assertThat(IdentityMasker.none().mask(null)).isNull();
    }

    @Test
    void partOfNameStandingAloneIsNotMasked() {
        // Gioi han 6b: chi thay DUNG gia tri nguon - "Quốc" dung rieng khong bi che.
        assertThat(masker("Quốc Huy").mask("Quốc gia")).isEqualTo("Quốc gia");
    }

    @Test
    void noneReturnsTextUnchanged() {
        String nfd = Normalizer.normalize("Quốc Huy", Normalizer.Form.NFD);
        assertThat(IdentityMasker.none().mask(nfd)).isSameAs(nfd);
        assertThat(IdentityMasker.of(List.of())).isSameAs(IdentityMasker.none());
    }
}
