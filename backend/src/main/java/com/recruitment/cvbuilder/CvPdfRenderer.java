package com.recruitment.cvbuilder;

import com.recruitment.common.exception.CvBuilderField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Component;

// FR-U12 R-P - dung PDF tu form DA chuan hoa va DA qua validator. PDFBox thuan (R-P1), A4 doc, le 50 pt (R-P3), hai mau
// mot cot (R-P4). Chi in hai loai chu: gia tri o form nguyen van va nhan co dinh CvPdfLabels (R-P5); industryCode khong in
// (O1); khoi rong khong in tieu de. Khong metadata, khong thoi gian, khong so ngau nhien (R-P10).
// styleOf + textWidth + wordsFit la MOT ham do/chon font dung chung voi validator (L3, R-F12).
@Component
public class CvPdfRenderer {

    static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    static final float MARGIN = 50;
    // Be rong dong noi dung cua ca hai mau (13.2) - tinh tu A4, khong go so.
    public static final float LINE_WIDTH = PAGE_SIZE.getWidth() - 2 * MARGIN;
    static final float LINE_SPACING = 1.3f;

    static final float BODY_SIZE = 10.5f;
    static final float CONTACT_SIZE = 10f;
    static final float CURRENT_TITLE_SIZE = 12f;
    static final float CLASSIC_NAME_SIZE = 20f;
    static final float MODERN_NAME_SIZE = 24f;
    static final float CLASSIC_HEADING_SIZE = 12f;
    static final float MODERN_HEADING_SIZE = 13f;
    static final float RULE_WIDTH = 0.5f;
    // #1F3A5F - mau duy nhat ngoai den, chi o tieu de muc cua MODERN.
    private static final float[] MODERN_HEADING_RGB = {0x1F / 255f, 0x3A / 255f, 0x5F / 255f};

    private static final float SECTION_GAP = 10f;
    private static final float ITEM_GAP = 5f;
    private static final float RULE_OFFSET = 4f;
    // L30 - dong dau cua muc + mot dong noi dung ke tiep, deu co 10,5.
    private static final float ITEM_START_HEIGHT = 2 * BODY_SIZE * LINE_SPACING;

    private static final Style BODY = new Style(false, BODY_SIZE);
    private static final Style BODY_BOLD = new Style(true, BODY_SIZE);
    private static final Style CONTACT = new Style(false, CONTACT_SIZE);
    private static final Style CURRENT_TITLE = new Style(false, CURRENT_TITLE_SIZE);

    // Kieu chu cua mot o: dam/thuong + co (pt).
    public record Style(boolean bold, float size) {}

    private final CvPdfFonts fonts;

    public CvPdfRenderer(CvPdfFonts fonts) {
        this.fonts = fonts;
    }

    // ---- ham do dung chung (L3) ----

    // L3 - font + co cua tung o tren mau. companyAsHeading: EXPERIENCE_COMPANY in dam o dong 1 khi muc khong co title.
    // null = o khong in (industryCode, versionLabel) hoac la ca khoi - khong kiem glyph, khong kiem R-F12.
    public static Style styleOf(CvTemplate template, CvBuilderField field, boolean companyAsHeading) {
        return switch (field) {
            case FULL_NAME -> new Style(true, template == CvTemplate.MODERN ? MODERN_NAME_SIZE : CLASSIC_NAME_SIZE);
            case CURRENT_TITLE -> CURRENT_TITLE;
            case EMAIL, PHONE, ADDRESS, LINKEDIN, LOCATION_TEXT -> CONTACT;
            case EDUCATION_SCHOOL, EXPERIENCE_TITLE, PROJECT_NAME, CERTIFICATION_NAME -> BODY_BOLD;
            case EXPERIENCE_COMPANY -> companyAsHeading ? BODY_BOLD : BODY;
            case EDUCATION_DEGREE, EDUCATION_MAJOR, EDUCATION_START_DATE, EDUCATION_END_DATE, EDUCATION_GPA,
                    EXPERIENCE_START_DATE, EXPERIENCE_END_DATE, EXPERIENCE_DESCRIPTION, SKILLS,
                    CERTIFICATION_ISSUER, CERTIFICATION_ISSUE_DATE, PROJECT_DESCRIPTION, PROJECT_TECHNOLOGIES -> BODY;
            case INDUSTRY_CODE, VERSION_LABEL, EDUCATION, EXPERIENCE, CERTIFICATIONS, PROJECTS -> null;
        };
    }

