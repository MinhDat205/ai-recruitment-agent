package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.catalog.CatalogEntry;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.CvBuilderEmptyException;
import com.recruitment.common.exception.InvalidCvBuilderFormException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

// FR-U12 T4 (R-F4 phan ky tu dieu khien/dinh dang, R-F5-R-F11, thu tu L5) - test don vi, khong Spring. Ca glyph va tu qua
// dai (R-F4 glyph, R-F12) o dot 3. Dau vao gia dinh DA chuan hoa (R-F3).
class CvBuilderFormValidatorTest {

    private static final CatalogRegistry REGISTRY = new CatalogRegistry(
            List.of(
                    new CatalogEntry("IT_SOFTWARE", "Công nghệ thông tin - Phần mềm", List.of()),
                    new CatalogEntry("OTHER", "Ngành khác", List.of())),
            List.of(new CatalogEntry("HA_NOI", "Hà Nội", List.of())));

    private final CvBuilderFormValidator validator = new CvBuilderFormValidator(REGISTRY);

    // ---- du lieu ----

    private static CvBuilderForm.Contact contact(String fullName) {
        return new CvBuilderForm.Contact(fullName, null, null, null, null);
    }

    private static CvBuilderForm.Experience exp(String company, String start, String end, Boolean current, String desc) {
        return new CvBuilderForm.Experience(company, null, start, end, current, desc);
    }

    private static CvBuilderForm.Education edu(String school, String start, String end, Double gpa) {
        return new CvBuilderForm.Education(school, null, null, start, end, gpa);
    }

    private static CvBuilderForm.Project project(String name, List<String> technologies) {
        return new CvBuilderForm.Project(name, null, technologies);
    }

    private static CvBuilderForm form(
            CvBuilderForm.Contact contact,
            String industryCode,
            List<CvBuilderForm.Education> education,
            List<CvBuilderForm.Experience> experience,
            List<String> skills,
            List<CvBuilderForm.Certification> certifications,
            List<CvBuilderForm.Project> projects) {
        return new CvBuilderForm(contact, null, null, industryCode, education, experience, skills, certifications, projects);
    }

    // Ho ten + 1 ky nang: form toi thieu hop le.
    private static CvBuilderForm minimal(String fullName) {
        return form(contact(fullName), null, null, null, List.of("Java"), null, null);
    }

    private static CvBuilderForm withEmail(String email) {
        return form(new CvBuilderForm.Contact("A", email, null, null, null), null, null, null, List.of("Java"), null, null);
    }

    private static CvBuilderForm withExperience(CvBuilderForm.Experience... items) {
        return form(contact("A"), null, null, Arrays.asList(items), null, null, null);
    }

    private static CvBuilderForm withEducation(CvBuilderForm.Education... items) {
        return form(contact("A"), null, Arrays.asList(items), null, null, null, null);
    }

    private static CvBuilderForm withSkills(List<String> skills) {
        return form(contact("A"), null, null, null, skills, null, null);
    }

    private static CvBuilderForm withProjects(CvBuilderForm.Project... items) {
        return form(contact("A"), null, null, null, null, null, Arrays.asList(items));
    }

    private static <T> List<T> times(int n, T item) {
        return new ArrayList<>(Collections.nCopies(n, item));
    }

    private void valid(CvBuilderForm form) {
        assertThatCode(() -> validator.validate(CvTemplate.CLASSIC, form, null)).doesNotThrowAnyException();
    }

    private void invalid(CvBuilderForm form, String message) {
        invalid(form, null, message);
    }

    private void invalid(CvBuilderForm form, String versionLabel, String message) {
        assertThatThrownBy(() -> validator.validate(CvTemplate.CLASSIC, form, versionLabel))
                .isInstanceOf(InvalidCvBuilderFormException.class)
                .hasMessage(message);
    }

    // ---- template, form null, R-F10 ----

    @Test
    void templateNull_firstError_evenWhenFormNull() {
        assertThatThrownBy(() -> validator.validate(null, null, null))
                .isInstanceOf(InvalidCvBuilderFormException.class)
                .hasMessage("Vui lòng chọn mẫu trình bày.");
    }

