package com.recruitment.cvbuilder;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.CvBuilderEmptyException;
import com.recruitment.common.exception.CvBuilderField;
import com.recruitment.common.exception.InvalidCvBuilderFormException;
import com.recruitment.resume.ResumeParsingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// FR-U12 R-F4 (ky tu dieu khien/dinh dang), R-F5-R-F10, cau loi R-F11. Dau vao DA qua CvBuilderFormNormalizer (R-F3).
// Chi bao LOI DAU TIEN theo thu tu L5:
//   template null -> form null (CV_BUILDER_EMPTY) -> cac o theo thu tu R-F1; khoi danh sach: so muc -> phan tu null ->
//   tung muc theo chi so; moi o: bat buoc -> do dai -> ky tu dieu khien/dinh dang -> [glyph, dot 3] -> dinh dang (moc,
//   email, GPA, nganh) -> [tu qua dai R-F12, dot 3]; R-F7/R-F8 sau cac o cua muc; versionLabel sau cung; roi R-F10.
// Cau loi chi ghep tu nhan o (CvBuilderField) va so thu tu - khong chua gia tri da go.
@Component
public class CvBuilderFormValidator {

    static final int FULL_NAME_MAX = 100;
    static final int EMAIL_MAX = 254;
    static final int PHONE_MAX = 30;
    static final int ADDRESS_MAX = 200;
    static final int LINKEDIN_MAX = 200;
    static final int CURRENT_TITLE_MAX = 150;
    static final int LOCATION_TEXT_MAX = 100;
    static final int EDUCATION_MAX_ITEMS = 10;
    static final int SCHOOL_MAX = 200;
    static final int DEGREE_MAX = 100;
    static final int MAJOR_MAX = 150;
    static final int EXPERIENCE_MAX_ITEMS = 15;
    static final int COMPANY_MAX = 200;
    static final int EXPERIENCE_TITLE_MAX = 150;
    static final int DESCRIPTION_MAX = 2000;
    static final int SKILLS_MAX_ITEMS = 50;
    static final int TAG_MAX = 50;
    static final int CERTIFICATIONS_MAX_ITEMS = 20;
    static final int CERTIFICATION_NAME_MAX = 200;
    static final int ISSUER_MAX = 200;
    static final int PROJECTS_MAX_ITEMS = 15;
    static final int PROJECT_NAME_MAX = 200;
    static final int TECHNOLOGIES_MAX_ITEMS = 20;
    static final int VERSION_MAX = 100;

    static final int MIN_YEAR = 1950;
    static final int MAX_YEAR = 2100;
    static final double GPA_MAX = 10;
    static final int GPA_MAX_SCALE = 2;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
    // R-F6: MM/YYYY (thang 01-12, du 2 chu so) hoac YYYY. Chi chu so ASCII.
    private static final Pattern MONTH_YEAR = Pattern.compile("^(0[1-9]|1[0-2])/([0-9]{4})$");
    private static final Pattern YEAR_ONLY = Pattern.compile("^([0-9]{4})$");

    private final CatalogRegistry catalogRegistry;

    public CvBuilderFormValidator(CatalogRegistry catalogRegistry) {
        this.catalogRegistry = catalogRegistry;
    }

