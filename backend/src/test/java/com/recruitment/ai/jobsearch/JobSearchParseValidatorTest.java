package com.recruitment.ai.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.recruitment.ai.jobsearch.JobSearchParsePayload.CodeCondition;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.SalaryCondition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

// FR-U13 T5 (unit, khong Spring): ham kiem output R-V. false -> K3 coi la output hong, thu lai 1 lan (hanh vi thu lai
// da kiem o SyncAiCallerTest). Ham TONG - moi ca null deu khang dinh khong nem exception.
class JobSearchParseValidatorTest {

    // Cau DA chuan hoa R-I2.
    private static final String Q = "Kế toán ở Hà Nội lương trên 15 triệu, làm từ xa, nữ dưới 30 tuổi";

    private static boolean valid(JobSearchParsePayload payload) {
        return JobSearchParseValidator.isValid(payload, Q);
    }

    private static JobSearchParsePayload locations(CodeCondition... conditions) {
        return new JobSearchParsePayload(Arrays.asList(conditions), List.of(), null, List.of(), List.of());
    }

    private static JobSearchParsePayload personal(String... texts) {
        return new JobSearchParsePayload(List.of(), List.of(), null, List.of(), Arrays.asList(texts));
    }

    private static JobSearchParsePayload salary(SalaryCondition salary) {
        return new JobSearchParsePayload(List.of(), List.of(), salary, List.of(), List.of());
    }