    // Do rong (pt) - getStringWidth tra don vi 1/1000 em. Nem IllegalArgumentException neu thieu glyph.
    public static float textWidth(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(text) * size / 1000f;
    }

    // R-F12 - true khi moi "tu" (chuoi lien khong co dau cach/xuong dong) vua mot dong. Cung phep so voi wrap().
    public static boolean wordsFit(PDFont font, float size, String value) throws IOException {
        for (String word : value.split("[ \n]")) {
            if (!word.isEmpty() && textWidth(font, size, word) > LINE_WIDTH) {
                return false;
            }
        }
        return true;
    }

    // R-P7 - ngat CHI tai dau cach (xuong dong '\n' xu ly o paragraph); dau cach o cho ngat bi bo; khong ngat giua tu,
    // khong gach noi. Tu dai hon dong (R-F12 da chan) duoc in nguyen tren mot dong.
    static List<String> wrap(PDFont font, float size, String text) throws IOException {
        List<String> lines = new ArrayList<>();
        String current = null;
        for (String word : text.split(" ", -1)) {
            if (current == null) {
                current = word;
                continue;
            }
            String candidate = current + " " + word;
            if (textWidth(font, size, candidate) <= LINE_WIDTH) {
                current = candidate;
            } else {
                lines.add(current);
                current = word;
            }
        }
        lines.add(current);
        return lines;
    }

    // ---- chuoi ghep tu gia tri + nhan ----

    // L4c - co ca hai -> start – end; current -> start – Hiện tại (khong start -> "Hiện tại"); chi start/chi end -> no;
    // khong moc nao -> null (khong in).
    static String dateText(String start, String end, boolean current) {
        String last = current ? CvPdfLabels.CURRENT : end;
        if (start != null && last != null) {
            return start + CvPdfLabels.DATE_RANGE + last;
        }
        return start != null ? start : last;
    }

    // R-F9 - dau cham, bo so 0 thua.
    static String formatGpa(Double gpa) {
        return BigDecimal.valueOf(gpa).stripTrailingZeros().toPlainString();
    }

