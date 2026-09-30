package com.recruitment.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Test don vi thuan (khong Spring, khong DB) cho REQUIREMENT FR-C05 muc 7.1. Du lieu fixture chep
// mot phan bang R-P4/R-P5/R-I1; kiem tren du lieu THAT cua V8 nam o CatalogSeedConsistencyTest.
class CatalogMatcherTest {

    private static final CatalogMatcher PROVINCES = CatalogMatcher.forProvinces(List.of(
            new CatalogEntry("HA_NOI", "Hà Nội", List.of("Hanoi", "HN")),
            new CatalogEntry(
                    "HO_CHI_MINH",
                    "TP. Hồ Chí Minh",
                    List.of("Bình Dương", "Bà Rịa - Vũng Tàu", "HCM", "TPHCM", "Sài Gòn")),
            new CatalogEntry("KHANH_HOA", "Khánh Hòa", List.of("Ninh Thuận"))));

    private static final CatalogMatcher INDUSTRIES = CatalogMatcher.forIndustries(List.of(
            new CatalogEntry("ACCOUNTING_AUDIT", "Kế toán - Kiểm toán", List.of("Kế toán", "Kiểm toán")),
            new CatalogEntry(
                    "IT_SOFTWARE",
                    "Công nghệ thông tin - Phần mềm",
                    List.of("Công nghệ thông tin", "IT", "Trí tuệ nhân tạo"))));

    @ParameterizedTest
    @ValueSource(strings = {
        "TP. Hồ Chí Minh", // nhan
        "Hồ Chí Minh", // khoa cua nhan sau R-M2
        "Thành phố Hồ Chí Minh",
        "TP.HCM", // "tp." -> "hcm" = bi danh HCM
        "TP HCM",
        "HCM",
        "Sài Gòn",
        "Bình Dương", // ten tinh cu
        "Tỉnh Bình Dương",
        "Bà Rịa-Vũng Tàu", // R-M1b
        "Bà Rịa – Vũng Tàu",
        "  tp.   hồ  CHÍ minh  "
    })
    void matchesHoChiMinh(String input) {
        assertThat(PROVINCES.match(input)).isEqualTo("HO_CHI_MINH");
    }

    @Test
    void matchesIgnoringCaseSpacesAndToneMarkPlacement() {
        assertThat(PROVINCES.match("  hà   NỘI ")).isEqualTo("HA_NOI");
        assertThat(PROVINCES.match("Hanoi")).isEqualTo("HA_NOI");
        assertThat(PROVINCES.match("Khánh Hòa")).isEqualTo("KHANH_HOA");
        assertThat(PROVINCES.match("Khánh Hoà")).isEqualTo("KHANH_HOA");
        assertThat(PROVINCES.match("Ninh Thuận")).isEqualTo("KHANH_HOA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Kế toán - Kiểm toán", "Kế toán – Kiểm toán", "Kế toán—Kiểm toán", "Kế toán-Kiểm toán", "KẾ TOÁN -KIỂM TOÁN"})
    void matchesAccountingWithAnyDashVariant(String input) {
        assertThat(INDUSTRIES.match(input)).isEqualTo("ACCOUNTING_AUDIT");
    }

    @Test
    void matchesIndustryAliases() {
        assertThat(INDUSTRIES.match("Công nghệ thông tin")).isEqualTo("IT_SOFTWARE");
        assertThat(INDUSTRIES.match("Trí tuệ nhân tạo")).isEqualTo("IT_SOFTWARE");
        assertThat(INDUSTRIES.match("it")).isEqualTo("IT_SOFTWARE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hà Nội, TP. HCM", "Quận 1", "Quận 1, Hồ Chí Minh", "Ho Chi Min", "TP.", "Việt Nam"})
    void doesNotMatchApproximatelyOrPartially(String input) {
        assertThat(PROVINCES.match(input)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " "})
    void blankInputReturnsNull(String input) {
        assertThat(PROVINCES.match(input)).isNull();
        assertThat(INDUSTRIES.match(input)).isNull();
    }

    @Test
    void provincePrefixIsNotStrippedForIndustries() {
        // R-M2 chi ap cho tinh/thanh: "Tinh ..." o danh muc nganh khong bi cat tien to.
        CatalogMatcher industries =
                CatalogMatcher.forIndustries(List.of(new CatalogEntry("X", "Tinh chỉnh", List.of())));
        assertThat(industries.match("Tinh chỉnh")).isEqualTo("X");
        assertThat(industries.match("chỉnh")).isNull();
    }

    @Test
    void keyPointingToTwoCodesFailsFast() {
        assertThatThrownBy(() -> CatalogMatcher.forProvinces(List.of(
                        new CatalogEntry("A", "Hà Nội", List.of()),
                        new CatalogEntry("B", "Hải Phòng", List.of("Ha Noi")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hai ma khac nhau");
    }

    @Test
    void aliasRedundantWithSameCodeIsReported() {
        CatalogMatcher matcher = CatalogMatcher.forProvinces(List.of(
                new CatalogEntry("HO_CHI_MINH", "TP. Hồ Chí Minh", List.of("Hồ Chí Minh", "HCM", "TP.HCM"))));
        assertThat(matcher.redundantAliases()).containsExactly("Hồ Chí Minh", "TP.HCM");
        // Thua nhung khong sai: van khop dung ma.
        assertThat(matcher.match("TP.HCM")).isEqualTo("HO_CHI_MINH");
    }

    @Test
    void normalizeTextDoesNotTouchDashes() {
        // R-E3 dung R-M1a: "01-2020" phai giu nguyen dang.
        assertThat(CatalogTextNormalizer.normalizeText(" 01-2020 ")).isEqualTo("01-2020");
        assertThat(CatalogTextNormalizer.normalize("01-2020")).isEqualTo("01 - 2020");
        assertThat(CatalogTextNormalizer.normalizeText("Hiện tại")).isEqualTo("hien tai");
        assertThat(CatalogTextNormalizer.normalizeText("Đến nay")).isEqualTo("den nay");
    }
}