    // Nem InvalidCvBuilderFormException / CvBuilderEmptyException o loi dau tien; hop le -> tra ve binh thuong.
    public void validate(CvTemplate template, CvBuilderForm form, String versionLabel) {
        if (template == null) {
            throw InvalidCvBuilderFormException.templateRequired();
        }
        if (form == null) {
            throw new CvBuilderEmptyException();
        }

        CvBuilderForm.Contact contact = form.contact();
        String fullName = contact == null ? null : contact.fullName();
        checkText(CvBuilderField.FULL_NAME.at(), fullName, true, FULL_NAME_MAX, false);
        if (contact != null) {
            checkText(CvBuilderField.EMAIL.at(), contact.email(), false, EMAIL_MAX, false);
            if (contact.email() != null && !EMAIL.matcher(contact.email()).matches()) {
                throw InvalidCvBuilderFormException.invalidEmail();
            }
            checkText(CvBuilderField.PHONE.at(), contact.phone(), false, PHONE_MAX, false);
            checkText(CvBuilderField.ADDRESS.at(), contact.address(), false, ADDRESS_MAX, false);
            checkText(CvBuilderField.LINKEDIN.at(), contact.linkedin(), false, LINKEDIN_MAX, false);
        }
        checkText(CvBuilderField.CURRENT_TITLE.at(), form.currentTitle(), false, CURRENT_TITLE_MAX, false);
        checkText(CvBuilderField.LOCATION_TEXT.at(), form.locationText(), false, LOCATION_TEXT_MAX, false);
        checkIndustry(form.industryCode());

        checkList(CvBuilderField.EDUCATION, form.education(), EDUCATION_MAX_ITEMS);
        for (int i = 0; i < form.education().size(); i++) {
            checkEducation(i + 1, form.education().get(i));
        }

        checkList(CvBuilderField.EXPERIENCE, form.experience(), EXPERIENCE_MAX_ITEMS);
        for (int i = 0; i < form.experience().size(); i++) {
            checkExperience(i + 1, form.experience().get(i));
        }

        checkList(CvBuilderField.SKILLS, form.skills(), SKILLS_MAX_ITEMS);
        for (int i = 0; i < form.skills().size(); i++) {
            checkText(CvBuilderField.SKILLS.at(i + 1), form.skills().get(i), true, TAG_MAX, false);
        }

        checkList(CvBuilderField.CERTIFICATIONS, form.certifications(), CERTIFICATIONS_MAX_ITEMS);
        for (int i = 0; i < form.certifications().size(); i++) {
            checkCertification(i + 1, form.certifications().get(i));
        }

        checkList(CvBuilderField.PROJECTS, form.projects(), PROJECTS_MAX_ITEMS);
        for (int i = 0; i < form.projects().size(); i++) {
            checkProject(i + 1, form.projects().get(i));
        }

        checkText(CvBuilderField.VERSION_LABEL.at(), versionLabel, false, VERSION_MAX, false);

        // R-F10 - fullName da bat buoc o tren; con lai: it nhat mot muc o mot trong nam khoi.
        if (form.education().isEmpty()
                && form.experience().isEmpty()
                && form.skills().isEmpty()
                && form.certifications().isEmpty()
                && form.projects().isEmpty()) {
            throw new CvBuilderEmptyException();
        }
    }

    private void checkIndustry(String code) {
        checkText(CvBuilderField.INDUSTRY_CODE.at(), code, false, null, false);
        if (code != null
                && (!catalogRegistry.isIndustry(code) || ResumeParsingService.EXCLUDED_INDUSTRY_CODE.equals(code))) {
            throw InvalidCvBuilderFormException.unknownIndustry();
        }
    }

    private static void checkEducation(int n, CvBuilderForm.Education e) {
        checkText(CvBuilderField.EDUCATION_SCHOOL.at(n), e.school(), true, SCHOOL_MAX, false);
        checkText(CvBuilderField.EDUCATION_DEGREE.at(n), e.degree(), false, DEGREE_MAX, false);
        checkText(CvBuilderField.EDUCATION_MAJOR.at(n), e.major(), false, MAJOR_MAX, false);
        MonthYear start = checkDate(CvBuilderField.EDUCATION_START_DATE.at(n), e.startDate());
        MonthYear end = checkDate(CvBuilderField.EDUCATION_END_DATE.at(n), e.endDate());
        checkGpa(CvBuilderField.EDUCATION.at(n), e.gpa());
        checkOrder(CvBuilderField.EDUCATION.at(n), start, end);
    }

    private static void checkExperience(int n, CvBuilderForm.Experience e) {
        checkText(CvBuilderField.EXPERIENCE_COMPANY.at(n), e.company(), true, COMPANY_MAX, false);
        checkText(CvBuilderField.EXPERIENCE_TITLE.at(n), e.title(), false, EXPERIENCE_TITLE_MAX, false);
        MonthYear start = checkDate(CvBuilderField.EXPERIENCE_START_DATE.at(n), e.startDate());
        MonthYear end = checkDate(CvBuilderField.EXPERIENCE_END_DATE.at(n), e.endDate());
        checkText(CvBuilderField.EXPERIENCE_DESCRIPTION.at(n), e.description(), false, DESCRIPTION_MAX, true);
        checkOrder(CvBuilderField.EXPERIENCE.at(n), start, end);
        // R-F8 - current null coi nhu false.
        if (Boolean.TRUE.equals(e.current()) && e.endDate() != null) {
            throw InvalidCvBuilderFormException.currentWithEndDate(CvBuilderField.EXPERIENCE.at(n));
        }
    }

    private static void checkCertification(int n, CvBuilderForm.Certification c) {
        checkText(CvBuilderField.CERTIFICATION_NAME.at(n), c.name(), true, CERTIFICATION_NAME_MAX, false);
        checkText(CvBuilderField.CERTIFICATION_ISSUER.at(n), c.issuer(), false, ISSUER_MAX, false);
        checkDate(CvBuilderField.CERTIFICATION_ISSUE_DATE.at(n), c.issueDate());
    }

