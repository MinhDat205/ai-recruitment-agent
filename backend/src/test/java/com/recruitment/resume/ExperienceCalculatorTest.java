package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Test don vi tren class thuan (khong Spring) - 20 ca cua REQUIREMENT FR-C05 R-E10, refMonth = 09/2026
// tru khi ghi khac. Moi ca khang dinh du bon gia tri: months, nam (BigDecimal, so sanh bang chuoi de bat
// ca scale), counted, skipped.
class ExperienceCalculatorTest {

    private static final YearMonth REF = YearMonth.of(2026, 9);

    private static ResumeParsedPayload.Experience entry(String start, String end) {
        return new ResumeParsedPayload.Experience("Cong ty", "Chuc danh", start, end, "Mo ta");
    }

    private static ExperienceCalculator.Result compute(ResumeParsedPayload.Experience... entries) {
        return ExperienceCalculator.compute(List.of(entries), REF);
    }

    private static void assertCounted(ExperienceCalculator.Result result, int months, String years, int counted, int skipped) {
        assertThat(result.months()).isEqualTo(months);
        assertThat(ExperienceCalculator.toYears(result.months()).toPlainString()).isEqualTo(years);
        assertThat(result.countedEntries()).isEqualTo(counted);
        assertThat(result.skippedEntries()).isEqualTo(skipped);
    }

    private static void assertNoMonths(ExperienceCalculator.Result result, int counted, int skipped) {
        assertThat(result.months()).isNull();
        assertThat(result.countedEntries()).isEqualTo(counted);
        assertThat(result.skippedEntries()).isEqualTo(skipped);
    }

    @Test
    void case01_sixMonthsInclusive() {
        assertCounted(compute(entry("01/2020", "06/2020")), 6, "0.5", 1, 0);
    }

    @Test
    void case02_sameMonthCountsAsOne() {
        assertCounted(compute(entry("05/2020", "05/2020")), 1, "0.1", 1, 0);
    }

    @Test
    void case03_adjacentRangesAddUp() {
        assertCounted(compute(entry("01/2020", "12/2020"), entry("01/2021", "12/2021")), 24, "2.0", 2, 0);
    }

    @Test
    void case04_overlappingRangesCountedOnce() {
        assertCounted(compute(entry("01/2020", "12/2020"), entry("07/2020", "06/2021")), 18, "1.5", 2, 0);
    }

    @Test
    void case05_nestedRangeCountedOnce() {
        assertCounted(compute(entry("01/2019", "12/2022"), entry("03/2020", "05/2020")), 48, "4.0", 2, 0);
    }

    @Test
    void case06_presentInVietnameseEndsAtRefMonth() {
        assertCounted(compute(entry("03/2024", "Hiện tại")), 31, "2.6", 1, 0);
    }

    @Test
    void case07_presentInEnglishEndsAtRefMonth() {
        assertCounted(compute(entry("03/2024", "Present")), 31, "2.6", 1, 0);
    }

    @Test
    void case08_yearOnlyEntrySkippedOthersCounted() {
        assertCounted(compute(entry("2019", "2021"), entry("01/2022", "12/2022")), 12, "1.0", 1, 1);
    }

    @Test
    void case09_noReadableEntry_monthsNull() {
        assertNoMonths(compute(entry("2019", "2021"), entry("abc", "06/2020")), 0, 2);
    }

    @Test
    void case10_emptyList_monthsNullNotZero() {
        assertNoMonths(ExperienceCalculator.compute(List.of(), REF), 0, 0);
    }

    @Test
    void case11_missingEndDate_skippedNotAssumedPresent() {
        assertNoMonths(compute(entry("01/2020", null)), 0, 1);
    }

    @Test
    void case12_startAfterEnd_skipped() {
        assertNoMonths(compute(entry("06/2021", "01/2021")), 0, 1);
    }

    @Test
    void case13_startAfterRefMonthWithPresent_skipped() {
        assertNoMonths(compute(entry("10/2026", "Hiện tại")), 0, 1);
    }

    @Test
    void case14_endAfterRefMonth_skipped() {
        assertNoMonths(compute(entry("01/2026", "12/2026")), 0, 1);
    }

    @Test
    void case15_monthOutOfRange_skipped() {
        assertNoMonths(compute(entry("13/2020", "06/2021")), 0, 1);
    }

