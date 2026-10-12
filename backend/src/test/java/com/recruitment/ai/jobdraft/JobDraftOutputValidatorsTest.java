package com.recruitment.ai.jobdraft;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.jobdraft.CriteriaDraftPayload.CriterionDraft;
import com.recruitment.ai.jobdraft.QuestionsDraftPayload.QuestionDraft;
import com.recruitment.screening.ScreeningQuestionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

// FR-H11 T5 (phan unit), T7, T8 (phan unit) - ham validate cua ba phan, khong Spring. false = output hong (K3 thu lai 1
// lan). Phan parse (enum la, ngay sai) va muc HTTP (so lan goi model, o FAILED) kiem o dot 4.
class JobDraftOutputValidatorsTest {

    // ---- T5 - phan JOB (R-P3) ----

    private static JobDraftPayload job(String title, String description, String requirements, String benefits,
            BigDecimal salaryMin, BigDecimal salaryMax, String currency) {
        return new JobDraftPayload(title, description, requirements, benefits, "IT_SOFTWARE", "Đà Nẵng",
                DraftEmploymentType.FULL_TIME, DraftWorkMode.ONSITE, salaryMin, salaryMax, currency,
                LocalDate.of(2026, 11, 30));
    }

    private static boolean validText(String title, String description, String requirements, String benefits) {
        return JobDraftOutputValidators.isValidJob(job(title, description, requirements, benefits, null, null, null));
    }