    private static String joinNonNull(String separator, String... parts) {
        String joined = Stream.of(parts).filter(Objects::nonNull).collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    // ---- dung PDF ----

    public byte[] render(CvTemplate template, CvBuilderForm form) throws IOException {
        try (PDDocument document = new PDDocument()) {
            try (Writer writer = new Writer(document, fonts.load(document), template)) {
                writeForm(writer, template, form);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    // R-P6 - ho ten -> chuc danh -> lien he -> Kinh nghiem -> Hoc van -> Du an -> Ky nang -> Chung chi. Trong khoi: dung
    // thu tu muc cua form.
    private static void writeForm(Writer w, CvTemplate t, CvBuilderForm form) throws IOException {
        boolean centered = t == CvTemplate.MODERN;
        CvBuilderForm.Contact contact = form.contact();
        if (contact != null && contact.fullName() != null) {
            w.paragraph(contact.fullName(), styleOf(t, CvBuilderField.FULL_NAME, false), centered);
        }
        if (form.currentTitle() != null) {
            w.paragraph(form.currentTitle(), styleOf(t, CvBuilderField.CURRENT_TITLE, false), centered);
        }
        if (contact != null) {
            w.prefixed(CvPdfLabels.EMAIL, contact.email(), styleOf(t, CvBuilderField.EMAIL, false), centered);
            w.prefixed(CvPdfLabels.PHONE, contact.phone(), styleOf(t, CvBuilderField.PHONE, false), centered);
            w.prefixed(CvPdfLabels.ADDRESS, contact.address(), styleOf(t, CvBuilderField.ADDRESS, false), centered);
            w.prefixed(CvPdfLabels.LINKEDIN, contact.linkedin(), styleOf(t, CvBuilderField.LINKEDIN, false), centered);
        }
        w.prefixed(CvPdfLabels.LOCATION, form.locationText(), styleOf(t, CvBuilderField.LOCATION_TEXT, false), centered);

        if (!form.experience().isEmpty()) {
            w.heading(CvPdfLabels.EXPERIENCE);
            for (int i = 0; i < form.experience().size(); i++) {
                w.startItem(i);
                writeExperience(w, t, form.experience().get(i));
            }
        }
        if (!form.education().isEmpty()) {
            w.heading(CvPdfLabels.EDUCATION);
            for (int i = 0; i < form.education().size(); i++) {
                w.startItem(i);
                writeEducation(w, t, form.education().get(i));
            }
        }
        if (!form.projects().isEmpty()) {
            w.heading(CvPdfLabels.PROJECTS);
            for (int i = 0; i < form.projects().size(); i++) {
                w.startItem(i);
                writeProject(w, t, form.projects().get(i));
            }
        }
        if (!form.skills().isEmpty()) {
            w.heading(CvPdfLabels.SKILLS);
            w.paragraph(String.join(CvPdfLabels.SEPARATOR, form.skills()), styleOf(t, CvBuilderField.SKILLS, false), false);
        }
        if (!form.certifications().isEmpty()) {
            w.heading(CvPdfLabels.CERTIFICATIONS);
            for (int i = 0; i < form.certifications().size(); i++) {
                w.startItem(i);
                writeCertification(w, t, form.certifications().get(i));
            }
        }
    }

    // Dong 1: title (dam) - khong co title thi company (dam). Dong 2: company · moc (L4b) - dong 1 la company thi chi moc.
    private static void writeExperience(Writer w, CvTemplate t, CvBuilderForm.Experience e) throws IOException {
        String dates = dateText(e.startDate(), e.endDate(), Boolean.TRUE.equals(e.current()));
        if (e.title() != null) {
            w.paragraph(e.title(), styleOf(t, CvBuilderField.EXPERIENCE_TITLE, false), false);
            w.paragraph(joinNonNull(CvPdfLabels.SEPARATOR, e.company(), dates),
                    styleOf(t, CvBuilderField.EXPERIENCE_COMPANY, false), false);
        } else {
            w.paragraph(e.company(), styleOf(t, CvBuilderField.EXPERIENCE_COMPANY, true), false);
            w.paragraph(dates, styleOf(t, CvBuilderField.EXPERIENCE_START_DATE, false), false);
        }
        w.paragraph(e.description(), styleOf(t, CvBuilderField.EXPERIENCE_DESCRIPTION, false), false);
    }

    // school (dam), degree · major, moc, "GPA: …".
    private static void writeEducation(Writer w, CvTemplate t, CvBuilderForm.Education e) throws IOException {
        w.paragraph(e.school(), styleOf(t, CvBuilderField.EDUCATION_SCHOOL, false), false);
        w.paragraph(joinNonNull(CvPdfLabels.SEPARATOR, e.degree(), e.major()),
                styleOf(t, CvBuilderField.EDUCATION_DEGREE, false), false);
        w.paragraph(dateText(e.startDate(), e.endDate(), false),
                styleOf(t, CvBuilderField.EDUCATION_START_DATE, false), false);
        if (e.gpa() != null) {
            w.prefixed(CvPdfLabels.GPA, formatGpa(e.gpa()), styleOf(t, CvBuilderField.EDUCATION_GPA, false), false);
        }
    }

    // name (dam), description, "Công nghệ: " + technologies noi ", ".
    private static void writeProject(Writer w, CvTemplate t, CvBuilderForm.Project p) throws IOException {
        w.paragraph(p.name(), styleOf(t, CvBuilderField.PROJECT_NAME, false), false);
        w.paragraph(p.description(), styleOf(t, CvBuilderField.PROJECT_DESCRIPTION, false), false);
        if (!p.technologies().isEmpty()) {
            w.prefixed(CvPdfLabels.TECHNOLOGIES, String.join(CvPdfLabels.TECHNOLOGY_SEPARATOR, p.technologies()),
                    styleOf(t, CvBuilderField.PROJECT_TECHNOLOGIES, false), false);
        }
    }

    // name (dam), "Cấp bởi: " + issuer, issueDate.
    private static void writeCertification(Writer w, CvTemplate t, CvBuilderForm.Certification c) throws IOException {
        w.paragraph(c.name(), styleOf(t, CvBuilderField.CERTIFICATION_NAME, false), false);
        w.prefixed(CvPdfLabels.ISSUER, c.issuer(), styleOf(t, CvBuilderField.CERTIFICATION_ISSUER, false), false);
        w.paragraph(c.issueDate(), styleOf(t, CvBuilderField.CERTIFICATION_ISSUE_DATE, false), false);
    }

    // Con tro viet tu tren xuong; het cho -> trang moi (R-P3). Moi dong mot showText (R-P7).
    private static final class Writer implements AutoCloseable {

        private final PDDocument document;
        private final CvPdfFonts.Loaded fonts;
        private final CvTemplate template;
        private PDPageContentStream content;
        private float y;

        Writer(PDDocument document, CvPdfFonts.Loaded fonts, CvTemplate template) throws IOException {
            this.document = document;
            this.fonts = fonts;
            this.template = template;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        // value null -> khong in gi.
        void paragraph(String value, Style style, boolean centered) throws IOException {
            if (value == null) {
                return;
            }
            PDFont font = fonts.of(style.bold());
            // Dong trong lien tiep trong description giu nhu go.
            for (String logicalLine : value.split("\n", -1)) {
                for (String line : wrap(font, style.size(), logicalLine)) {
                    line(line, font, style.size(), centered, null);
                }
            }
        }

        // Tien to + gia tri thanh mot doan; ngat duoc tai dau cach sau tien to.
        void prefixed(String prefix, String value, Style style, boolean centered) throws IOException {
            if (value != null) {
                paragraph(prefix + value, style, centered);
            }
        }

        // Tieu de muc: CLASSIC Bold 12 + duong ke 0,5 pt; MODERN Bold 13 mau #1F3A5F, khong ke (R-P4). Giu tieu de cung
        // trang voi dong dau cua muc dau tien VA dong ke tiep (L30) - neu khong, keepItemStart() se day muc sang trang
        // moi va bo tieu de lai mot minh.
        void heading(String text) throws IOException {
            boolean modern = template == CvTemplate.MODERN;
            float size = modern ? MODERN_HEADING_SIZE : CLASSIC_HEADING_SIZE;
            y -= SECTION_GAP;
            if (y - size * LINE_SPACING - RULE_OFFSET - ITEM_START_HEIGHT < MARGIN) {
                newPage();
            }
            line(text, fonts.bold(), size, false, modern ? MODERN_HEADING_RGB : null);
            if (!modern) {
                content.setLineWidth(RULE_WIDTH);
                content.moveTo(MARGIN, y - RULE_OFFSET);
                content.lineTo(MARGIN + LINE_WIDTH, y - RULE_OFFSET);
                content.stroke();
            }
            y -= RULE_OFFSET;
        }

        // Khoang cach giua hai muc, roi L30: dong dau cua muc (Bold 10,5) khong bi bo lai mot minh cuoi trang - can cho
        // cho no + mot dong noi dung ke tiep (10,5), khong du thi sang trang moi.
        void startItem(int index) throws IOException {
            if (index > 0) {
                y -= ITEM_GAP;
            }
            if (y - ITEM_START_HEIGHT < MARGIN) {
                newPage();
            }
        }

        private void line(String text, PDFont font, float size, boolean centered, float[] rgb) throws IOException {
            float advance = size * LINE_SPACING;
            if (y - advance < MARGIN) {
                newPage();
            }
            y -= advance;
            if (text.isEmpty()) {
                return;
            }
            float x = centered ? MARGIN + (LINE_WIDTH - textWidth(font, size, text)) / 2 : MARGIN;
            if (rgb != null) {
                content.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
            }
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
            if (rgb != null) {
                content.setNonStrokingColor(0f);
            }
        }

        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