    // Ca 16: moi dang o R-E3 cho 01/2020, ket thuc 01/2020 -> 1 thang.
    @ParameterizedTest
    @ValueSource(strings = {
        "1/2020", "01/2020", "01-2020", "01.2020",
        "2020-01", "2020/01",
        "15/01/2020", "15-01-2020",
        "tháng 1/2020", "Tháng 1 năm 2020",
        "Jan 2020", "January 2020", "JAN 2020"
    })
    void case16_everySupportedFormat_readsJanuary2020(String start) {
        assertCounted(compute(entry(start, "01/2020")), 1, "0.1", 1, 0);
    }

    @Test
    void case17_threeMonths_roundsHalfUpAtQuarter() {
        assertCounted(compute(entry("01/2020", "03/2020")), 3, "0.3", 1, 0);
    }

    @Test
    void case18_nineMonths_roundsHalfUpAtThreeQuarters() {
        assertCounted(compute(entry("01/2020", "09/2020")), 9, "0.8", 1, 0);
    }

    @Test
    void case19_thirteenMonths() {
        assertCounted(compute(entry("01/2020", "01/2021")), 13, "1.1", 1, 0);
    }

    @Test
    void case20_startAtRefMonthWithPresent_oneMonth() {
        assertCounted(compute(entry("09/2026", "Hiện tại")), 1, "0.1", 1, 0);
    }

    // ---- Doc moc (R-E3, R-E4) - ca am ----

    @ParameterizedTest
    @ValueSource(strings = {"2019", "01/20", "00/2020", "13/2020", "2020-13", "Sept 2020", "abc", "   ", "01 - 2020",
        "Hiện tại", "present"})
    void parseMonth_unreadableOrPresentWord_returnsNull(String raw) {
        assertThat(ExperienceCalculator.parseMonth(raw)).isNull();
    }

    @Test
    void parseMonth_null_returnsNull() {
        assertThat(ExperienceCalculator.parseMonth(null)).isNull();
    }

    // Tu "dang lam" o startDate khong doc duoc (R-E4) -> muc bi bo qua.
    @Test
    void presentWordAsStartDate_entrySkipped() {
        assertNoMonths(compute(entry("Hiện tại", "06/2026")), 0, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hiện tại", "hien nay", "Nay", "Đến nay", "PRESENT", "Now", "current", "  hiện   tại  "})
    void everyPresentWord_endsAtRefMonth(String end) {
        assertCounted(compute(entry("09/2026", end)), 1, "0.1", 1, 0);
    }

    // ---- Quy doi nam (R-E8): bien lam tron, khong double ----

    @Test
    void toYears_boundaries() {
        assertThat(ExperienceCalculator.toYears(1)).isEqualTo(new BigDecimal("0.1"));
        assertThat(ExperienceCalculator.toYears(3)).isEqualTo(new BigDecimal("0.3"));
        assertThat(ExperienceCalculator.toYears(9)).isEqualTo(new BigDecimal("0.8"));
        assertThat(ExperienceCalculator.toYears(12)).isEqualTo(new BigDecimal("1.0"));
        assertThat(ExperienceCalculator.toYears(15)).isEqualTo(new BigDecimal("1.3"));
    }

    // ---- Moc tham chieu theo gio Viet Nam (R-E2) ----

    @Test
    void referenceMonth_usesVietnamTimeZone() {
        // 17:30 UTC ngay 30/09 = 00:30 ngay 01/10 gio Viet Nam.
        assertThat(ExperienceCalculator.referenceMonth(Instant.parse("2026-09-30T17:30:00Z")))
                .isEqualTo(YearMonth.of(2026, 10));
        // 16:59 UTC ngay 30/09 = 23:59 ngay 30/09 gio Viet Nam.
        assertThat(ExperienceCalculator.referenceMonth(Instant.parse("2026-09-30T16:59:00Z")))
                .isEqualTo(YearMonth.of(2026, 9));
    }

    // Muc null trong danh sach (JSON LLM co the co phan tu null) bi bo qua, khong nem NPE.
    @Test
    void nullEntryInList_skipped() {
        List<ResumeParsedPayload.Experience> entries = new ArrayList<>();
        entries.add(null);
        entries.add(entry("01/2020", "06/2020"));
        assertCounted(ExperienceCalculator.compute(entries, REF), 6, "0.5", 1, 1);
    }
}