    @Test
    void formNull_cvBuilderEmpty() {
        assertThatThrownBy(() -> validator.validate(CvTemplate.MODERN, null, null))
                .isInstanceOf(CvBuilderEmptyException.class)
                .hasMessage("CV cần có họ và tên và ít nhất một mục ở học vấn, kinh nghiệm, kỹ năng, chứng chỉ hoặc dự án.");
    }

    @Test
    void onlyFullName_cvBuilderEmpty() {
        assertThatThrownBy(() -> validator.validate(CvTemplate.CLASSIC, form(contact("A"), null, null, null, null, null, null), null))
                .isInstanceOf(CvBuilderEmptyException.class);
    }

    @Test
    void emptyForm_fullNameRequiredBeforeEmpty() {
        invalid(form(null, null, null, null, null, null, null), "Họ và tên là bắt buộc.");
    }

    @Test
    void noFullNameButHasItems_fullNameRequired() {
        invalid(form(contact(null), null, null, null, List.of("Java"), null, null), "Họ và tên là bắt buộc.");
        invalid(form(null, null, null, List.of(exp("Cty", null, null, null, null)), null, null, null),
                "Họ và tên là bắt buộc.");
    }

    @Test
    void eachBlockAloneIsEnough() {
        valid(form(contact("A"), null, List.of(edu("ĐH", null, null, null)), null, null, null, null));
        valid(form(contact("A"), null, null, List.of(exp("Cty", null, null, null, null)), null, null, null));
        valid(form(contact("A"), null, null, null, List.of("Java"), null, null));
        valid(form(contact("A"), null, null, null, null, List.of(new CvBuilderForm.Certification("IELTS", null, null)), null));
        valid(form(contact("A"), null, null, null, null, null, List.of(project("P", null))));
    }

    @Test
    void fullValidForm_passes() {
        CvBuilderForm full = new CvBuilderForm(
                new CvBuilderForm.Contact("Nguyễn Quốc Huy", "a@b.vn", "0901 234 567", "Quận 1", "linkedin.com/in/x"),
                "Chuyên viên",
                "Hà Nội",
                "IT_SOFTWARE",
                List.of(new CvBuilderForm.Education("ĐH", "Cử nhân", "QTKD", "09/2015", "06/2019", 3.2)),
                List.of(new CvBuilderForm.Experience("Cty A", "CSKH", "07/2019", null, true, "Dòng 1\nDòng 2")),
                List.of("Giao tiếp", "Excel"),
                List.of(new CvBuilderForm.Certification("IELTS", "IDP", "2020")),
                List.of(new CvBuilderForm.Project("Bot", "Mô tả\n- ý 1", List.of("Java", "C#"))));

        assertThatCode(() -> validator.validate(CvTemplate.MODERN, full, "Bản 1")).doesNotThrowAnyException();
    }

    // ---- bien do dai / so muc ----

    @Test
    void fullName_boundary_1_100_101() {
        valid(minimal("a"));
        valid(minimal("a".repeat(100)));
        invalid(minimal("a".repeat(101)), "Họ và tên tối đa 100 ký tự.");
    }

    @Test
    void description_boundary_2000_2001() {
        valid(withExperience(exp("Cty", null, null, null, "d".repeat(2000))));
        invalid(withExperience(exp("Cty", null, null, null, "d".repeat(2001))), "Kinh nghiệm 1: Mô tả tối đa 2000 ký tự.");
        valid(withProjects(new CvBuilderForm.Project("P", "d".repeat(2000), null)));
        invalid(withProjects(new CvBuilderForm.Project("P", "d".repeat(2001), null)), "Dự án 1: Mô tả tối đa 2000 ký tự.");
    }

    @Test
    void education_boundary_10_11_items() {
        valid(form(contact("A"), null, times(10, edu("ĐH", null, null, null)), null, null, null, null));
        invalid(form(contact("A"), null, times(11, edu("ĐH", null, null, null)), null, null, null, null),
                "Học vấn tối đa 10 mục.");
    }

