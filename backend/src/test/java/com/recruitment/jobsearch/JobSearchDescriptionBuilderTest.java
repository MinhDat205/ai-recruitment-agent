package com.recruitment.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.jobsearch.JobSearchConditionResolver.Resolution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

// FR-U13 T8 (unit, khong Spring) - phan mo ta R-D: doan cat (R-D1), cach cat (R-D2), don (R-D3), khong them chu (R-D4,
// kiem tren 20 cau sinh ngau nhien seed co dinh), co nhan than (R-D5).
class JobSearchDescriptionBuilderTest {

    private static final String README_QUERY =
            "content marketing lương trên 15 triệu ở Hà Nội, không yêu cầu kinh nghiệm";

    private static Set<String> ordered(String... texts) {
        return new LinkedHashSet<>(Arrays.asList(texts));
    }

    private static Resolution resolution(Set<String> accepted, Set<String> dropped, List<String> personal) {
        return new Resolution(List.of(), List.of(), null, null, List.of(), accepted, dropped, personal);
    }

    private static Resolution accepted(String... texts) {
        return resolution(ordered(texts), Set.of(), List.of());
    }

    private static String build(String q, Resolution resolution) {
        return JobSearchDescriptionBuilder.build(q, resolution);
    }

    // ---- R-D1/R-D2/R-D3 ----

    @Test
    void readmeExample_cutsAcceptedTexts_mergesComma() {
        assertThat(build(README_QUERY, accepted("lương trên 15 triệu", "ở Hà Nội")))
                .isEqualTo("content marketing, không yêu cầu kinh nghiệm");
    }

    @Test
    void droppedConditionText_keptInDescription() {
        String q = "kế toán ở Sao Hoả";
        assertThat(build(q, resolution(Set.of(), ordered("ở Sao Hoả"), List.of()))).isEqualTo(q);
    }

    // Doan dung chung boi mot dieu kien nhan va mot dieu kien bo -> giu nguyen (Q-T4).
    @Test
    void textSharedByAcceptedAndDropped_kept() {
        String q = "kế toán ở Hà Nội hoặc Sao Hoả";
        String shared = "ở Hà Nội hoặc Sao Hoả";
        assertThat(build(q, resolution(ordered(shared), ordered(shared), List.of()))).isEqualTo(q);
    }

    // Doan nhan than luon bi cat - ke ca khi trung voi doan cua dieu kien bi bo.
    @Test
    void personalInfoText_alwaysCut() {
        assertThat(build("kế toán, nữ dưới 30 tuổi", resolution(Set.of(), Set.of(), List.of("nữ dưới 30 tuổi"))))
                .isEqualTo("kế toán");
        assertThat(build("kế toán nữ dưới 30 tuổi",
                resolution(Set.of(), ordered("nữ dưới 30 tuổi"), List.of("nữ dưới 30 tuổi"))))
                .isEqualTo("kế toán");
    }

    // Doan nhan than o dang chua gop khoang trang van cat duoc (R-D2 chuan hoa truoc khi tim).
    @Test
    void personalInfoText_whitespaceNormalizedBeforeSearch() {
        assertThat(build("kế toán nữ dưới 30 tuổi", resolution(Set.of(), Set.of(), List.of("  nữ  dưới\n30 tuổi"))))
                .isEqualTo("kế toán");
    }

    // Hai doan long nhau: dai cat truoc; ngan tim trong chuoi CON LAI - khong con -> bo qua. Thu tu dau vao (ngan truoc)
    // khong anh huong.
    @Test
    void nestedTexts_longerCutFirst_shorterSkipped() {
        String q = "kế toán lương trên 15 triệu ở Hà Nội, làm việc ở Hà Nội cũ";
        // "lương trên 15 triệu ở Hà Nội" cat truoc; "ở Hà Nội" con mot lan o cuoi cau -> cat o do.
        assertThat(build(q, accepted("ở Hà Nội", "lương trên 15 triệu ở Hà Nội")))
                .isEqualTo("kế toán, làm việc cũ");
        assertThat(build("kế toán lương trên 15 triệu ở Hà Nội", accepted("ở Hà Nội", "lương trên 15 triệu ở Hà Nội")))
                .isEqualTo("kế toán");
    }

