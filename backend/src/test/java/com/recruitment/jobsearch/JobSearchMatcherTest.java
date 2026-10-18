package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.job.Job;
import com.recruitment.jobsearch.JobSearchConditionResolver.Resolution;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// FR-U13 T9 (unit, khong Spring) - dieu kien khop tung viec R-M: thu tu co dinh, ma NULL khong co dong, bien luong
// (moi moc can so phai co gia tri), ngoai te/thoa thuan khong co dong luong.
class JobSearchMatcherTest {

    private static final CatalogRegistry REGISTRY = new CatalogRegistry(
            List.of(new CatalogEntry("ACCOUNTING_AUDIT", "Kế toán - Kiểm toán", List.of())),
            List.of(
                    new CatalogEntry("HA_NOI", "Hà Nội", List.of()),
                    new CatalogEntry("DA_NANG", "Đà Nẵng", List.of())));
    private static final String SALARY = "Mức lương khớp";

    private final JobSearchMatcher matcher = new JobSearchMatcher(REGISTRY);

    private static Resolution conditions(
            List<String> locations, List<String> industries, Integer min, Integer max, List<String> workModes) {
        return new Resolution(locations, industries, min, max, workModes, Set.of(), Set.of(), List.of());
    }

    private static Resolution salary(Integer min, Integer max) {
        return conditions(List.of(), List.of(), min, max, List.of());
    }

    private static Job job(String location, String category, String workMode) {
        Job job = new Job();
        job.setLocationCode(location);
        job.setCategoryCode(category);
        job.setWorkMode(workMode);
        return job;
    }

    private static Job salaryJob(Integer minMillions, Integer maxMillions, String currency) {
        Job job = new Job();
        job.setSalaryMin(minMillions == null ? null : BigDecimal.valueOf(minMillions * 1_000_000L));
        job.setSalaryMax(maxMillions == null ? null : BigDecimal.valueOf(maxMillions * 1_000_000L));
        job.setSalaryCurrency(currency);
        return job;
    }

    private boolean salaryMatched(Job job, Resolution conditions) {
        return matcher.matchedConditions(job, conditions).contains(SALARY);
    }

    @Test
    void salaryLabel_isFixedString() {
        assertThat(JobSearchMatcher.SALARY_MATCH_LABEL).isEqualTo(SALARY);
    }

    // ---- thu tu co dinh Khu vuc . Nganh . Hinh thuc . Luong ----

    @Test
    void allMatched_fixedOrder() {
        Job job = job("HA_NOI", "ACCOUNTING_AUDIT", "REMOTE");
        job.setSalaryMin(BigDecimal.valueOf(9_000_000));
        job.setSalaryMax(BigDecimal.valueOf(14_000_000));
        job.setSalaryCurrency("VND");

        List<String> matched = matcher.matchedConditions(
                job, conditions(List.of("DA_NANG", "HA_NOI"), List.of("ACCOUNTING_AUDIT"), 10, null,
                        List.of("ONSITE", "REMOTE")));

        assertThat(matched).containsExactly("Hà Nội", "Kế toán - Kiểm toán", "Từ xa", SALARY);
    }

    @Test
    void noConditions_emptyList() {
        Job job = job("HA_NOI", "ACCOUNTING_AUDIT", "REMOTE");
        job.setSalaryMax(BigDecimal.valueOf(20_000_000));

        assertThat(matcher.matchedConditions(job, conditions(List.of(), List.of(), null, null, List.of()))).isEmpty();
    }

    @Test
    void codesNotInConditions_noLines() {
        assertThat(matcher.matchedConditions(job("DA_NANG", "ACCOUNTING_AUDIT", "ONSITE"),
                conditions(List.of("HA_NOI"), List.of("IT_SOFTWARE"), null, null, List.of("REMOTE")))).isEmpty();
    }

    // R-M2 - viec lot qua do ma NULL (SQL coi NULL la khong loai) -> khong co dong khu vuc/nganh/hinh thuc.
    @Test
    void nullCodes_noLocationIndustryOrWorkModeLine() {
        assertThat(matcher.matchedConditions(job(null, null, null),
                conditions(List.of("HA_NOI"), List.of("ACCOUNTING_AUDIT"), null, null, List.of("REMOTE")))).isEmpty();
    }

    // ---- luong: viec 9-14 trieu ----

    @Test
    void salary_job9To14_minBoundary14Matched_15NotMatched() {
        Job job = salaryJob(9, 14, "VND");
        assertThat(salaryMatched(job, salary(14, null))).isTrue();
        assertThat(salaryMatched(job, salary(13, null))).isTrue();
        assertThat(salaryMatched(job, salary(15, null))).isFalse();
    }

    @Test
    void salary_job9To14_maxBoundary9Matched_8NotMatched() {
        Job job = salaryJob(9, 14, "VND");
        assertThat(salaryMatched(job, salary(null, 10))).isTrue();
        assertThat(salaryMatched(job, salary(null, 9))).isTrue();
        assertThat(salaryMatched(job, salary(null, 8))).isFalse();
    }

    @Test
    void salary_job9To14_range10To12Matched() {
        assertThat(salaryMatched(salaryJob(9, 14, "VND"), salary(10, 12))).isTrue();
        assertThat(salaryMatched(salaryJob(9, 14, "VND"), salary(15, 20))).isFalse();
        assertThat(salaryMatched(salaryJob(9, 14, "VND"), salary(5, 8))).isFalse();
    }

    // Mot moc can so ma NULL -> khong co dong luong.
    @Test
    void salary_jobOnlyMin10_conditionMin5_notMatched() {
        assertThat(salaryMatched(salaryJob(10, null, "VND"), salary(5, null))).isFalse();
    }

    @Test
    void salary_jobOnlyMax20_conditionMax25_notMatched() {
        assertThat(salaryMatched(salaryJob(null, 20, "VND"), salary(null, 25))).isFalse();
    }

    @Test
    void salary_jobOnlyMax20_conditionMin15_matched_min21NotMatched() {
        assertThat(salaryMatched(salaryJob(null, 20, "VND"), salary(15, null))).isTrue();
        assertThat(salaryMatched(salaryJob(null, 20, "VND"), salary(20, null))).isTrue();
        assertThat(salaryMatched(salaryJob(null, 20, "VND"), salary(21, null))).isFalse();
    }

    @Test
    void salary_jobOnlyMin10_conditionMax12_matched() {
        assertThat(salaryMatched(salaryJob(10, null, "VND"), salary(null, 12))).isTrue();
    }

    @Test
    void salary_foreignCurrency_notMatched() {
        assertThat(salaryMatched(salaryJob(9, 14, "USD"), salary(10, null))).isFalse();
    }

    // Tien te chuan hoa nhu U15: null -> VND; khoang trang/hoa thuong.
    @Test
    void salary_currencyNullOrLowercaseVnd_treatedAsVnd() {
        assertThat(salaryMatched(salaryJob(9, 14, null), salary(10, null))).isTrue();
        assertThat(salaryMatched(salaryJob(9, 14, " vnd "), salary(10, null))).isTrue();
    }

    @Test
    void salary_negotiable_bothNull_notMatched() {
        assertThat(salaryMatched(salaryJob(null, null, "VND"), salary(10, null))).isFalse();
        assertThat(salaryMatched(salaryJob(null, null, "VND"), salary(null, 10))).isFalse();
    }

    @Test
    void salary_noSalaryCondition_noLine() {
        assertThat(salaryMatched(salaryJob(9, 14, "VND"), salary(null, null))).isFalse();
    }
}