    private static boolean validSalary(String min, String max, String currency) {
        return JobDraftOutputValidators.isValidJob(job("Tieu de", "Mo ta", null, null,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), currency));
    }

    @Test
    void job_nullPayload_invalid_allNullFields_valid() {
        assertThat(JobDraftOutputValidators.isValidJob(null)).isFalse();
        assertThat(JobDraftOutputValidators.isValidJob(
                        new JobDraftPayload(null, null, null, null, null, null, null, null, null, null, null, null)))
                .isTrue();
    }

    @Test
    void job_titleBoundary_200Valid_201Invalid() {
        assertThat(validText("a".repeat(199), "Mo ta", null, null)).isTrue();
        assertThat(validText("a".repeat(200), "Mo ta", null, null)).isTrue();
        assertThat(validText("a".repeat(201), "Mo ta", null, null)).isFalse();
        // Do sau khi bo khoang trang hai dau.
        assertThat(validText("  " + "a".repeat(200) + "  ", "Mo ta", null, null)).isTrue();
    }

    @Test
    void job_descriptionRequirementsBenefitsBoundaries() {
        assertThat(validText("T", "a".repeat(1999), null, null)).isTrue();
        assertThat(validText("T", "a".repeat(2000), null, null)).isTrue();
        assertThat(validText("T", "a".repeat(2001), null, null)).isFalse();
        assertThat(validText("T", "M", "a".repeat(1499), null)).isTrue();
        assertThat(validText("T", "M", "a".repeat(1500), null)).isTrue();
        assertThat(validText("T", "M", "a".repeat(1501), null)).isFalse();
        assertThat(validText("T", "M", null, "a".repeat(999))).isTrue();
        assertThat(validText("T", "M", null, "a".repeat(1000))).isTrue();
        assertThat(validText("T", "M", null, "a".repeat(1001))).isFalse();
    }

    // CRLF dem la 1 ky tu (doi sang LF truoc khi do): 1998 + CRLF + 'a' = 2000 sau chuan hoa -> hop le;
    // 1999 + CRLF + 'a' = 2001 sau chuan hoa -> hong.
    @Test
    void job_lengthMeasuredAfterCrlfNormalization() {
        assertThat(validText("T", "a".repeat(1998) + "\r\n" + "a", null, null)).isTrue();
        assertThat(validText("T", "a".repeat(1999) + "\r\n" + "a", null, null)).isFalse();
    }

    @Test
    void job_salaryBoundaries() {
        assertThat(validSalary("-1", null, "VND")).isFalse();
        assertThat(validSalary(null, "-1", "VND")).isFalse();
        assertThat(validSalary("-0.01", null, "VND")).isFalse();
        assertThat(validSalary("0", null, "VND")).isTrue();
        assertThat(validSalary(null, "999999999999.99", "VND")).isTrue();
        assertThat(validSalary(null, "1000000000000", "VND")).isFalse();
        assertThat(validSalary("1000000000000", null, "VND")).isFalse();
    }

    @Test
    void job_salaryRange() {
        assertThat(validSalary("20000000", "15000000", "VND")).isFalse();
        assertThat(validSalary("15000000", "14999999.99", "VND")).isFalse();
        assertThat(validSalary("15000000", "15000000", "VND")).isTrue();
        assertThat(validSalary("15000000", "20000000", "VND")).isTrue();
        assertThat(validSalary("15000000", null, "VND")).isTrue();
        assertThat(validSalary(null, "20000000", "VND")).isTrue();
    }

    @Test
    void job_currencyFormat() {
        assertThat(validSalary("1", "2", "USD")).isTrue();
        assertThat(validSalary("1", "2", "VND")).isTrue();
        assertThat(validSalary("1", "2", null)).isTrue();
        assertThat(validSalary("1", "2", "vnd")).isFalse();
        assertThat(validSalary("1", "2", "VN")).isFalse();
        assertThat(validSalary("1", "2", "VNDD")).isFalse();
        assertThat(validSalary("1", "2", "V1D")).isFalse();
        // Chi khoang trang = coi nhu null (facade vo hieu sau - R-P4), khong lam hong output.
        assertThat(validSalary("1", "2", "   ")).isTrue();
    }

    // ---- T7 - phan CRITERIA (R-C2) ----

    private static List<CriterionDraft> criteria(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new CriterionDraft("Tieu chi " + i, "Mo ta " + i)).toList();
    }

    private static boolean validCriteria(List<CriterionDraft> list) {
        return JobDraftOutputValidators.isValidCriteria(new CriteriaDraftPayload(list));
    }

    @Test
    void criteria_countBoundaries_zeroAndSixValid_sevenInvalid() {
        assertThat(validCriteria(List.of())).isTrue();
        assertThat(validCriteria(null)).isTrue();
        assertThat(validCriteria(criteria(5))).isTrue();
        assertThat(validCriteria(criteria(6))).isTrue();
        assertThat(validCriteria(criteria(7))).isFalse();
        assertThat(JobDraftOutputValidators.isValidCriteria(null)).isFalse();
    }

    @Test
    void criteria_nameBoundaries() {
        assertThat(validCriteria(List.of(new CriterionDraft("a".repeat(99), null)))).isTrue();
        assertThat(validCriteria(List.of(new CriterionDraft("a".repeat(100), null)))).isTrue();
        assertThat(validCriteria(List.of(new CriterionDraft(" " + "a".repeat(100) + " ", null)))).isTrue();
        assertThat(validCriteria(List.of(new CriterionDraft("a".repeat(101), null)))).isFalse();
        assertThat(validCriteria(List.of(new CriterionDraft(null, "Mo ta")))).isFalse();
        assertThat(validCriteria(List.of(new CriterionDraft("", "Mo ta")))).isFalse();
        assertThat(validCriteria(List.of(new CriterionDraft("   ", "Mo ta")))).isFalse();
    }

    @Test
    void criteria_descriptionBoundaries() {
        assertThat(validCriteria(List.of(new CriterionDraft("Java", "a".repeat(299))))).isTrue();
        assertThat(validCriteria(List.of(new CriterionDraft("Java", "a".repeat(300))))).isTrue();
        assertThat(validCriteria(List.of(new CriterionDraft("Java", "a".repeat(301))))).isFalse();
    }

    @Test
    void criteria_duplicateNamesIgnoringCaseAndSpaces_invalid_nullElement_invalid() {
        assertThat(validCriteria(List.of(new CriterionDraft("Kinh nghiệm Java", "a"),
                        new CriterionDraft(" kinh nghiệm JAVA ", "b"))))
                .isFalse();
        List<CriterionDraft> withNull = new ArrayList<>(criteria(1));
        withNull.add(null);
        assertThat(validCriteria(withNull)).isFalse();
    }

    // ---- T8 - phan QUESTIONS (R-Q2) ----

    private static QuestionDraft yesNo(String text) {
        return new QuestionDraft(ScreeningQuestionType.YES_NO, text, List.of());
    }

    private static QuestionDraft single(String text, List<String> options) {
        return new QuestionDraft(ScreeningQuestionType.SINGLE_CHOICE, text, options);
    }

    private static List<String> options(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Phuong an " + i).toList();
    }

    private static boolean validQuestions(List<QuestionDraft> list) {
        return JobDraftOutputValidators.isValidQuestions(new QuestionsDraftPayload(list));
    }

    @Test
    void questions_countBoundaries_fiveValid_sixInvalid() {
        List<QuestionDraft> six = IntStream.rangeClosed(1, 6).mapToObj(i -> yesNo("Cau hoi " + i)).toList();
        assertThat(validQuestions(List.of())).isTrue();
        assertThat(validQuestions(null)).isTrue();
        assertThat(validQuestions(six.subList(0, 4))).isTrue();
        assertThat(validQuestions(six.subList(0, 5))).isTrue();
        assertThat(validQuestions(six)).isFalse();
        assertThat(JobDraftOutputValidators.isValidQuestions(null)).isFalse();
    }

    @Test
    void questions_singleChoiceOptionCount_usesFrH10Rules() {
        assertThat(validQuestions(List.of(single("Cau hoi", options(1))))).isFalse();
        assertThat(validQuestions(List.of(single("Cau hoi", options(2))))).isTrue();
        assertThat(validQuestions(List.of(single("Cau hoi", options(10))))).isTrue();
        assertThat(validQuestions(List.of(single("Cau hoi", options(11))))).isFalse();
        assertThat(validQuestions(List.of(single("Cau hoi", null)))).isFalse();
    }

    @Test
    void questions_optionLengthAndEmptyAndDuplicate() {
        assertThat(validQuestions(List.of(single("Cau hoi", List.of("a".repeat(99), "b"))))).isTrue();
        assertThat(validQuestions(List.of(single("Cau hoi", List.of("a".repeat(100), "b"))))).isTrue();
        assertThat(validQuestions(List.of(single("Cau hoi", List.of("a".repeat(101), "b"))))).isFalse();
        assertThat(validQuestions(List.of(single("Cau hoi", List.of("Co", " "))))).isFalse();
        assertThat(validQuestions(List.of(single("Cau hoi", List.of("Có", " có "))))).isFalse();
    }

    @Test
    void questions_optionsOnlyForSingleChoice() {
        assertThat(validQuestions(List.of(new QuestionDraft(ScreeningQuestionType.YES_NO, "Cau hoi", List.of("Co")))))
                .isFalse();
        assertThat(validQuestions(List.of(new QuestionDraft(ScreeningQuestionType.SHORT_TEXT, "Cau hoi", List.of("x")))))
                .isFalse();
        assertThat(validQuestions(List.of(new QuestionDraft(ScreeningQuestionType.SHORT_TEXT, "Cau hoi", null))))
                .isTrue();
    }

    @Test
    void questions_textBoundaries() {
        assertThat(validQuestions(List.of(yesNo("a".repeat(299))))).isTrue();
        assertThat(validQuestions(List.of(yesNo("a".repeat(300))))).isTrue();
        assertThat(validQuestions(List.of(yesNo("a".repeat(301))))).isFalse();
        assertThat(validQuestions(List.of(yesNo("  ")))).isFalse();
        assertThat(validQuestions(List.of(yesNo(null)))).isFalse();
    }

    @Test
    void questions_duplicateTextIgnoringCaseAndSpaces_invalid_nullTypeOrElement_invalid() {
        assertThat(validQuestions(List.of(yesNo("Bạn có thể đi làm tại Đà Nẵng?"),
                        new QuestionDraft(ScreeningQuestionType.SHORT_TEXT, " bạn có thể đi làm tại đà nẵng? ", null))))
                .isFalse();
        assertThat(validQuestions(List.of(new QuestionDraft(null, "Cau hoi", List.of())))).isFalse();
        List<QuestionDraft> withNull = new ArrayList<>(List.of(yesNo("Cau hoi")));
        withNull.add(null);
        assertThat(validQuestions(withNull)).isFalse();
    }
}