    // Chi cat lan xuat hien DAU.
    @Test
    void onlyFirstOccurrenceCut() {
        assertThat(build("ở Hà Nội và ở Hà Nội", accepted("ở Hà Nội"))).isEqualTo("và ở Hà Nội");
    }

    @Test
    void everythingCut_null() {
        assertThat(build("ở Hà Nội", accepted("ở Hà Nội"))).isNull();
        assertThat(build("lương trên 15 triệu ở Hà Nội", accepted("ở Hà Nội", "lương trên 15 triệu"))).isNull();
    }

    @Test
    void onlyPunctuationLeft_null() {
        assertThat(build("ở Hà Nội, . lương trên 15 triệu", accepted("ở Hà Nội", "lương trên 15 triệu"))).isNull();
        assertThat(build("ở Hà Nội - — –", accepted("ở Hà Nội"))).isNull();
        // L25 - "!" "?" khong nam trong tap dau o hai dau (R-D3) nhung khong con chu cai/chu so -> null.
        assertThat(build("ở Hà Nội?!", accepted("ở Hà Nội"))).isNull();
    }

    // Chuoi dau [,;:] lien tiep (xen dau cach) -> mot dau phay.
    @Test
    void punctuationRuns_collapsedToSingleComma() {
        assertThat(build("kế toán, ở Hà Nội, làm từ xa, không cần kinh nghiệm", accepted("ở Hà Nội", "làm từ xa")))
                .isEqualTo("kế toán, không cần kinh nghiệm");
        assertThat(build("kế toán; ở Hà Nội: tổng hợp", accepted("ở Hà Nội"))).isEqualTo("kế toán, tổng hợp");
    }

    // L24 - mot dau don le giu nguyen (khong doi ":" thanh ",").
    @Test
    void singlePunctuation_keptAsIs() {
        assertThat(build("Kỹ năng: Java ở Hà Nội", accepted("ở Hà Nội"))).isEqualTo("Kỹ năng: Java");
    }

    // Dau cach truoc dau cau bi bo; dau cau va gach noi o hai dau bi bo; gach noi ben trong giu nguyen.
    @Test
    void spacesBeforePunctuationAndEdgeDashesRemoved_innerDashKept() {
        assertThat(build("ở Hà Nội - kế toán tổng hợp", accepted("ở Hà Nội"))).isEqualTo("kế toán tổng hợp");
        assertThat(build("kế toán — ở Hà Nội", accepted("ở Hà Nội"))).isEqualTo("kế toán");
        assertThat(build("kế toán ở Hà Nội .", accepted("ở Hà Nội"))).isEqualTo("kế toán");
        assertThat(build("làm ở Bà Rịa - Vũng Tàu remote", accepted("remote"))).isEqualTo("làm ở Bà Rịa - Vũng Tàu");
        assertThat(build("kế toán ở Hà Nội !", accepted("ở Hà Nội"))).isEqualTo("kế toán!");
    }

    @Test
    void nothingToCut_descriptionIsQuery() {
        assertThat(build(README_QUERY, resolution(Set.of(), Set.of(), List.of()))).isEqualTo(README_QUERY);
    }

    // Doan khong co trong cau (khong xay ra sau R-V - phong ve) -> bo qua, khong loi.
    @Test
    void textNotInQuery_ignored() {
        assertThat(build("kế toán", accepted("ở Hà Nội"))).isEqualTo("kế toán");
    }

    // ---- L56: chi con tu dem -> null ----

    @Test
    void fillerOnly_null() {
        assertThat(build("việc", accepted())).isNull();
        assertThat(build("Việc", accepted())).isNull();
        assertThat(build("tìm việc làm", accepted())).isNull();
        // Hai dau cach - gop truoc khi so.
        assertThat(build("việc  làm", accepted())).isNull();
        assertThat(JobSearchDescriptionBuilder.isFiller("việc  làm")).isTrue();
        // Con dau cau o hai dau sau khi cat -> R-D3 bo truoc, roi moi so.
        assertThat(build("việc ở Hà Nội,", accepted("ở Hà Nội"))).isNull();
    }

