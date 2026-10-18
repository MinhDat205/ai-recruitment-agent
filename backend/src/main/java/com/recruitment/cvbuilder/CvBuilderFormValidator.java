package com.recruitment.cvbuilder;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.common.exception.CvBuilderEmptyException;
import com.recruitment.common.exception.CvBuilderField;
import com.recruitment.common.exception.InvalidCvBuilderFormException;
import com.recruitment.resume.ResumeParsingService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Component;

// FR-U12 R-F4 (ky tu dieu khien/dinh dang + glyph), R-F5-R-F10, R-F12, cau loi R-F11. Dau vao DA qua
// CvBuilderFormNormalizer (R-F3). Chi bao LOI DAU TIEN theo thu tu L5:
//   template null -> form null (CV_BUILDER_EMPTY) -> cac o theo thu tu R-F1; khoi danh sach: so muc -> phan tu null ->
//   tung muc theo chi so; moi o: bat buoc -> do dai -> ky tu dieu khien/dinh dang -> glyph -> dinh dang (moc, email,
//   GPA, nganh) -> tu qua dai (R-F12); R-F7/R-F8 sau cac o cua muc; versionLabel sau cung; roi R-F10.
// Glyph va R-F12 do bang font that, qua CvPdfRenderer.styleOf/wordsFit - cung ham renderer dung (L3); o khong in
// (industryCode, versionLabel) khong kiem glyph/R-F12.
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
    private final CvPdfFonts fonts;

    public CvBuilderFormValidator(CatalogRegistry catalogRegistry, CvPdfFonts fonts) {
        this.catalogRegistry = catalogRegistry;
        this.fonts = fonts;
    }

    // Nem InvalidCvBuilderFormException / CvBuilderEmptyException o loi dau tien; hop le -> tra ve binh thuong.
    public void validate(CvTemplate template, CvBuilderForm form, String versionLabel) {
        if (template == null) {
            throw InvalidCvBuilderFormException.templateRequired();
        }
        if (form == null) {
            throw new CvBuilderEmptyException();
        }
        // Font nap vao mot document nhap rieng cho lan kiem nay (PDType0Font khong an toan da luong) - khong luu gi.
        try (PDDocument scratch = new PDDocument()) {
            validateFields(new Layout(template, fonts.load(scratch)), form, versionLabel);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong nap duoc font de kiem form CV", e);
        }
    }

    private void validateFields(Layout layout, CvBuilderForm form, String versionLabel) {
        CvBuilderForm.Contact contact = form.contact();
        String fullName = contact == null ? null : contact.fullName();
        checkPrinted(layout, CvBuilderField.FULL_NAME.at(), fullName, true, FULL_NAME_MAX, false);
        if (contact != null) {
            checkText(layout, CvBuilderField.EMAIL.at(), contact.email(), false, EMAIL_MAX, false);
            if (contact.email() != null && !EMAIL.matcher(contact.email()).matches()) {
                throw InvalidCvBuilderFormException.invalidEmail();
            }
            checkWordsFit(layout, CvBuilderField.EMAIL.at(), contact.email(), false);
            checkPrinted(layout, CvBuilderField.PHONE.at(), contact.phone(), false, PHONE_MAX, false);
            checkPrinted(layout, CvBuilderField.ADDRESS.at(), contact.address(), false, ADDRESS_MAX, false);
            checkPrinted(layout, CvBuilderField.LINKEDIN.at(), contact.linkedin(), false, LINKEDIN_MAX, false);
        }
        checkPrinted(layout, CvBuilderField.CURRENT_TITLE.at(), form.currentTitle(), false, CURRENT_TITLE_MAX, false);
        checkPrinted(layout, CvBuilderField.LOCATION_TEXT.at(), form.locationText(), false, LOCATION_TEXT_MAX, false);
        checkIndustry(layout, form.industryCode());

        checkList(CvBuilderField.EDUCATION, form.education(), EDUCATION_MAX_ITEMS);
        for (int i = 0; i < form.education().size(); i++) {
            checkEducation(layout, i + 1, form.education().get(i));
        }

        checkList(CvBuilderField.EXPERIENCE, form.experience(), EXPERIENCE_MAX_ITEMS);
        for (int i = 0; i < form.experience().size(); i++) {
            checkExperience(layout, i + 1, form.experience().get(i));
        }

        checkList(CvBuilderField.SKILLS, form.skills(), SKILLS_MAX_ITEMS);
        for (int i = 0; i < form.skills().size(); i++) {
            checkPrinted(layout, CvBuilderField.SKILLS.at(i + 1), form.skills().get(i), true, TAG_MAX, false);
        }

        checkList(CvBuilderField.CERTIFICATIONS, form.certifications(), CERTIFICATIONS_MAX_ITEMS);
        for (int i = 0; i < form.certifications().size(); i++) {
            checkCertification(layout, i + 1, form.certifications().get(i));
        }

        checkList(CvBuilderField.PROJECTS, form.projects(), PROJECTS_MAX_ITEMS);
        for (int i = 0; i < form.projects().size(); i++) {
            checkProject(layout, i + 1, form.projects().get(i));
        }

        checkText(layout, CvBuilderField.VERSION_LABEL.at(), versionLabel, false, VERSION_MAX, false);

        // R-F10 - fullName da bat buoc o tren; con lai: it nhat mot muc o mot trong nam khoi.
        if (form.education().isEmpty()
                && form.experience().isEmpty()
                && form.skills().isEmpty()
                && form.certifications().isEmpty()
                && form.projects().isEmpty()) {
            throw new CvBuilderEmptyException();
        }
    }

    private void checkIndustry(Layout layout, String code) {
        checkText(layout, CvBuilderField.INDUSTRY_CODE.at(), code, false, null, false);
        if (code != null
                && (!catalogRegistry.isIndustry(code) || ResumeParsingService.EXCLUDED_INDUSTRY_CODE.equals(code))) {
            throw InvalidCvBuilderFormException.unknownIndustry();
        }
    }

    private static void checkEducation(Layout layout, int n, CvBuilderForm.Education e) {
        checkPrinted(layout, CvBuilderField.EDUCATION_SCHOOL.at(n), e.school(), true, SCHOOL_MAX, false);
        checkPrinted(layout, CvBuilderField.EDUCATION_DEGREE.at(n), e.degree(), false, DEGREE_MAX, false);
        checkPrinted(layout, CvBuilderField.EDUCATION_MAJOR.at(n), e.major(), false, MAJOR_MAX, false);
        MonthYear start = checkDate(layout, CvBuilderField.EDUCATION_START_DATE.at(n), e.startDate());
        MonthYear end = checkDate(layout, CvBuilderField.EDUCATION_END_DATE.at(n), e.endDate());
        checkGpa(CvBuilderField.EDUCATION.at(n), e.gpa());
        checkOrder(CvBuilderField.EDUCATION.at(n), start, end);
    }

    private static void checkExperience(Layout layout, int n, CvBuilderForm.Experience e) {
        // L3 - company in dam o dong 1 khi muc khong co title.
        CvBuilderField.Path company = CvBuilderField.EXPERIENCE_COMPANY.at(n);
        checkText(layout, company, e.company(), true, COMPANY_MAX, false);
        checkWordsFit(layout, company, e.company(), e.title() == null);
        checkPrinted(layout, CvBuilderField.EXPERIENCE_TITLE.at(n), e.title(), false, EXPERIENCE_TITLE_MAX, false);
        MonthYear start = checkDate(layout, CvBuilderField.EXPERIENCE_START_DATE.at(n), e.startDate());
        MonthYear end = checkDate(layout, CvBuilderField.EXPERIENCE_END_DATE.at(n), e.endDate());
        checkPrinted(layout, CvBuilderField.EXPERIENCE_DESCRIPTION.at(n), e.description(), false, DESCRIPTION_MAX, true);
        checkOrder(CvBuilderField.EXPERIENCE.at(n), start, end);
        // R-F8 - current null coi nhu false.
        if (Boolean.TRUE.equals(e.current()) && e.endDate() != null) {
            throw InvalidCvBuilderFormException.currentWithEndDate(CvBuilderField.EXPERIENCE.at(n));
        }
    }

    private static void checkCertification(Layout layout, int n, CvBuilderForm.Certification c) {
        checkPrinted(layout, CvBuilderField.CERTIFICATION_NAME.at(n), c.name(), true, CERTIFICATION_NAME_MAX, false);
        checkPrinted(layout, CvBuilderField.CERTIFICATION_ISSUER.at(n), c.issuer(), false, ISSUER_MAX, false);
        checkDate(layout, CvBuilderField.CERTIFICATION_ISSUE_DATE.at(n), c.issueDate());
    }

    private static void checkProject(Layout layout, int n, CvBuilderForm.Project p) {
        checkPrinted(layout, CvBuilderField.PROJECT_NAME.at(n), p.name(), true, PROJECT_NAME_MAX, false);
        checkPrinted(layout, CvBuilderField.PROJECT_DESCRIPTION.at(n), p.description(), false, DESCRIPTION_MAX, true);
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
            CvBuilderField.Path path = CvBuilderField.PROJECT_TECHNOLOGIES.at(n, j + 1);
            String technology = technologies.get(j);
            checkText(layout, path, technology, true, TAG_MAX, false);
            // L29 - in "a, b": dau phay dinh vao tu cuoi cua moi cong nghe tru cong nghe cuoi -> do kem dau phay.
            boolean followedBySeparator = j < technologies.size() - 1;
            checkWordsFit(layout, path,
                    followedBySeparator ? technology + CvPdfLabels.TECHNOLOGY_SEPARATOR.strip() : technology, false);
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

    // O khong co kiem dinh dang: toan bo chuoi kiem cua mot o, ket thuc bang R-F12.
    private static void checkPrinted(
            Layout layout, CvBuilderField.Path path, String value, boolean required, Integer maxLength,
            boolean multiLine) {
        checkText(layout, path, value, required, maxLength, multiLine);
        checkWordsFit(layout, path, value, false);
    }

    // bat buoc -> do dai -> ky tu dieu khien/dinh dang -> glyph (o co in). maxLength null = khong gioi han o R-F5.
    private static void checkText(
            Layout layout, CvBuilderField.Path path, String value, boolean required, Integer maxLength,
            boolean multiLine) {
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
        if (layout.isPrinted(path) && layout.lacksGlyph(value)) {
            throw InvalidCvBuilderFormException.unsupportedCharacter(path);
        }
    }

    // R-F12 - buoc cuoi cua moi o (sau dinh dang). Font + co theo o va mau (L3).
    private static void checkWordsFit(Layout layout, CvBuilderField.Path path, String value, boolean companyAsHeading) {
        if (value != null && !layout.wordsFit(path, value, companyAsHeading)) {
            throw InvalidCvBuilderFormException.wordTooLong(path);
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
    private static MonthYear checkDate(Layout layout, CvBuilderField.Path path, String value) {
        checkText(layout, path, value, false, null, false);
        if (value == null) {
            return null;
        }
        MonthYear parsed = parseDate(value);
        if (parsed == null) {
            throw InvalidCvBuilderFormException.invalidDate(path);
        }
        checkWordsFit(layout, path, value, false);
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

    // Mau duoc chon + hai font da nap cho MOT lan kiem.
    private record Layout(CvTemplate template, CvPdfFonts.Loaded fonts) {

        boolean isPrinted(CvBuilderField.Path path) {
            return CvPdfRenderer.styleOf(template, path.field(), false) != null;
        }

        // R-F4 phan glyph (a29) - ky tu phai co glyph trong CA HAI font. '\n' khong in thanh glyph (ngat dong).
        boolean lacksGlyph(String value) {
            String printable = value.replace("\n", "");
            return lacks(fonts.regular(), printable) || lacks(fonts.bold(), printable);
        }

        private static boolean lacks(PDFont font, String text) {
            try {
                font.encode(text);
                return false;
            } catch (IllegalArgumentException e) {
                return true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // O khong in (style null) luon "vua".
        boolean wordsFit(CvBuilderField.Path path, String value, boolean companyAsHeading) {
            CvPdfRenderer.Style style = CvPdfRenderer.styleOf(template, path.field(), companyAsHeading);
            if (style == null) {
                return true;
            }
            try {
                return CvPdfRenderer.wordsFit(fonts.of(style.bold()), style.size(), value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
