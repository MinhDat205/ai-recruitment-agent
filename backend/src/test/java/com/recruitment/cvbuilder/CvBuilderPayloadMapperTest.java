package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataEnricher;
import com.recruitment.resume.ResumeParsedPayload;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// FR-U12 T5 (R-M1-R-M3). Mapper la lop thuan; phan R-M3 chay qua ResumeParsedDataEnricher THAT (bean Spring, danh muc
// that cua V8 nap tu DB) - khong ghi DB.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CvBuilderPayloadMapperTest {

    private final CvBuilderPayloadMapper mapper = new CvBuilderPayloadMapper();

    @Autowired
    private ResumeParsedDataEnricher enricher;

    private static CvBuilderForm fullForm(String locationText) {
        return new CvBuilderForm(
                new CvBuilderForm.Contact("Nguyễn Quốc Huy", "huy@example.com", "0901 234 567", "Quận 1", "linkedin.com/in/x"),
                "Chuyên viên CSKH",
                locationText,
                "IT_SOFTWARE",
                List.of(new CvBuilderForm.Education("ĐH Kinh tế", "Cử nhân", "QTKD", "09/2015", "06/2019", 3.2)),
                List.of(
                        new CvBuilderForm.Experience("Công ty A", "CSKH", "03/2019", null, true, "Dòng 1\nDòng 2"),
                        new CvBuilderForm.Experience("Công ty B", null, "2019", null, null, null),
                        new CvBuilderForm.Experience("Công ty C", "Dev", "01/2018", "02/2018", false, "<Java> & Spring")),
                List.of("iOS", "C#", "Node.js"),
                List.of(new CvBuilderForm.Certification("IELTS", "IDP", "2020")),
                List.of(new CvBuilderForm.Project("Bot", "Tăng 30%", List.of("Java", "Zalo OA"))));
    }

    @Test
    void toPayload_copiesEveryStringVerbatim() {
        CvBuilderForm form = fullForm("Hà Nội");

        ResumeParsedPayload p = mapper.toPayload(form);

        assertThat(p.contact()).isEqualTo(new ResumeParsedPayload.Contact(
                "Nguyễn Quốc Huy", "huy@example.com", "0901 234 567", "Quận 1", "linkedin.com/in/x"));
        assertThat(p.currentTitle()).isEqualTo("Chuyên viên CSKH");
        assertThat(p.locationText()).isEqualTo("Hà Nội");
        assertThat(p.industryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(p.education()).containsExactly(
                new ResumeParsedPayload.Education("ĐH Kinh tế", "Cử nhân", "QTKD", "09/2015", "06/2019", 3.2));
        assertThat(p.experience()).containsExactly(
                new ResumeParsedPayload.Experience("Công ty A", "CSKH", "03/2019", "Hiện tại", "Dòng 1\nDòng 2"),
                new ResumeParsedPayload.Experience("Công ty B", null, "2019", null, null),
                new ResumeParsedPayload.Experience("Công ty C", "Dev", "01/2018", "02/2018", "<Java> & Spring"));
        assertThat(p.skills()).containsExactly("iOS", "C#", "Node.js");
        assertThat(p.certifications()).containsExactly(new ResumeParsedPayload.Certification("IELTS", "IDP", "2020"));
        assertThat(p.projects()).containsExactly(
                new ResumeParsedPayload.Project("Bot", "Tăng 30%", List.of("Java", "Zalo OA")));
    }

    @Test
    void current_true_endDateIsHienTai_falseOrNull_keepsEndDate() {
        assertThat(CvBuilderPayloadMapper.CURRENT_END_DATE).isEqualTo("Hiện tại");
        ResumeParsedPayload p = mapper.toPayload(fullForm(null));

        assertThat(p.experience().get(0).endDate()).isEqualTo("Hiện tại");
        assertThat(p.experience().get(1).endDate()).isNull();
        assertThat(p.experience().get(2).endDate()).isEqualTo("02/2018");
    }

    @Test
    void gpa_keptAsDouble() {
        Double gpa = mapper.toPayload(fullForm(null)).education().get(0).gpa();

        assertThat(gpa).isInstanceOf(Double.class).isEqualTo(3.2);
    }

    @Test
    void allContactFieldsNull_contactNull() {
        CvBuilderForm form = new CvBuilderForm(
                new CvBuilderForm.Contact(null, null, null, null, null), null, null, null, null, null,
                List.of("Java"), null, null);

        ResumeParsedPayload p = mapper.toPayload(form);

        assertThat(p.contact()).isNull();
        assertThat(mapper.toPayload(new CvBuilderForm(null, null, null, null, null, null, null, null, null)).contact())
                .isNull();
    }

    @Test
    void emptyLists_stayEmpty() {
        ResumeParsedPayload p = mapper.toPayload(new CvBuilderForm(
                new CvBuilderForm.Contact("A", null, null, null, null), null, null, null, null, null, null, null, null));

        assertThat(p.education()).isEmpty();
        assertThat(p.experience()).isEmpty();
        assertThat(p.skills()).isEmpty();
        assertThat(p.certifications()).isEmpty();
        assertThat(p.projects()).isEmpty();
        assertThat(p.currentTitle()).isNull();
        assertThat(p.industryCode()).isNull();
        assertThat(p.locationText()).isNull();
    }

    // ---- R-M3: qua enricher that ----

    private ResumeParsedData enrich(CvBuilderForm form) {
        ResumeParsedData target = new ResumeParsedData();
        enricher.applyExtraction(target, mapper.toPayload(form));
        return target;
    }

    @Test
    void enricher_countsHienTaiExperience_skipsYearOnly() {
        ResumeParsedData data = enrich(fullForm("Hà Nội"));

        // 03/2019 - Hien tai: tinh; 2019 (chi nam): bo qua; 01/2018-02/2018: tinh.
        assertThat(data.getExperienceEntriesCounted()).isEqualTo(2);
        assertThat(data.getExperienceEntriesSkipped()).isEqualTo(1);
        assertThat(data.getExperienceMonths()).isNotNull().isPositive();
        assertThat(data.getExperienceComputedAt()).isNotNull();
    }

    @Test
    void enricher_onlyHienTaiEntry_counted() {
        CvBuilderForm form = new CvBuilderForm(
                new CvBuilderForm.Contact("A", null, null, null, null), null, null, null, null,
                List.of(new CvBuilderForm.Experience("Cty", null, "03/2019", null, true, null)), null, null, null);

        ResumeParsedData data = enrich(form);

        assertThat(data.getExperienceEntriesCounted()).isEqualTo(1);
        assertThat(data.getExperienceEntriesSkipped()).isZero();
    }

    @Test
    void enricher_onlyYearEntry_skipped_monthsNull() {
        CvBuilderForm form = new CvBuilderForm(
                new CvBuilderForm.Contact("A", null, null, null, null), null, null, null, null,
                List.of(new CvBuilderForm.Experience("Cty", null, "2019", "2020", null, null)), null, null, null);

        ResumeParsedData data = enrich(form);

        assertThat(data.getExperienceEntriesCounted()).isZero();
        assertThat(data.getExperienceEntriesSkipped()).isEqualTo(1);
        assertThat(data.getExperienceMonths()).isNull();
    }

    @Test
    void enricher_haNoi_regionMatched_textKept() {
        ResumeParsedData data = enrich(fullForm("Hà Nội"));

        assertThat(data.getRegionCode()).isEqualTo("HA_NOI");
        assertThat(data.getData().locationText()).isEqualTo("Hà Nội");
        assertThat(data.getIndustryCode()).isEqualTo("IT_SOFTWARE");
    }

    @Test
    void enricher_unknownLocation_regionNull_textKept() {
        ResumeParsedData data = enrich(fullForm("Sao Hỏa"));

        assertThat(data.getRegionCode()).isNull();
        assertThat(data.getData().locationText()).isEqualTo("Sao Hỏa");
    }

    // Payload sau enricher giu nguyen chuoi cua form (enricher chi co the doi industryCode - ma hop le nen giu).
    @Test
    void enricher_keepsMappedPayload() {
        CvBuilderForm form = fullForm("Hà Nội");

        ResumeParsedData data = enrich(form);

        assertThat(data.getData()).isEqualTo(mapper.toPayload(form));
    }
}
