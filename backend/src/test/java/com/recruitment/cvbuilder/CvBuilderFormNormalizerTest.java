package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// FR-U12 T3 (R-F3) - test don vi, khong Spring.
class CvBuilderFormNormalizerTest {

    private final CvBuilderFormNormalizer normalizer = new CvBuilderFormNormalizer();

    private static CvBuilderForm formWith(
            CvBuilderForm.Contact contact,
            List<CvBuilderForm.Experience> experience,
            List<String> skills,
            List<CvBuilderForm.Project> projects) {
        return new CvBuilderForm(contact, null, null, null, null, experience, skills, null, projects);
    }

    @Test
    void nfdInput_becomesNfc() {
        String nfd = "Nguyễn"; // "Nguyễn" dang NFD (e + mu + nga)
        assertThat(nfd).isNotEqualTo("Nguyễn");

        assertThat(normalizer.singleLine(nfd)).isEqualTo("Nguyễn");
        assertThat(normalizer.singleLine("Nguyên")).isEqualTo("Nguyên");
    }

    @Test
    void crlfAndCr_becomeLf_inMultiLine() {
        assertThat(normalizer.multiLine("a\r\nb\rc\nd")).isEqualTo("a\nb\nc\nd");
    }

    @Test
    void tabAndUnicodeSpaces_becomePlainSpace() {
        assertThat(normalizer.singleLine("a\tb c d e")).isEqualTo("a b c d e");
        assertThat(normalizer.multiLine("a\tb c")).isEqualTo("a b c");
    }

    @Test
    void newlineInSingleLineField_becomesSpace() {
        assertThat(normalizer.singleLine("Công ty\r\nABC\nXYZ")).isEqualTo("Công ty ABC XYZ");
    }

    @Test
    void newlineInDescription_kept() {
        CvBuilderForm form = formWith(
                new CvBuilderForm.Contact("A", null, null, null, null),
                List.of(new CvBuilderForm.Experience("Cty\nA", null, null, null, null, "Dòng 1\r\n\r\nDòng 3")),
                null,
                List.of(new CvBuilderForm.Project("P\n1", "Mô tả\ndự án", List.of("Java\nSpring"))));

        CvBuilderForm out = normalizer.normalize(form);

        assertThat(out.experience().get(0).company()).isEqualTo("Cty A");
        assertThat(out.experience().get(0).description()).isEqualTo("Dòng 1\n\nDòng 3");
        assertThat(out.projects().get(0).name()).isEqualTo("P 1");
        assertThat(out.projects().get(0).description()).isEqualTo("Mô tả\ndự án");
        assertThat(out.projects().get(0).technologies()).containsExactly("Java Spring");
    }

    @Test
    void stripBothEnds_includingUnicodeSpaces() {
        assertThat(normalizer.singleLine("   Java  \t")).isEqualTo("Java");
        assertThat(normalizer.multiLine("\n\n  Mô tả  \n")).isEqualTo("Mô tả");
    }

    @Test
    void blankOptional_becomesNull() {
        assertThat(normalizer.singleLine("   ")).isNull();
        assertThat(normalizer.singleLine(" \t")).isNull();
        assertThat(normalizer.multiLine("\r\n \n")).isNull();
        assertThat(normalizer.singleLine("")).isNull();
        assertThat(normalizer.singleLine(null)).isNull();
    }

    @Test
    void caseAndInnerSpacing_unchanged() {
        assertThat(normalizer.singleLine("iOS")).isEqualTo("iOS");
        assertThat(normalizer.singleLine("JAVA")).isEqualTo("JAVA");
        assertThat(normalizer.singleLine("a   b")).isEqualTo("a   b");
    }

    @Test
    void normalize_appliesToEveryStringIncludingSkillsAndTechnologies_keepsNullElementsAndOrder() {
        CvBuilderForm form = new CvBuilderForm(
                new CvBuilderForm.Contact(" Nguyên ", " a@b ", "  ", "Q1\nHCM", "\tlinkedin.com/in/x"),
                " Dev ",
                " Hà Nội ",
                " IT_SOFTWARE ",
                Arrays.asList(new CvBuilderForm.Education(" ĐH ", " ", null, " 09/2015 ", "06/2019 ", 3.2), null),
                null,
                Arrays.asList(" Java ", null, "  ", "iOS"),
                List.of(new CvBuilderForm.Certification(" IELTS ", " IDP ", " 2020 ")),
                null);

        CvBuilderForm out = normalizer.normalize(form);

        assertThat(out.contact()).isEqualTo(new CvBuilderForm.Contact("Nguyên", "a@b", null, "Q1 HCM", "linkedin.com/in/x"));
        assertThat(out.currentTitle()).isEqualTo("Dev");
        assertThat(out.locationText()).isEqualTo("Hà Nội");
        assertThat(out.industryCode()).isEqualTo("IT_SOFTWARE");
        assertThat(out.education()).hasSize(2);
        assertThat(out.education().get(0)).isEqualTo(new CvBuilderForm.Education("ĐH", null, null, "09/2015", "06/2019", 3.2));
        assertThat(out.education().get(1)).isNull();
        assertThat(out.skills()).containsExactly("Java", null, null, "iOS");
        assertThat(out.certifications().get(0)).isEqualTo(new CvBuilderForm.Certification("IELTS", "IDP", "2020"));
        assertThat(out.experience()).isEmpty();
        assertThat(out.projects()).isEmpty();
    }

    @Test
    void normalize_nullFormOrContact() {
        assertThat(normalizer.normalize(null)).isNull();
        CvBuilderForm out = normalizer.normalize(formWith(null, null, null, null));
        assertThat(out.contact()).isNull();
    }

    @Test
    void experienceCurrentAndGpa_untouched() {
        CvBuilderForm out = normalizer.normalize(formWith(
                null, List.of(new CvBuilderForm.Experience("A", null, "03/2019", null, Boolean.TRUE, null)), null, null));

        assertThat(out.experience().get(0).current()).isTrue();
    }
}