    @Test
    void experience_boundary_15_16_items() {
        valid(form(contact("A"), null, null, times(15, exp("Cty", null, null, null, null)), null, null, null));
        invalid(form(contact("A"), null, null, times(16, exp("Cty", null, null, null, null)), null, null, null),
                "Kinh nghiệm tối đa 15 mục.");
    }

    @Test
    void certifications_boundary_20_21_items() {
        CvBuilderForm.Certification c = new CvBuilderForm.Certification("IELTS", null, null);
        valid(form(contact("A"), null, null, null, null, times(20, c), null));
        invalid(form(contact("A"), null, null, null, null, times(21, c), null), "Chứng chỉ tối đa 20 mục.");
    }

    @Test
    void projects_boundary_15_16_items() {
        valid(form(contact("A"), null, null, null, null, null, times(15, project("P", null))));
        invalid(form(contact("A"), null, null, null, null, null, times(16, project("P", null))), "Dự án tối đa 15 mục.");
    }

    @Test
    void skills_boundary_50_51_items_and_50_51_chars() {
        valid(withSkills(times(50, "Java")));
        invalid(withSkills(times(51, "Java")), "Kỹ năng tối đa 50 mục.");
        valid(withSkills(List.of("s".repeat(50))));
        invalid(withSkills(List.of("Java", "s".repeat(51))), "Kỹ năng 2 tối đa 50 ký tự.");
    }

    @Test
    void technologies_boundary_20_21_items_and_50_51_chars() {
        valid(withProjects(project("P", times(20, "Java"))));
        invalid(withProjects(project("P", times(21, "Java"))), "Dự án 1: Công nghệ tối đa 20 mục.");
        valid(withProjects(project("P", List.of("t".repeat(50)))));
        invalid(withProjects(project("P", List.of("Java")), project("Q", List.of("Go", "t".repeat(51)))),
                "Dự án 2: Công nghệ 2 tối đa 50 ký tự.");
    }

    @Test
    void versionLabel_boundary_100_101() {
        assertThatCode(() -> validator.validate(CvTemplate.CLASSIC, minimal("A"), "v".repeat(100)))
                .doesNotThrowAnyException();
        invalid(minimal("A"), "v".repeat(101), "Tên phiên bản tối đa 100 ký tự.");
    }

    @Test
    void contactFieldLimits() {
        invalid(form(new CvBuilderForm.Contact("A", null, "1".repeat(31), null, null), null, null, null, List.of("J"), null, null),
                "Điện thoại tối đa 30 ký tự.");
        invalid(form(new CvBuilderForm.Contact("A", null, null, "x".repeat(201), null), null, null, null, List.of("J"), null, null),
                "Địa chỉ tối đa 200 ký tự.");
        invalid(form(new CvBuilderForm.Contact("A", null, null, null, "x".repeat(201)), null, null, null, List.of("J"), null, null),
                "LinkedIn tối đa 200 ký tự.");
        invalid(withEmail("a@" + "b".repeat(253)), "Email tối đa 254 ký tự.");
        valid(withEmail("a@" + "b".repeat(252)));
    }

