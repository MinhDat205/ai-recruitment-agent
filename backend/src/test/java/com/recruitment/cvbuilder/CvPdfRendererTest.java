package com.recruitment.cvbuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.resume.PdfBoxPoiTextExtractor;
import com.recruitment.resume.ResumeFileType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// FR-U12 T7 (K2 tren PDF that - Q-U3), T8 (khong chu thua - R-P5), T12 phan xac dinh (R-P10 - L10), ngat dong R-P7, moc
// L4c. Test don vi, khong Spring; font that trong src/main/resources/fonts, trich van ban bang PdfBoxPoiTextExtractor
// (cung bo trich cua R-P8).
class CvPdfRendererTest {

    private static final CvPdfFonts FONTS = new CvPdfFonts();
    private final CvPdfRenderer renderer = new CvPdfRenderer(FONTS);
    private final PdfBoxPoiTextExtractor extractor = new PdfBoxPoiTextExtractor();

    // ---- du lieu ----

    // Moi dong ~150 ky tu -> 2 dong PDF o 10,5 pt; 8 dong/muc x 6 muc -> du ≥ 60 dong noi dung, sang trang 2 (L15).
    private static String longDescription(int item) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            lines.add("- Hạng mục " + item + "." + i + ": phát triển dịch vụ <Java> & Spring cho hệ thống thanh toán, "
                    + "giảm 30% thời gian phản hồi, tích hợp C# và Node.js, hỗ trợ ứng dụng iOS cho khách hàng doanh nghiệp.");
        }
        // Dong trong giu nhu go (R-P7).
        lines.add(3, "");
        return String.join("\n", lines);
    }

    static CvBuilderForm fullForm() {
        List<CvBuilderForm.Experience> experience = new ArrayList<>();
        experience.add(new CvBuilderForm.Experience(
                "Công ty Cổ phần Phần mềm Ánh Dương", "Chuyên viên phát triển phần mềm", "07/2019", null, true,
                longDescription(1)));
        experience.add(new CvBuilderForm.Experience(
                "Ngân hàng TMCP Đông Á", null, "01/2018", "06/2019", false, longDescription(2)));
        experience.add(new CvBuilderForm.Experience(
                "Tập đoàn Viễn thông Quân đội", "Lập trình viên", "2017", null, null, longDescription(3)));
        experience.add(new CvBuilderForm.Experience(
                "Công ty TNHH Giải pháp Số Hưng Thịnh", "Thực tập sinh", null, "12/2016", null, longDescription(4)));
        experience.add(new CvBuilderForm.Experience(
                "Trung tâm Đổi mới Sáng tạo Quốc gia", "Cộng tác viên", "03/2015", "08/2015", null, longDescription(5)));
        experience.add(new CvBuilderForm.Experience(
                "Hợp tác xã Nông nghiệp Ứng Hòa", "Tình nguyện viên", "2014", "2014", null, longDescription(6)));
        return new CvBuilderForm(
                new CvBuilderForm.Contact(
                        "Nguyễn Quốc Huy",
                        "huy.nguyen@example.vn",
                        "0901 234 567",
                        "Số 12 ngõ 34 phố Huế, quận Hai Bà Trưng",
                        "https://www.linkedin.com/in/nguyen-quoc-huy"),
                "Kỹ sư phần mềm cao cấp",
                "Hà Nội",
                "IT_SOFTWARE",
                List.of(
                        new CvBuilderForm.Education(
                                "Đại học Bách khoa Hà Nội", "Kỹ sư", "Công nghệ thông tin", "09/2012", "06/2017", 3.25),
                        new CvBuilderForm.Education("Trường THPT Chu Văn An", "Tốt nghiệp", null, "2009", "2012", 8.5)),
                experience,
                List.of("Java", "Spring Boot", "C#", "Node.js", "iOS", "Giao tiếp", "Quản lý thời gian"),
                List.of(
                        new CvBuilderForm.Certification("IELTS 7.5", "IDP Việt Nam", "06/2021"),
                        new CvBuilderForm.Certification("Oracle Certified Professional Java SE 17", "Oracle", "2022")),
                List.of(
                        new CvBuilderForm.Project(
                                "Ứng dụng đặt lịch khám bệnh",
                                "Xây dựng API đặt lịch cho 20 phòng khám.\nTối ưu truy vấn, giảm 30% tải cơ sở dữ liệu.",
                                List.of("Java", "Spring Boot", "<React> & Redux", "PostgreSQL")),
                        new CvBuilderForm.Project("Bot hỗ trợ khách hàng", "Trả lời câu hỏi thường gặp.",
                                List.of("Node.js", "C#"))));
    }

    // R-P8 - moi chuoi van ban cua form: o chu, phan tu skills/technologies, moc, "Hiện tại" khi current, GPA da dinh
    // dang. KHONG gom industryCode (khong in - O1).
    static List<String> formStrings(CvBuilderForm form) {
        List<String> values = new ArrayList<>();
        CvBuilderForm.Contact c = form.contact();
        addAll(values, c.fullName(), c.email(), c.phone(), c.address(), c.linkedin(), form.currentTitle(),
                form.locationText());
        for (CvBuilderForm.Education e : form.education()) {
            addAll(values, e.school(), e.degree(), e.major(), e.startDate(), e.endDate());
        }
        for (CvBuilderForm.Experience e : form.experience()) {
            addAll(values, e.company(), e.title(), e.startDate(), e.endDate(), e.description());
            if (Boolean.TRUE.equals(e.current())) {
                values.add("Hiện tại");
            }
        }
        values.addAll(form.skills());
        for (CvBuilderForm.Certification x : form.certifications()) {
            addAll(values, x.name(), x.issuer(), x.issueDate());
        }
        for (CvBuilderForm.Project p : form.projects()) {
            addAll(values, p.name(), p.description());
            values.addAll(p.technologies());
        }
        return values;
    }

    private static void addAll(List<String> target, String... values) {
        for (String v : values) {
            if (v != null) {
                target.add(v);
            }
        }
    }

    private String extract(byte[] pdf) {
        return extractor.extract(new ByteArrayInputStream(pdf), ResumeFileType.PDF);
    }

    // ---- T7 ----

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t7_everyFormStringIsVerbatimInExtractedText(CvTemplate template) throws IOException {
        CvBuilderForm form = fullForm();
        String text = extract(renderer.render(template, form));

        List<String> strings = new ArrayList<>(formStrings(form));
        // GPA da dinh dang (R-F9), viet tay - khong goi lai ham cua renderer.
        strings.add("3.25");
        strings.add("8.5");
        assertThat(strings).hasSizeGreaterThan(50);
        for (String s : strings) {
            assertThat(VerbatimQuoteVerifier.isVerbatim(s, text)).as("khong khop K2: %s", s).isTrue();
        }
        // Chuoi ghep L4b/L4c: company · moc o dong 2 khi co title; dong 1 la company thi dong 2 chi co moc.
        assertThat(VerbatimQuoteVerifier.isVerbatim(
                "Công ty Cổ phần Phần mềm Ánh Dương · 07/2019 – Hiện tại", text)).isTrue();
        assertThat(VerbatimQuoteVerifier.isVerbatim("Ngân hàng TMCP Đông Á 01/2018 – 06/2019", text)).isTrue();
        assertThat(VerbatimQuoteVerifier.isVerbatim("GPA: 3.25", text)).isTrue();
        assertThat(VerbatimQuoteVerifier.isVerbatim(
                "Công nghệ: Java, Spring Boot, <React> & Redux, PostgreSQL", text)).isTrue();
        assertThat(VerbatimQuoteVerifier.isVerbatim(
                "Java · Spring Boot · C# · Node.js · iOS · Giao tiếp · Quản lý thời gian", text)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t7_atLeastTwoA4Pages_bothFontsEmbedded(CvTemplate template) throws IOException {
        byte[] pdf = renderer.render(template, fullForm());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            Set<String> fontNames = new HashSet<>();
            for (PDPage page : doc.getPages()) {
                PDRectangle box = page.getMediaBox();
                assertThat(box.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
                assertThat(box.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
                for (COSName name : page.getResources().getFontNames()) {
                    PDFont font = page.getResources().getFont(name);
                    assertThat(font.isEmbedded()).as(font.getName()).isTrue();
                    // Ten tap con dang "ABCDEF+NotoSans-Regular".
                    fontNames.add(font.getName().substring(font.getName().indexOf('+') + 1));
                }
            }
            assertThat(fontNames).containsExactlyInAnyOrder("NotoSans-Regular", "NotoSans-Bold");
            // R-P10 - khong metadata.
            PDDocumentInformation info = doc.getDocumentInformation();
            assertThat(info.getTitle()).isNull();
            assertThat(info.getAuthor()).isNull();
            assertThat(info.getCreator()).isNull();
            assertThat(info.getCreationDate()).isNull();
        }
    }

    // ---- T8 ----

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t8_removingFormValuesAndFixedTextLeavesOnlyWhitespace(CvTemplate template) throws IOException {
        CvBuilderForm form = fullForm();
        String remaining = VerbatimQuoteVerifier.normalizeWhitespace(extract(renderer.render(template, form)));

        List<String> values = new ArrayList<>(formStrings(form));
        values.add("3.25");
        values.add("8.5");
        // Gia tri dai truoc (mo ta chua ky nang), roi den nhan co dinh.
        values.sort(Comparator.comparingInt(String::length).reversed());
        for (String v : values) {
            remaining = remaining.replace(VerbatimQuoteVerifier.normalizeWhitespace(v), " ");
        }
        List<String> fixed = new ArrayList<>(CvPdfLabels.all());
        fixed.sort(Comparator.comparingInt(String::length).reversed());
        for (String f : fixed) {
            remaining = remaining.replace(f.strip(), " ");
        }
        assertThat(remaining).as("chu thua: [%s]", remaining).isBlank();
    }

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t8_industryCodeNotPrinted(CvTemplate template) throws IOException {
        String text = extract(renderer.render(template, fullForm()));
        assertThat(text).doesNotContain("IT_SOFTWARE");
    }

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t8_emptyBlocksHaveNoHeading(CvTemplate template) throws IOException {
        CvBuilderForm onlySkills = new CvBuilderForm(
                new CvBuilderForm.Contact("Lê Thị Lan", null, null, null, null), null, null, null,
                null, null, List.of("Excel"), null, null);
        String text = extract(renderer.render(template, onlySkills));
        assertThat(text).contains(CvPdfLabels.SKILLS);
        assertThat(text).doesNotContain(CvPdfLabels.EXPERIENCE, CvPdfLabels.EDUCATION, CvPdfLabels.PROJECTS,
                CvPdfLabels.CERTIFICATIONS, CvPdfLabels.EMAIL.strip(), CvPdfLabels.LOCATION.strip());
        assertThat(VerbatimQuoteVerifier.normalizeWhitespace(text)).isEqualTo("Lê Thị Lan Kỹ năng Excel");

        CvBuilderForm full = fullForm();
        CvBuilderForm noCertifications = new CvBuilderForm(full.contact(), full.currentTitle(), full.locationText(),
                full.industryCode(), full.education(), full.experience(), full.skills(), null, full.projects());
        assertThat(extract(renderer.render(template, noCertifications))).doesNotContain(CvPdfLabels.CERTIFICATIONS);
    }

    // ---- T12 phan xac dinh ----

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void t12_sameFormAndTemplate_sameExtractedText(CvTemplate template) throws IOException {
        String first = extract(renderer.render(template, fullForm()));
        String second = extract(new CvPdfRenderer(new CvPdfFonts()).render(template, fullForm()));
        assertThat(second).isEqualTo(first);
    }

    // ---- L30: dong dau cua muc khong bi bo lai mot minh cuoi trang ----

    // Muc kinh nghiem dem co k dong (moi dong logic ngan = 1 dong PDF), roi 4 muc can kiem. Quet k de moi muc lan luot
    // roi vao moi vi tri so voi ranh gioi trang (~54 dong/trang).
    private static CvBuilderForm boundaryForm(int k) {
        String filler = String.join("\n", IntStream.rangeClosed(1, k).mapToObj(i -> "Dòng đệm " + i).toList());
        return new CvBuilderForm(
                new CvBuilderForm.Contact("Trần Văn Bình", null, null, null, null), null, null, null,
                List.of(new CvBuilderForm.Education("Mốc C trường", "Mốc C bằng", null, null, null, null)),
                List.of(new CvBuilderForm.Experience("Công ty đệm", null, null, null, null, filler),
                        new CvBuilderForm.Experience("Mốc B công ty", "Mốc B chức danh", null, null, null, null)),
                null,
                List.of(new CvBuilderForm.Certification("Mốc E chứng chỉ", "Mốc E đơn vị", null)),
                List.of(new CvBuilderForm.Project("Mốc D dự án", "Mốc D mô tả", null)));
    }

    // Van ban tung trang (PDFTextStripper setStartPage/setEndPage).
    private static List<String> pageTexts(byte[] pdf) throws IOException {
        List<String> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(stripper.getText(doc));
            }
        }
        return pages;
    }

    private static int pageOf(List<String> pages, String marker) {
        List<Integer> found = IntStream.range(0, pages.size()).filter(i -> pages.get(i).contains(marker)).boxed().toList();
        assertThat(found).as("marker %s", marker).hasSize(1);
        return found.get(0);
    }

    @ParameterizedTest
    @EnumSource(CvTemplate.class)
    void l30_itemFirstLineStaysWithNextLine(CvTemplate template) throws IOException {
        // [dong dau cua muc, dong ke tiep]
        String[][] pairs = {
                {"Mốc B chức danh", "Mốc B công ty"},
                {"Mốc C trường", "Mốc C bằng"},
                {"Mốc D dự án", "Mốc D mô tả"},
                {"Mốc E chứng chỉ", "Mốc E đơn vị"}};
        int firstLineOpensPage = 0;
        for (int k = 1; k <= 70; k++) {
            List<String> pages = pageTexts(renderer.render(template, boundaryForm(k)));
            for (String[] pair : pairs) {
                int first = pageOf(pages, pair[0]);
                assertThat(pageOf(pages, pair[1])).as("k=%d, %s", k, pair[0]).isEqualTo(first);
                if (first > 0 && pages.get(first).strip().startsWith(pair[0])) {
                    firstLineOpensPage++;
                }
            }
        }
        // Chung minh phep quet thuc su cham ranh gioi trang (khong xanh vi moi thu nam tren trang 1).
        assertThat(firstLineOpensPage).isGreaterThan(0);
    }

    // ---- R-P7 ngat dong, L4c moc, R-F9 GPA, R-P2 font ----

    @Test
    void wrap_breaksOnlyAtSpaces_eachLineFits() throws IOException {
        String text = longDescription(9).replace("\n", " ");
        try (PDDocument doc = new PDDocument()) {
            PDFont font = FONTS.load(doc).regular();
            List<String> lines = CvPdfRenderer.wrap(font, CvPdfRenderer.BODY_SIZE, text);
            assertThat(lines).hasSizeGreaterThan(5);
            // Noi lai bang dung mot dau cach (dau cach bi bo o cho ngat) -> ra dung chuoi goc: khong ngat giua tu.
            assertThat(String.join(" ", lines)).isEqualTo(text);
            for (String line : lines) {
                assertThat(CvPdfRenderer.textWidth(font, CvPdfRenderer.BODY_SIZE, line))
                        .isLessThanOrEqualTo(CvPdfRenderer.LINE_WIDTH);
            }
            // Dong khong phai dong cuoi da "day": them tu dau dong sau vao se vuot.
            IntStream.range(0, lines.size() - 1).forEach(i -> {
                String next = lines.get(i + 1).split(" ")[0];
                try {
                    assertThat(CvPdfRenderer.textWidth(font, CvPdfRenderer.BODY_SIZE, lines.get(i) + " " + next))
                            .isGreaterThan(CvPdfRenderer.LINE_WIDTH);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test
    void dateText_rulesL4c() {
        assertThat(CvPdfRenderer.dateText("03/2019", "05/2020", false)).isEqualTo("03/2019 – 05/2020");
        assertThat(CvPdfRenderer.dateText("03/2019", null, true)).isEqualTo("03/2019 – Hiện tại");
        assertThat(CvPdfRenderer.dateText(null, null, true)).isEqualTo("Hiện tại");
        assertThat(CvPdfRenderer.dateText("03/2019", null, false)).isEqualTo("03/2019");
        assertThat(CvPdfRenderer.dateText(null, "2020", false)).isEqualTo("2020");
        assertThat(CvPdfRenderer.dateText(null, null, false)).isNull();
    }

    @Test
    void formatGpa_plainWithDot() {
        assertThat(CvPdfRenderer.formatGpa(3.25)).isEqualTo("3.25");
        assertThat(CvPdfRenderer.formatGpa(3.2)).isEqualTo("3.2");
        assertThat(CvPdfRenderer.formatGpa(10.0)).isEqualTo("10");
        assertThat(CvPdfRenderer.formatGpa(0.0)).isEqualTo("0");
    }

    @Test
    void currentLabel_sharedWithPayloadMapper() {
        assertThat(CvPdfLabels.CURRENT).isSameAs(CvBuilderPayloadMapper.CURRENT_END_DATE);
    }

    @Test
    void lineWidth_fromA4MinusMargins() {
        assertThat(CvPdfRenderer.LINE_WIDTH).isEqualTo(PDRectangle.A4.getWidth() - 2 * 50);
    }

    @Test
    void missingFontFile_failsWithFileName() {
        assertThatThrownBy(() -> new CvPdfFonts("khong-co-thu-muc/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("khong-co-thu-muc/NotoSans-Regular.ttf");
    }
}