    private static void checkProject(int n, CvBuilderForm.Project p) {
        checkText(CvBuilderField.PROJECT_NAME.at(n), p.name(), true, PROJECT_NAME_MAX, false);
        checkText(CvBuilderField.PROJECT_DESCRIPTION.at(n), p.description(), false, DESCRIPTION_MAX, true);
        List<String> technologies = p.technologies();
        if (technologies.size() > TECHNOLOGIES_MAX_ITEMS) {
            throw InvalidCvBuilderFormException.tooManyItems(
                    CvBuilderField.PROJECT_TECHNOLOGIES.at(n), TECHNOLOGIES_MAX_ITEMS);
        }
        int firstNull = firstNullIndex(technologies);
        if (firstNull >= 0) {
            throw InvalidCvBuilderFormException.required(CvBuilderField.PROJECT_TECHNOLOGIES.at(n, firstNull + 1));
        }
        for (int j = 0; j < technologies.size(); j++) {
            checkText(CvBuilderField.PROJECT_TECHNOLOGIES.at(n, j + 1), technologies.get(j), true, TAG_MAX, false);
        }
    }

    // Khoi danh sach: so muc -> phan tu null (truoc khi kiem bat ky muc nao - L5).
    private static void checkList(CvBuilderField block, List<?> items, int maxItems) {
        if (items.size() > maxItems) {
            throw InvalidCvBuilderFormException.tooManyItems(block.at(), maxItems);
        }
        int firstNull = firstNullIndex(items);
        if (firstNull >= 0) {
            throw InvalidCvBuilderFormException.required(block.at(firstNull + 1));
        }
    }

    // Khong dung List.indexOf(null): List.of()/List.copyOf() nem NullPointerException khi tim null.
    private static int firstNullIndex(List<?> items) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    // bat buoc -> do dai -> ky tu dieu khien/dinh dang. maxLength null = khong gioi han o R-F5.
    private static void checkText(
            CvBuilderField.Path path, String value, boolean required, Integer maxLength, boolean multiLine) {
        if (value == null) {
            if (required) {
                throw InvalidCvBuilderFormException.required(path);
            }
            return;
        }
        if (maxLength != null && value.length() > maxLength) {
            throw InvalidCvBuilderFormException.tooLong(path, maxLength);
        }
        if (hasDisallowedCharacter(value, multiLine)) {
            throw InvalidCvBuilderFormException.unsupportedCharacter(path);
        }
    }

    // R-F4 phan ky tu: CONTROL (tru \n o o nhieu dong) va FORMAT (gom U+200B, U+200D, U+FEFF). Duyet theo code point.
    static boolean hasDisallowedCharacter(String value, boolean allowNewline) {
        return value.codePoints().anyMatch(cp -> {
            int type = Character.getType(cp);
            if (type == Character.CONTROL) {
                return !(allowNewline && cp == '\n');
            }
            return type == Character.FORMAT;
        });
    }

    // Moc thoi gian (R-F6). null -> null; sai dang -> loi.
    private static MonthYear checkDate(CvBuilderField.Path path, String value) {
        checkText(path, value, false, null, false);
        if (value == null) {
            return null;
        }
        MonthYear parsed = parseDate(value);
        if (parsed == null) {
            throw InvalidCvBuilderFormException.invalidDate(path);
        }
        return parsed;
    }

    static MonthYear parseDate(String value) {
        Integer month = null;
        int year;
        Matcher monthYear = MONTH_YEAR.matcher(value);
        Matcher yearOnly = YEAR_ONLY.matcher(value);
        if (monthYear.matches()) {
            month = Integer.valueOf(monthYear.group(1));
            year = Integer.parseInt(monthYear.group(2));
        } else if (yearOnly.matches()) {
            year = Integer.parseInt(yearOnly.group(1));
        } else {
            return null;
        }
        if (year < MIN_YEAR || year > MAX_YEAR) {
            return null;
        }
        return new MonthYear(year, month);
    }

    // R-F7 - so (nam, thang); mot ben chi co nam -> so theo nam. Bang nhau hop le.
    private static void checkOrder(CvBuilderField.Path item, MonthYear start, MonthYear end) {
        if (start == null || end == null) {
            return;
        }
        boolean startAfterEnd = start.year() != end.year()
                ? start.year() > end.year()
                : start.month() != null && end.month() != null && start.month() > end.month();
        if (startAfterEnd) {
            throw InvalidCvBuilderFormException.startAfterEnd(item);
        }
    }

    // R-F9 - [0, 10], toi da 2 chu so thap phan. NaN/vo cuc -> loi (BigDecimal.valueOf nem voi chung).
    private static void checkGpa(CvBuilderField.Path item, Double gpa) {
        if (gpa == null) {
            return;
        }
        if (!Double.isFinite(gpa)
                || gpa < 0
                || gpa > GPA_MAX
                || BigDecimal.valueOf(gpa).stripTrailingZeros().scale() > GPA_MAX_SCALE) {
            throw InvalidCvBuilderFormException.invalidGpa(item);
        }
    }

    record MonthYear(int year, Integer month) {
    }
}