    private static List<CodeCondition> repeat(CodeCondition condition, int times) {
        List<CodeCondition> list = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            list.add(condition);
        }
        return list;
    }

    // ---- hop le ----

    @Test
    void emptyListsAndNullSalary_valid() {
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(), null, List.of(), List.of()))).isTrue();
    }

    // Danh sach null (vang mat trong JSON) -> compact constructor doi thanh [] -> hop le.
    @Test
    void nullLists_normalizedToEmpty_valid() {
        JobSearchParsePayload payload = new JobSearchParsePayload(null, null, null, null, null);
        assertThat(payload.locations()).isEmpty();
        assertThat(payload.industries()).isEmpty();
        assertThat(payload.workModes()).isEmpty();
        assertThat(payload.personalInfoTexts()).isEmpty();
        assertThat(valid(payload)).isTrue();
    }

    @Test
    void fullPayload_allVerbatim_valid() {
        JobSearchParsePayload payload = new JobSearchParsePayload(
                List.of(new CodeCondition("HA_NOI", "ở Hà Nội")),
                List.of(new CodeCondition("ACCOUNTING_AUDIT", "Kế toán")),
                new SalaryCondition(15, null, "lương trên 15 triệu"),
                List.of(new CodeCondition("REMOTE", "làm từ xa")),
                List.of("nữ dưới 30 tuổi"));
        assertThat(valid(payload)).isTrue();
    }

    // ---- R-V2: nguyen van (K2) ----

    @Test
    void sourceTextMissingDiacritic_invalid() {
        assertThat(valid(locations(new CodeCondition("HA_NOI", "ở Ha Noi")))).isFalse();
    }

    @Test
    void sourceTextDifferentCase_invalid() {
        assertThat(valid(locations(new CodeCondition("HA_NOI", "Ở hà nội")))).isFalse();
    }

    // K2 gop khoang trang: khac khoang trang van nguyen van.
    @Test
    void sourceTextDiffersOnlyInWhitespace_valid() {
        assertThat(valid(locations(new CodeCondition("HA_NOI", "  ở   Hà\tNội\n")))).isTrue();
    }

    @Test
    void sourceTextNullEmptyOrBlank_invalid_noException() {
        for (String text : Arrays.asList(null, "", "   ", "\n\t")) {
            JobSearchParsePayload payload = locations(new CodeCondition("HA_NOI", text));
            assertThatCode(() -> valid(payload)).as("[%s]", text).doesNotThrowAnyException();
            assertThat(valid(payload)).as("[%s]", text).isFalse();
        }
    }

    // Mot doan khong nguyen van la ca output hong (moi loai dieu kien).
    @Test
    void oneNonVerbatimAmongGood_wholeOutputInvalid() {
        assertThat(valid(locations(
                new CodeCondition("HA_NOI", "ở Hà Nội"), new CodeCondition("DA_NANG", "ở Đà Nẵng")))).isFalse();
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(new CodeCondition("IT_SOFTWARE", "CNTT")),
                null, List.of(), List.of()))).isFalse();
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(), null,
                List.of(new CodeCondition("REMOTE", "remote")), List.of()))).isFalse();
    }

    @Test
    void personalInfoTexts_verbatimValid_nonVerbatimInvalid() {
        assertThat(valid(personal("nữ dưới 30 tuổi"))).isTrue();
        assertThat(valid(personal("nữ dưới 30 tuổi", "giới tính nữ"))).isFalse();
    }

    @Test
    void personalInfoTexts_emptyOrNullElement_invalid_noException() {
        assertThat(valid(personal(""))).isFalse();
        assertThatCode(() -> valid(personal((String) null))).doesNotThrowAnyException();
        assertThat(valid(personal((String) null))).isFalse();
    }

    // ---- R-V1: bien so phan tu 10/11, phan tu null ----

    @Test
    void listSize_10Valid_11Invalid_eachList() {
        CodeCondition ok = new CodeCondition("HA_NOI", "ở Hà Nội");
        assertThat(valid(new JobSearchParsePayload(repeat(ok, 10), List.of(), null, List.of(), List.of()))).isTrue();
        assertThat(valid(new JobSearchParsePayload(repeat(ok, 11), List.of(), null, List.of(), List.of()))).isFalse();
        assertThat(valid(new JobSearchParsePayload(List.of(), repeat(ok, 10), null, List.of(), List.of()))).isTrue();
        assertThat(valid(new JobSearchParsePayload(List.of(), repeat(ok, 11), null, List.of(), List.of()))).isFalse();
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(), null, repeat(ok, 10), List.of()))).isTrue();
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(), null, repeat(ok, 11), List.of()))).isFalse();

        List<String> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ten.add("nữ dưới 30 tuổi");
        }
        assertThat(valid(personal(ten.toArray(String[]::new)))).isTrue();
        ten.add("nữ dưới 30 tuổi");
        assertThat(valid(personal(ten.toArray(String[]::new)))).isFalse();
    }

    @Test
    void nullConditionElement_invalid_noException() {
        JobSearchParsePayload payload = locations(new CodeCondition("HA_NOI", "ở Hà Nội"), null);
        assertThatCode(() -> valid(payload)).doesNotThrowAnyException();
        assertThat(valid(payload)).isFalse();
    }

    // ---- R-V3: luong ----

    @Test
    void salary_bothNumbersNull_invalid() {
        assertThat(valid(salary(new SalaryCondition(null, null, "lương trên 15 triệu")))).isFalse();
    }

    @Test
    void salary_onlyMinOrOnlyMax_valid() {
        assertThat(valid(salary(new SalaryCondition(15, null, "lương trên 15 triệu")))).isTrue();
        assertThat(valid(salary(new SalaryCondition(null, 15, "lương trên 15 triệu")))).isTrue();
    }

    @Test
    void salary_sourceTextNullOrNotVerbatim_invalid_noException() {
        assertThatCode(() -> valid(salary(new SalaryCondition(15, null, null)))).doesNotThrowAnyException();
        assertThat(valid(salary(new SalaryCondition(15, null, null)))).isFalse();
        assertThat(valid(salary(new SalaryCondition(15, null, "lương từ 15 triệu")))).isFalse();
    }

    // Luong am, min > max KHONG phai output hong (R-C bo dieu kien, giu chu).
    @Test
    void salary_negativeOrMinGreaterThanMax_notInvalid() {
        assertThat(valid(salary(new SalaryCondition(-1, null, "lương trên 15 triệu")))).isTrue();
        assertThat(valid(salary(new SalaryCondition(20, 10, "lương trên 15 triệu")))).isTrue();
    }

    // ---- ma la / OTHER khong phai output hong ----

    @Test
    void unknownCodeOrOtherOrNullCode_notInvalid() {
        assertThat(valid(locations(new CodeCondition("SAO_HOA", "ở Hà Nội")))).isTrue();
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(new CodeCondition("OTHER", "Kế toán")),
                null, List.of(), List.of()))).isTrue();
        assertThat(valid(new JobSearchParsePayload(List.of(), List.of(), null,
                List.of(new CodeCondition("OFFICE", "làm từ xa")), List.of()))).isTrue();
        assertThat(valid(locations(new CodeCondition(null, "ở Hà Nội")))).isTrue();
    }

    // ---- R-P2: doi nguoc ‹ › truoc khi kiem ----

    @Test
    void guillemetsInOutput_unsanitizedBeforeK2_valid() {
        String q = "Lập trình viên <b>Java</b> ở Hà Nội";
        JobSearchParsePayload payload = new JobSearchParsePayload(
                List.of(new CodeCondition("HA_NOI", "‹b›Java‹/b› ở Hà Nội")), List.of(), null, List.of(),
                List.of());
        assertThat(JobSearchParseValidator.isValid(payload, q)).isTrue();
    }

    // Khong doi nguoc thi ‹b› khong co trong cau -> chung minh ca tren khong qua nho trung hop.
    @Test
    void guillemetsInOutput_withoutMatchingAngleBracketsInQuery_invalid() {
        String q = "Lập trình viên Java ở Hà Nội";
        JobSearchParsePayload payload = new JobSearchParsePayload(
                List.of(new CodeCondition("HA_NOI", "‹b›Java ở Hà Nội")), List.of(), null, List.of(), List.of());
        assertThat(JobSearchParseValidator.isValid(payload, q)).isFalse();
    }

    @Test
    void unsanitize_allStringsAndNullsKept() {
        JobSearchParsePayload payload = new JobSearchParsePayload(
                Arrays.asList(new CodeCondition("‹A›", "‹x›"), null),
                List.of(new CodeCondition("I", "‹i›")),
                new SalaryCondition(1, 2, "‹s›"),
                List.of(new CodeCondition("REMOTE", null)),
                List.of("‹p›"));

        JobSearchParsePayload result = JobSearchParseValidator.unsanitize(payload);

        assertThat(result.locations()).containsExactly(new CodeCondition("<A>", "<x>"), null);
        assertThat(result.industries()).containsExactly(new CodeCondition("I", "<i>"));
        assertThat(result.salary()).isEqualTo(new SalaryCondition(1, 2, "<s>"));
        assertThat(result.workModes()).containsExactly(new CodeCondition("REMOTE", null));
        assertThat(result.personalInfoTexts()).containsExactly("<p>");
        assertThat(JobSearchParseValidator.unsanitize(null)).isNull();
    }

    // ---- ham TONG ----

    @Test
    void nullPayloadOrNullQuery_invalid_noException() {
        JobSearchParsePayload empty = new JobSearchParsePayload(List.of(), List.of(), null, List.of(), List.of());
        assertThatCode(() -> JobSearchParseValidator.isValid(null, Q)).doesNotThrowAnyException();
        assertThat(JobSearchParseValidator.isValid(null, Q)).isFalse();
        assertThatCode(() -> JobSearchParseValidator.isValid(empty, null)).doesNotThrowAnyException();
        assertThat(JobSearchParseValidator.isValid(empty, null)).isFalse();
    }
}
