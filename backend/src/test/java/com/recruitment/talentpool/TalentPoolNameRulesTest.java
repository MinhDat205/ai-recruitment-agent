package com.recruitment.talentpool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// FR-H15 T1 (R-K2) - strip roi kiem 1-100 ky tu theo code point. Bien: 0 (rong / chi khoang trang), 1, 100, 101.
class TalentPoolNameRulesTest {

    @Test
    void t1_nullEmptyOrBlank_rejected() {
        assertThat(TalentPoolNameRules.normalize(null)).isEmpty();
        assertThat(TalentPoolNameRules.normalize("")).isEmpty();
        assertThat(TalentPoolNameRules.normalize("   \t\n ")).isEmpty();
        // U+3000 (khoang trang chu Han) - strip() nhan dien theo Character.isWhitespace.
        assertThat(TalentPoolNameRules.normalize("　")).isEmpty();
    }

    @Test
    void t1_oneCharacter_accepted() {
        assertThat(TalentPoolNameRules.normalize("A")).contains("A");
    }

    @Test
    void t1_hundredCharacters_accepted_hundredOne_rejected() {
        assertThat(TalentPoolNameRules.normalize("a".repeat(100))).contains("a".repeat(100));
        assertThat(TalentPoolNameRules.normalize("a".repeat(101))).isEmpty();
    }

    @Test
    void t1_stripsBothEnds_thenCountsStrippedLength() {
        assertThat(TalentPoolNameRules.normalize("  Backend tiềm năng \t")).contains("Backend tiềm năng");
        // 100 ky tu sau khi strip van dat du chuoi goc dai 104.
        assertThat(TalentPoolNameRules.normalize("  " + "b".repeat(100) + "  ")).contains("b".repeat(100));
    }

    @Test
    void t1_vietnameseWithDiacritics_hundredAccepted_hundredOneRejected() {
        // "ữ" dang dung san (precomposed, U+1EEF) - 1 code point.
        assertThat(TalentPoolNameRules.normalize("ữ".repeat(100))).contains("ữ".repeat(100));
        assertThat(TalentPoolNameRules.normalize("ữ".repeat(101))).isEmpty();
    }

    @Test
    void t1_countsCodePoints_notUtf16Units() {
        // U+1F4BC (cap tai lieu) nam ngoai BMP: 1 code point = 2 don vi UTF-16. 100 ky tu = 200 char Java van dat.
        String briefcase = new String(Character.toChars(0x1F4BC));
        assertThat(briefcase.repeat(100).length()).isEqualTo(200);
        assertThat(TalentPoolNameRules.normalize(briefcase.repeat(100))).contains(briefcase.repeat(100));
        assertThat(TalentPoolNameRules.normalize(briefcase.repeat(101))).isEmpty();
    }
}