    @Test
    void otherFieldLimits() {
        invalid(new CvBuilderForm(contact("A"), "t".repeat(151), null, null, null, null, List.of("J"), null, null),
                "Chức danh hiện tại tối đa 150 ký tự.");
        invalid(new CvBuilderForm(contact("A"), null, "l".repeat(101), null, null, null, List.of("J"), null, null),
                "Khu vực tối đa 100 ký tự.");
        invalid(withEducation(new CvBuilderForm.Education("s".repeat(201), null, null, null, null, null)),
                "Học vấn 1: Trường tối đa 200 ký tự.");
        invalid(withEducation(new CvBuilderForm.Education("ĐH", "d".repeat(101), null, null, null, null)),
                "Học vấn 1: Bằng cấp tối đa 100 ký tự.");
        invalid(withEducation(new CvBuilderForm.Education("ĐH", null, "m".repeat(151), null, null, null)),
                "Học vấn 1: Chuyên ngành tối đa 150 ký tự.");
        invalid(withExperience(new CvBuilderForm.Experience("c".repeat(201), null, null, null, null, null)),
                "Kinh nghiệm 1: Tên công ty tối đa 200 ký tự.");
        invalid(withExperience(new CvBuilderForm.Experience("Cty", "t".repeat(151), null, null, null, null)),
                "Kinh nghiệm 1: Chức danh tối đa 150 ký tự.");
        invalid(form(contact("A"), null, null, null, null,
                        List.of(new CvBuilderForm.Certification("n".repeat(201), null, null)), null),
                "Chứng chỉ 1: Tên chứng chỉ tối đa 200 ký tự.");
        invalid(form(contact("A"), null, null, null, null,
                        List.of(new CvBuilderForm.Certification("IELTS", "i".repeat(201), null)), null),
                "Chứng chỉ 1: Đơn vị cấp tối đa 200 ký tự.");
        invalid(withProjects(project("n".repeat(201), null)), "Dự án 1: Tên dự án tối đa 200 ký tự.");
    }

    // ---- bat buoc trong muc, phan tu null ----

    @Test
    void requiredItemFields() {
        invalid(withExperience(exp("Cty", null, null, null, null), exp(null, null, null, null, null)),
                "Kinh nghiệm 2: Tên công ty là bắt buộc.");
        invalid(withEducation(edu(null, null, null, null)), "Học vấn 1: Trường là bắt buộc.");
        invalid(form(contact("A"), null, null, null, null, List.of(new CvBuilderForm.Certification(null, "IDP", null)), null),
                "Chứng chỉ 1: Tên chứng chỉ là bắt buộc.");
        invalid(withProjects(project(null, null)), "Dự án 1: Tên dự án là bắt buộc.");
    }

    @Test
    void nullElements_rejected() {
        invalid(form(contact("A"), null, Arrays.asList((CvBuilderForm.Education) null), null, null, null, null),
                "Học vấn 1 là bắt buộc.");
        invalid(withSkills(Arrays.asList("Java", null)), "Kỹ năng 2 là bắt buộc.");
        invalid(withProjects(project("P", Arrays.asList((String) null))), "Dự án 1: Công nghệ 1 là bắt buộc.");
        invalid(withExperience(exp("Cty", null, null, null, null), null), "Kinh nghiệm 2 là bắt buộc.");
    }

    // L5: trong mot khoi, phan tu null duoc bao TRUOC loi cua muc dung truoc no.
    @Test
    void nullElementReportedBeforeEarlierItemError() {
        invalid(withExperience(exp(null, null, null, null, null), null), "Kinh nghiệm 2 là bắt buộc.");
    }

    // ---- moc thoi gian (R-F6, R-F7, R-F8) ----

