package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.jobsearch.JobSearchParsePayload;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.CodeCondition;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.SalaryCondition;
import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.jobsearch.JobSearchConditionResolver.Resolution;
import java.util.List;
import org.junit.jupiter.api.Test;

// FR-U13 T7 (unit, khong Spring) - chap nhan dieu kien R-C tren danh muc fixture: ma hop le/la, khu trung, tran 3 ma,
// OTHER, hinh thuc lam viec, bien luong [0, 1000] va min <= max. Doan chu cua dieu kien nhan/bo cho R-D1.
class JobSearchConditionResolverTest {

    private static final CatalogRegistry REGISTRY = new CatalogRegistry(
            List.of(
                    new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of()),
                    new CatalogEntry("ACCOUNTING_AUDIT", "Kế toán - Kiểm toán", List.of()),
                    new CatalogEntry("MARKETING", "Marketing", List.of()),
                    new CatalogEntry("SALES", "Bán hàng", List.of()),
                    new CatalogEntry("OTHER", "Khác", List.of())),
            List.of(
                    new CatalogEntry("HA_NOI", "Hà Nội", List.of()),
                    new CatalogEntry("DA_NANG", "Đà Nẵng", List.of()),
                    new CatalogEntry("HUE", "Huế", List.of()),
                    new CatalogEntry("HO_CHI_MINH", "TP. Hồ Chí Minh", List.of())));

    private final JobSearchConditionResolver resolver = new JobSearchConditionResolver(REGISTRY);

    private static CodeCondition c(String code, String text) {
        return new CodeCondition(code, text);
    }

    private Resolution locations(CodeCondition... conditions) {
        return resolver.resolve(new JobSearchParsePayload(List.of(conditions), List.of(), null, List.of(), List.of()));
    }

    private Resolution industries(CodeCondition... conditions) {
        return resolver.resolve(new JobSearchParsePayload(List.of(), List.of(conditions), null, List.of(), List.of()));
    }

    private Resolution workModes(CodeCondition... conditions) {
        return resolver.resolve(new JobSearchParsePayload(List.of(), List.of(), null, List.of(conditions), List.of()));
    }

    private Resolution salary(Integer min, Integer max) {
        return resolver.resolve(new JobSearchParsePayload(
                List.of(), List.of(), new SalaryCondition(min, max, "lương"), List.of(), List.of()));
    }

    // ---- R-C1 khu vuc ----

    @Test
    void location_validCode_accepted_textAccepted() {
        Resolution r = locations(c("HA_NOI", "ở Hà Nội"));

        assertThat(r.locationCodes()).containsExactly("HA_NOI");
        assertThat(r.acceptedSourceTexts()).containsExactly("ở Hà Nội");
        assertThat(r.droppedSourceTexts()).isEmpty();
    }

    @Test
    void location_unknownCode_dropped_textDropped() {
        Resolution r = locations(c("SAO_HOA", "ở Sao Hoả"));

        assertThat(r.locationCodes()).isEmpty();
        assertThat(r.acceptedSourceTexts()).isEmpty();
        assertThat(r.droppedSourceTexts()).containsExactly("ở Sao Hoả");
    }

    // Ma khong phai tinh (ma nganh, chu thuong, null) -> bo.
    @Test
    void location_industryCodeLowercaseOrNull_dropped() {
        assertThat(locations(c("IT_SOFTWARE", "a")).locationCodes()).isEmpty();
        assertThat(locations(c("ha_noi", "a")).locationCodes()).isEmpty();
        assertThat(locations(c(null, "a")).locationCodes()).isEmpty();
    }

    @Test
    void location_duplicate_keptOnceInFirstOrder() {
        Resolution r = locations(c("DA_NANG", "Đà Nẵng"), c("HA_NOI", "Hà Nội"), c("DA_NANG", "ở Đà Nẵng"));

        assertThat(r.locationCodes()).containsExactly("DA_NANG", "HA_NOI");
        assertThat(r.acceptedSourceTexts()).containsExactly("Đà Nẵng", "Hà Nội", "ở Đà Nẵng");
    }

    // Bien MAX_CODES: 2, 3 nhan; ma thu 4 bo (doan cua no vao danh sach bo).
    @Test
    void location_twoAndThreeCodesAccepted_fourthDropped() {
        assertThat(locations(c("HA_NOI", "a"), c("DA_NANG", "b")).locationCodes()).hasSize(2);
        assertThat(locations(c("HA_NOI", "a"), c("DA_NANG", "b"), c("HUE", "c")).locationCodes())
                .containsExactly("HA_NOI", "DA_NANG", "HUE");

        Resolution four = locations(c("HA_NOI", "a"), c("DA_NANG", "b"), c("HUE", "c"), c("HO_CHI_MINH", "d"));
        assertThat(four.locationCodes()).containsExactly("HA_NOI", "DA_NANG", "HUE");
        assertThat(four.droppedSourceTexts()).containsExactly("d");
    }

    // Ma trung KHONG chiem cho: 3 ma khac nhau + 1 ma trung van du 3.
    @Test
    void location_duplicateDoesNotCountTowardLimit() {
        Resolution r = locations(c("HA_NOI", "a"), c("HA_NOI", "b"), c("DA_NANG", "c"), c("HUE", "d"));

        assertThat(r.locationCodes()).containsExactly("HA_NOI", "DA_NANG", "HUE");
        assertThat(r.droppedSourceTexts()).isEmpty();
    }

    // Doan chu luu o dang normalizeWhitespace (K2) - R-D1 so sanh tren dang nay.
    @Test
    void sourceTexts_storedWhitespaceNormalized() {
        assertThat(locations(c("HA_NOI", "  ở  Hà\nNội ")).acceptedSourceTexts()).containsExactly("ở Hà Nội");
    }

    // Mot doan dung chung cho ma nhan + ma bo -> co mat o ca hai tap (R-D1 giu lai doan nay).
    @Test
    void sharedSourceText_acceptedAndDropped() {
        Resolution r = locations(c("HA_NOI", "ở Hà Nội hoặc Sao Hoả"), c("SAO_HOA", "ở Hà Nội hoặc Sao Hoả"));

        assertThat(r.locationCodes()).containsExactly("HA_NOI");
        assertThat(r.acceptedSourceTexts()).containsExactly("ở Hà Nội hoặc Sao Hoả");
        assertThat(r.droppedSourceTexts()).containsExactly("ở Hà Nội hoặc Sao Hoả");
    }

    // ---- R-C2 nganh ----

    @Test
    void industry_validAccepted_otherDropped() {
        Resolution r = industries(c("IT_SOFTWARE", "IT"), c("OTHER", "ngành khác"));

        assertThat(r.industryCodes()).containsExactly("IT_SOFTWARE");
        assertThat(r.droppedSourceTexts()).containsExactly("ngành khác");
    }

    @Test
    void industry_otherAlone_dropped() {
        assertThat(industries(c("OTHER", "x")).industryCodes()).isEmpty();
    }

    @Test
    void industry_provinceCodeOrUnknown_dropped() {
        assertThat(industries(c("HA_NOI", "x"), c("KHONG_CO", "y")).industryCodes()).isEmpty();
    }

    @Test
    void industry_threeAccepted_fourthDropped() {
        Resolution r = industries(
                c("IT_SOFTWARE", "a"), c("ACCOUNTING_AUDIT", "b"), c("MARKETING", "c"), c("SALES", "d"));

        assertThat(r.industryCodes()).containsExactly("IT_SOFTWARE", "ACCOUNTING_AUDIT", "MARKETING");
        assertThat(r.droppedSourceTexts()).containsExactly("d");
    }

    // ---- R-C3 hinh thuc ----

    @Test
    void workMode_validAccepted_dedup_allThree() {
        Resolution r = workModes(c("REMOTE", "a"), c("HYBRID", "b"), c("REMOTE", "c"), c("ONSITE", "d"));

        assertThat(r.workModes()).containsExactly("REMOTE", "HYBRID", "ONSITE");
    }

    @Test
    void workMode_officeLowercaseOrNull_dropped() {
        Resolution r = workModes(c("OFFICE", "a"), c("remote", "b"), c(null, "c"));

        assertThat(r.workModes()).isEmpty();
        assertThat(r.droppedSourceTexts()).containsExactly("a", "b", "c");
    }

    // ---- R-C4 luong: bien 0/-1, 1000/1001, min > max ----

    @Test
    void salary_min0Accepted_minMinus1Dropped() {
        assertThat(salary(0, null).salaryMin()).isEqualTo(0);
        Resolution negative = salary(-1, null);
        assertThat(negative.hasSalary()).isFalse();
        assertThat(negative.droppedSourceTexts()).containsExactly("lương");
    }

    @Test
    void salary_1000Accepted_1001Dropped() {
        assertThat(salary(1000, null).salaryMin()).isEqualTo(1000);
        assertThat(salary(null, 1000).salaryMax()).isEqualTo(1000);
        assertThat(salary(1001, null).hasSalary()).isFalse();
        assertThat(salary(null, 1001).hasSalary()).isFalse();
        assertThat(salary(null, -1).hasSalary()).isFalse();
    }

    // min > max -> bo CA HAI; min = max van nhan; mot moc hop le + mot moc ngoai khoang -> bo ca hai.
    @Test
    void salary_minGreaterThanMax_bothDropped_equalAccepted() {
        Resolution r = salary(20, 15);
        assertThat(r.salaryMin()).isNull();
        assertThat(r.salaryMax()).isNull();
        assertThat(r.hasSalary()).isFalse();

        Resolution equal = salary(15, 15);
        assertThat(equal.salaryMin()).isEqualTo(15);
        assertThat(equal.salaryMax()).isEqualTo(15);

        Resolution mixed = salary(10, 1001);
        assertThat(mixed.salaryMin()).isNull();
        assertThat(mixed.salaryMax()).isNull();
    }

    @Test
    void salary_onlyMin_onlyMax_accepted() {
        Resolution onlyMin = salary(15, null);
        assertThat(onlyMin.salaryMin()).isEqualTo(15);
        assertThat(onlyMin.salaryMax()).isNull();
        assertThat(onlyMin.acceptedSourceTexts()).containsExactly("lương");

        Resolution onlyMax = salary(null, 20);
        assertThat(onlyMax.salaryMin()).isNull();
        assertThat(onlyMax.salaryMax()).isEqualTo(20);
    }

    @Test
    void salary_null_noSalaryNoTexts() {
        Resolution r = resolver.resolve(new JobSearchParsePayload(List.of(), List.of(), null, List.of(), List.of()));

        assertThat(r.hasSalary()).isFalse();
        assertThat(r.acceptedSourceTexts()).isEmpty();
        assertThat(r.droppedSourceTexts()).isEmpty();
    }

    // ---- R-C5: nhan than chi di qua, khong thanh dieu kien ----

    @Test
    void personalInfo_passedThrough_neverACondition() {
        Resolution r = resolver.resolve(new JobSearchParsePayload(
                List.of(), List.of(), null, List.of(), List.of("nữ dưới 30 tuổi")));

        assertThat(r.personalInfoTexts()).containsExactly("nữ dưới 30 tuổi");
        assertThat(r.locationCodes()).isEmpty();
        assertThat(r.industryCodes()).isEmpty();
        assertThat(r.workModes()).isEmpty();
        assertThat(r.hasSalary()).isFalse();
        assertThat(r.acceptedSourceTexts()).isEmpty();
    }
}