    // Chi so trung TOAN BO; khong co nhom "tuyen" (L56).
    @Test
    void nonFiller_kept() {
        assertThat(build("việc nhẹ", accepted())).isEqualTo("việc nhẹ");
        assertThat(build("Java", accepted())).isEqualTo("Java");
        assertThat(build("tuyển dụng", accepted())).isEqualTo("tuyển dụng");
        assertThat(build("tìm việc gấp", accepted())).isEqualTo("tìm việc gấp");
    }

    // 7.2 B cau 3: cat ba doan cua nganh, khu vuc, hinh thuc -> con "việc" -> null (truoc L56: "việc").
    @Test
    void gate72bCase3_threeTextsCut_null() {
        String q = "việc hành chính văn phòng Hà Nội hoặc Sài Gòn, làm kết hợp";
        assertThat(build(q, accepted("hành chính văn phòng", "Hà Nội hoặc Sài Gòn", "làm kết hợp"))).isNull();
    }

    // ---- R-D5 ----

    @Test
    void personalInfoIgnored_trueOnlyWhenPersonalTexts() {
        assertThat(JobSearchDescriptionBuilder.personalInfoIgnored(
                resolution(Set.of(), Set.of(), List.of("nữ")))).isTrue();
        assertThat(JobSearchDescriptionBuilder.personalInfoIgnored(
                resolution(ordered("ở Hà Nội"), Set.of(), List.of()))).isFalse();
    }

    // ---- R-D4: khong them chu - 20 cau ngau nhien, seed co dinh ----

    private static final String[] WORDS = {
        "kế", "toán", "Hà", "Nội", "lương", "trên", "15", "triệu", "ở", "làm", "từ", "xa", "Java", "<b>", "&",
        "Đà", "Nẵng", ",", ".", ";", ":", "-", "—", "!", "?", "không", "yêu", "cầu", "kinh", "nghiệm", "nữ", "30"
    };

    @Test
    void randomQueries_descriptionOnlyUsesQueryCharactersPlusSpaceAndComma() {
        Random random = new Random(20261018L);
        int nonNull = 0;
        for (int round = 0; round < 20; round++) {
            List<String> words = new ArrayList<>();
            int length = 3 + random.nextInt(14);
            for (int i = 0; i < length; i++) {
                words.add(WORDS[random.nextInt(WORDS.length)]);
            }
            String q = VerbatimQuoteVerifier.normalizeWhitespace(String.join(" ", words));

            Set<String> accepted = new LinkedHashSet<>();
            Set<String> dropped = new LinkedHashSet<>();
            List<String> personal = new ArrayList<>();
            int spans = 1 + random.nextInt(4);
            for (int s = 0; s < spans; s++) {
                int from = random.nextInt(words.size());
                int to = from + 1 + random.nextInt(Math.min(4, words.size() - from));
                String text = String.join(" ", words.subList(from, to));
                switch (random.nextInt(3)) {
                    case 0 -> accepted.add(text);
                    case 1 -> dropped.add(text);
                    default -> personal.add(text);
                }
            }

            String description = build(q, resolution(accepted, dropped, personal));

            if (description == null) {
                continue;
            }
            nonNull++;
            assertThat(description).as("q=[%s]", q).isNotBlank();
            for (int i = 0; i < description.length(); i++) {
                char ch = description.charAt(i);
                assertThat(ch == ' ' || ch == ',' || q.indexOf(ch) >= 0)
                        .as("ky tu [%s] cua [%s] khong co trong q=[%s]", ch, description, q)
                        .isTrue();
            }
        }
        // Chong pass rong: phan lon cau con lai chu.
        assertThat(nonNull).isGreaterThanOrEqualTo(10);
    }
}
