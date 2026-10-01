package com.recruitment.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

// Test don vi (khong Spring) cho ham ghi ket qua trich xuat DUNG CHUNG cho trich xuat lan dau va trich
// xuat lai (FR-C05 R-C3, R-C4, R-E9a). Danh muc la fixture nho, dong ho co dinh o 15/09/2026 (gio VN)
// de moc tham chieu la 09/2026 nhu bang R-E10.
class ResumeParsedDataEnricherTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T03:00:00Z");

    private static final CatalogRegistry REGISTRY = new CatalogRegistry(
            List.of(
                    new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of("Trí tuệ nhân tạo")),
                    new CatalogEntry("OTHER", "Ngành khác", List.of())),
            List.of(
                    new CatalogEntry("HA_NOI", "Hà Nội", List.of()),
                    new CatalogEntry("HO_CHI_MINH", "TP. Hồ Chí Minh", List.of("Bình Dương", "TP.HCM"))));

    private final ResumeParsedDataEnricher enricher =
            new ResumeParsedDataEnricher(REGISTRY, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    private static ResumeParsedPayload payload(String industryCode, String locationText) {
        return new ResumeParsedPayload(
                null,
                List.of(),
                List.of(new ResumeParsedPayload.Experience("Cong ty ABC", "Backend", "03/2024", "Hiện tại", "API")),
                List.of("Java"),
                List.of(),
                List.of(),
                "Senior Java Developer",
                industryCode,
                locationText);
    }

    private ResumeParsedData apply(ResumeParsedPayload aiPayload) {
        ResumeParsedData target = new ResumeParsedData();
        enricher.applyExtraction(target, aiPayload);
        return target;
    }

    // ---- R-C3/R-C4: ma nganh ----

    @Test
    void unknownIndustryCode_nullInJsonAndColumn() {
        ResumeParsedData data = apply(payload("NOT_A_CODE", null));

        assertThat(data.getData().industryCode()).isNull();
        assertThat(data.getIndustryCode()).isNull();
    }

    @Test
    void otherIndustryCode_nullInJsonAndColumn() {
        ResumeParsedData data = apply(payload("OTHER", null));

        assertThat(data.getData().industryCode()).isNull();
        assertThat(data.getIndustryCode()).isNull();
    }

    // Nhan (khong phai ma) bi coi nhu ma la.
    @Test
    void industryLabelInsteadOfCode_nullInJsonAndColumn() {
        ResumeParsedData data = apply(payload("Công nghệ thông tin - Phần mềm", null));

        assertThat(data.getData().industryCode()).isNull();
        assertThat(data.getIndustryCode()).isNull();
    }

    @Test
    void validIndustryCode_keptAndColumnEqualsJson() {
        ResumeParsedData data = apply(payload("IT_SOFTWARE", null));

        assertThat(data.getData().industryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(data.getIndustryCode()).isEqualTo(data.getData().industryCode());
    }

    // Cac field khac cua payload giu nguyen khi ma nganh bi thay bang null.
    @Test
    void sanitizingIndustry_keepsOtherFieldsVerbatim() {
        ResumeParsedData data = apply(payload("NOT_A_CODE", "Bình Dương"));

        assertThat(data.getData().currentTitle()).isEqualTo("Senior Java Developer");
        assertThat(data.getData().locationText()).isEqualTo("Bình Dương");
        assertThat(data.getData().skills()).containsExactly("Java");
        assertThat(data.getData().experience()).hasSize(1);
    }

    // ---- R-C3: khu vuc ----

    @Test
    void oldProvinceName_mapsToNewProvince_textKeptVerbatim() {
        ResumeParsedData data = apply(payload(null, "Bình Dương"));

        assertThat(data.getRegionCode()).isEqualTo("HO_CHI_MINH");
        assertThat(data.getData().locationText()).isEqualTo("Bình Dương");
    }

    @Test
    void unmatchedLocation_regionNull_textKeptVerbatim() {
        ResumeParsedData data = apply(payload(null, "Quận 1, Hồ Chí Minh"));

        assertThat(data.getRegionCode()).isNull();
        assertThat(data.getData().locationText()).isEqualTo("Quận 1, Hồ Chí Minh");
    }

    @Test
    void nullLocation_regionNull() {
        ResumeParsedData data = apply(payload(null, null));

        assertThat(data.getRegionCode()).isNull();
        assertThat(data.getData().locationText()).isNull();
    }

    // ---- R-E9a: kinh nghiem tinh cung luc ghi, moc lay tu Clock inject ----

    @Test
    void experienceComputedWithInjectedClock() {
        ResumeParsedData data = apply(payload("IT_SOFTWARE", null));

        // 03/2024 - Hien tai, refMonth 09/2026 -> 31 thang (ca 6 cua R-E10).
        assertThat(data.getExperienceMonths()).isEqualTo(31);
        assertThat(data.getExperienceEntriesCounted()).isEqualTo(1);
        assertThat(data.getExperienceEntriesSkipped()).isZero();
        assertThat(data.getExperienceComputedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void noReadableExperience_monthsNullNotZero() {
        ResumeParsedPayload noDates = new ResumeParsedPayload(
                null,
                List.of(),
                List.of(new ResumeParsedPayload.Experience("Cong ty", "Dev", "2019", "2021", null)),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);

        ResumeParsedData data = apply(noDates);

        assertThat(data.getExperienceMonths()).isNull();
        assertThat(data.getExperienceEntriesCounted()).isZero();
        assertThat(data.getExperienceEntriesSkipped()).isEqualTo(1);
        assertThat(data.getExperienceComputedAt()).isEqualTo(FIXED_NOW);
    }
}
