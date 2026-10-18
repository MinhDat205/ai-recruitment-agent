package com.recruitment.ai.cvrephrase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.recruitment.common.text.VietnameseLetters;
import org.junit.jupiter.api.Test;

// FR-U12 T18 (R-A4, don vi) - ham kiem goi y dien dat: bien do dai 1,5 lan, con so, ngon ngu goc, ky tu R-F4; HAM TONG
// (khong bao gio nem exception). out o day la chuoi DA doi nguoc ‹ › va chuan hoa (CvRephraseService lam truoc).
class CvRephraseValidatorTest {

    private static final String VI_IN = "Tăng doanh số 30% năm 2023";

    // ---- buoc 2: do dai <= 1,5 lan ----

    @Test
    void length_in100_out149And150Valid_out151Invalid() {
        String in = "x".repeat(100);

        assertThat(CvRephraseValidator.isValid(in, "y".repeat(149))).isTrue();
        assertThat(CvRephraseValidator.isValid(in, "y".repeat(150))).isTrue();
        assertThat(CvRephraseValidator.isValid(in, "y".repeat(151))).isFalse();
    }

    // Do dai le: 3 * 3 / 2 = 4,5 -> 4 hop le, 5 hong.
    @Test
    void length_oddInput_roundsDown() {
        assertThat(CvRephraseValidator.isValid("abc", "abcd")).isTrue();
        assertThat(CvRephraseValidator.isValid("abc", "abcde")).isFalse();
    }

    // ---- buoc 1: khac null, khac rong ----

    @Test
    void emptyBlankOrNullOutput_invalid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, null)).isFalse();
        assertThat(CvRephraseValidator.isValid(VI_IN, "")).isFalse();
        assertThat(CvRephraseValidator.isValid(VI_IN, "   ")).isFalse();
        assertThat(CvRephraseValidator.isValid(VI_IN, "\n\n")).isFalse();
    }

    // ---- buoc 3: con so ----

    @Test
    void digits_sameRunsWithDifferentSpacing_valid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng 30 % doanh số trong năm 2023")).isTrue();
    }

    @Test
    void digits_changedNumber_invalid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số 35% năm 2023")).isFalse();
    }

    // "3" la day con cua "30" nhung khong phai NGUYEN day nao cua dau vao.
    @Test
    void digits_partOfOriginalRun_invalid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số 3 lần năm 2023")).isFalse();
        assertThat(CvRephraseValidator.isValid("Đào tạo 30 nhân viên mới", "Đào tạo 3 nhân viên mới")).isFalse();
    }

    @Test
    void digits_newYear_invalid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số 30% năm 2024")).isFalse();
    }

    // Bo bot con so khong pham luat con so (chi chan so MOI) - prompt yeu cau giu, validator khong bat duoc (R-A6).
    @Test
    void digits_droppedNumber_stillValid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số 30% trong năm")).isTrue();
    }

    @Test
    void digitRuns_extractsWholeRuns() {
        assertThat(CvRephraseValidator.digitRunsOf("v2.015, 30%, 2 000 khách")).containsExactlyInAnyOrder(
                "2", "015", "30", "000");
    }

    // ---- buoc 4: ngon ngu goc ----

    @Test
    void language_vietnameseInput_outputWithoutDiacritics_invalid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tang doanh so 30% nam 2023")).isFalse();
    }

    @Test
    void language_englishInput_outputWithVietnameseWord_invalid() {
        String in = "Built an internal dashboard to track 120 support tickets per week.";

        assertThat(CvRephraseValidator.isValid(in, "Built an internal dashboard được 120 tickets per week.")).isFalse();
        assertThat(CvRephraseValidator.isValid(in, "Built an internal dashboard tracking 120 support tickets weekly."))
                .isTrue();
    }

    @Test
    void language_englishInputWithVietnameseName_keepingName_valid() {
        String in = "Worked with Nguyễn on a payment API serving 3 banks.";

        assertThat(CvRephraseValidator.isValid(in, "Collaborated with Nguyễn on a payment API serving 3 banks."))
                .isTrue();
        assertThat(CvRephraseValidator.isValid(in, "Collaborated with Nguyen on a payment API serving 3 banks."))
                .isFalse();
    }

    // Moi chu trong bo hang dung chung (L8) deu duoc nhan la chu tieng Viet co dau; nguyen am tron thi khong.
    @Test
    void hasVietnameseLetter_everyLetterOfSharedConstant_andNotPlainVowels() {
        for (char letter : VietnameseLetters.ALL.toCharArray()) {
            assertThat(CvRephraseValidator.hasVietnameseLetter("abc" + letter)).as(String.valueOf(letter)).isTrue();
        }
        assertThat(CvRephraseValidator.hasVietnameseLetter("aeiouy AEIOUY dD")).isFalse();
    }

    // ---- out = in ----

    @Test
    void outputEqualToInput_valid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, VI_IN)).isTrue();
    }

    // ---- buoc 5: ky tu R-F4 ----

    @Test
    void zeroWidthSpace_invalid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số​ 30% năm 2023")).isFalse();
    }

    @Test
    void controlCharacter_invalid_newlineValid() {
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số\u0007 30% năm 2023")).isFalse();
        assertThat(CvRephraseValidator.isValid(VI_IN, "Tăng doanh số\n30% năm 2023")).isTrue();
    }

    // ---- ham tong ----

    @Test
    void nullInputs_returnFalse_neverThrow() {
        assertThatCode(() -> {
            assertThat(CvRephraseValidator.isValid(null, VI_IN)).isFalse();
            assertThat(CvRephraseValidator.isValid(null, null)).isFalse();
            assertThat(CvRephraseValidator.isValid("", "a")).isFalse();
        }).doesNotThrowAnyException();
    }
}