    @ParameterizedTest
    @ValueSource(strings = {"03/2019", "2019", "1950", "2100", "01/1950", "12/2100"})
    void date_validFormats(String date) {
        valid(withExperience(exp("Cty", date, null, null, null)));
        valid(withEducation(edu("ĐH", null, date, null)));
        valid(form(contact("A"), null, null, null, null, List.of(new CvBuilderForm.Certification("C", null, date)), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3/2019", "13/2019", "00/2019", "1949", "2101", "03-2019", "2019-03", "12/1949",
            "01/2101", "Hiện tại", "١٩٩٠", "03/19"})
    void date_invalidFormats(String date) {
        invalid(withExperience(exp("Cty", date, null, null, null)),
                "Kinh nghiệm 1: Thời gian bắt đầu phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.");
        invalid(withEducation(edu("ĐH", null, date, null)),
                "Học vấn 1: Thời gian kết thúc phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.");
        invalid(form(contact("A"), null, null, null, null, List.of(new CvBuilderForm.Certification("C", null, date)), null),
                "Chứng chỉ 1: Thời gian cấp phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.");
    }

    @Test
    void dateOrder() {
        invalid(withExperience(exp("Cty", "06/2020", "05/2020", null, null)),
                "Kinh nghiệm 1: thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.");
        invalid(withExperience(exp("Cty", "2021", "05/2020", null, null)),
                "Kinh nghiệm 1: thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.");
        invalid(withEducation(edu("ĐH", "2020", "2019", null)),
                "Học vấn 1: thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.");
        valid(withExperience(exp("Cty", "05/2020", "05/2020", null, null)));
        valid(withExperience(exp("Cty", "2019", "05/2019", null, null)));
        valid(withExperience(exp("Cty", "05/2019", "2019", null, null)));
        valid(withExperience(exp("Cty", "12/2019", "01/2020", null, null)));
        valid(withEducation(edu("ĐH", "2019", "2019", null)));
    }

    @Test
    void currentWithEndDate() {
        invalid(withExperience(exp("Cty", "03/2019", "05/2020", true, null)),
                "Kinh nghiệm 1: đang làm thì không nhập thời gian kết thúc.");
        valid(withExperience(exp("Cty", "03/2019", null, true, null)));
        valid(withExperience(exp("Cty", "03/2019", "05/2020", false, null)));
        valid(withExperience(exp("Cty", "03/2019", "05/2020", null, null)));
    }

    // ---- GPA, email, nganh ----

    @Test
    void gpa_boundaries() {
        String gpaError = "Học vấn 1: GPA phải từ 0 đến 10, tối đa 2 chữ số thập phân.";
        invalid(withEducation(edu("ĐH", null, null, -0.01)), gpaError);
        valid(withEducation(edu("ĐH", null, null, 0.0)));
        valid(withEducation(edu("ĐH", null, null, 3.55)));
        invalid(withEducation(edu("ĐH", null, null, 3.555)), gpaError);
        valid(withEducation(edu("ĐH", null, null, 10.0)));
        invalid(withEducation(edu("ĐH", null, null, 10.01)), gpaError);
        invalid(withEducation(edu("ĐH", null, null, Double.NaN)), gpaError);
        invalid(withEducation(edu("ĐH", null, null, Double.POSITIVE_INFINITY)), gpaError);
    }

    // L19 - cau GPA mang tien to muc hoc van bi sai.
    @Test
    void gpa_errorInSecondEducation_prefixedWithItemNumber() {
        assertThatThrownBy(() -> validator.validate(
                        CvTemplate.CLASSIC,
                        withEducation(edu("ĐH A", null, null, 3.2), edu("ĐH B", null, null, 10.5)),
                        null))
                .isInstanceOf(InvalidCvBuilderFormException.class)
                .hasMessage("Học vấn 2: GPA phải từ 0 đến 10, tối đa 2 chữ số thập phân.")
                .message()
                .startsWith("Học vấn 2: ");
    }

    @Test
    void email_format() {
        valid(withEmail("a@b"));
        invalid(withEmail("ab"), "Email không đúng định dạng.");
        invalid(withEmail("a@b@c"), "Email không đúng định dạng.");
        invalid(withEmail("a b@c"), "Email không đúng định dạng.");
    }

    @Test
    void industryCode_catalog() {
        valid(form(contact("A"), "IT_SOFTWARE", null, null, List.of("J"), null, null));
        invalid(form(contact("A"), "OTHER", null, null, List.of("J"), null, null), "Ngành nghề không có trong danh mục.");
        invalid(form(contact("A"), "MA_LA", null, null, List.of("J"), null, null), "Ngành nghề không có trong danh mục.");
        invalid(form(contact("A"), "it_software", null, null, List.of("J"), null, null), "Ngành nghề không có trong danh mục.");
    }

    // ---- R-F4: ky tu dieu khien / dinh dang ----

    @Test
    void controlAndFormatCharacters_rejected() {
        invalid(minimal("A\u0007B"), "Họ và tên có ký tự không hỗ trợ trong CV.");
        invalid(withSkills(List.of("Ja​va")), "Kỹ năng 1 có ký tự không hỗ trợ trong CV.");
        invalid(withSkills(List.of("Java", "﻿Go")), "Kỹ năng 2 có ký tự không hỗ trợ trong CV.");
        invalid(withSkills(List.of("a‍b")), "Kỹ năng 1 có ký tự không hỗ trợ trong CV.");
        invalid(withExperience(exp("Cty", null, null, null, "Mô tả\u0000")), "Kinh nghiệm 1: Mô tả có ký tự không hỗ trợ trong CV.");
        invalid(withProjects(project("P", List.of("Ja\u0007va"))), "Dự án 1: Công nghệ 1 có ký tự không hỗ trợ trong CV.");
        invalid(minimal("A"), "Bản​ 1", "Tên phiên bản có ký tự không hỗ trợ trong CV.");
    }

    @Test
    void newline_allowedOnlyInDescription() {
        valid(withExperience(exp("Cty", null, null, null, "Dòng 1\n\nDòng 3")));
        valid(withProjects(new CvBuilderForm.Project("P", "a\nb", null)));
        invalid(withExperience(exp("Cty\nA", null, null, null, null)),
                "Kinh nghiệm 1: Tên công ty có ký tự không hỗ trợ trong CV.");
    }

    // ---- thu tu loi (L5) ----

    @Test
    void twoErrors_onlyFirstByFieldOrder() {
        // fullName (truoc) va email (sau) cung sai.
        invalid(form(new CvBuilderForm.Contact("a".repeat(101), "ab", null, null, null), null, null, null, List.of("J"), null, null),
                "Họ và tên tối đa 100 ký tự.");
        // hoc van (truoc) va kinh nghiem (sau) cung sai.
        invalid(form(contact("A"), null, List.of(edu("ĐH", "13/2019", null, null)),
                        List.of(exp("Cty", null, null, null, "d".repeat(2001))), null, null, null),
                "Học vấn 1: Thời gian bắt đầu phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.");
        // trong mot o: do dai truoc ky tu dieu khien.
        invalid(minimal("\u0007".repeat(101)), "Họ và tên tối đa 100 ký tự.");
        // trong mot muc: moc sai dang truoc thu tu moc / dang lam.
        invalid(withExperience(exp("Cty", "2021", "13/2020", true, null)),
                "Kinh nghiệm 1: Thời gian kết thúc phải có dạng MM/YYYY hoặc YYYY, năm từ 1950 đến 2100.");
        // R-F7 truoc R-F8.
        invalid(withExperience(exp("Cty", "2021", "2020", true, null)),
                "Kinh nghiệm 1: thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.");
        // versionLabel sau moi o cua form.
        invalid(withSkills(List.of("s".repeat(51))), "v".repeat(101), "Kỹ năng 1 tối đa 50 ký tự.");
        // loi cua o truoc R-F10 (CV_BUILDER_EMPTY).
        invalid(form(contact("A"), "MA_LA", null, null, null, null, null), "Ngành nghề không có trong danh mục.");
    }

    // ---- cau loi khong chua gia tri da go ----

    @Test
    void messages_neverContainTypedValue() {
        String secret = "ZQXbiMat";
        List<Runnable> cases = List.of(
                () -> validator.validate(CvTemplate.CLASSIC, minimal(secret + "a".repeat(100)), null),
                () -> validator.validate(CvTemplate.CLASSIC, withEmail(secret), null),
                () -> validator.validate(CvTemplate.CLASSIC, withSkills(List.of(secret + "​")), null),
                () -> validator.validate(CvTemplate.CLASSIC, withExperience(exp("Cty", secret, null, null, null)), null),
                () -> validator.validate(CvTemplate.CLASSIC, form(contact("A"), secret, null, null, List.of("J"), null, null), null),
                () -> validator.validate(CvTemplate.CLASSIC, minimal("A"), secret + "v".repeat(100)));
        for (Runnable c : cases) {
            assertThatThrownBy(c::run)
                    .isInstanceOf(InvalidCvBuilderFormException.class)
                    .message()
                    .doesNotContain(secret);
        }
    }
}
