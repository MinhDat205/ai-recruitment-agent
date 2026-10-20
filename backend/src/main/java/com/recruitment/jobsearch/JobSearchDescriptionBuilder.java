package com.recruitment.jobsearch;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.jobsearch.JobSearchConditionResolver.Resolution;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// FR-U13 R-D - phan mo ta = chu NGUYEN VAN con lai cua cau sau khi cat doan cua dieu kien da nhan va doan nhan than.
// AI chi chi ra doan nao (sourceText/personalInfoTexts, da qua K2), backend tu cat - chu AI viet KHONG BAO GIO vao
// description hay embedding (R-D4): ket qua chi gom ky tu cua q, them toi da dau cach va dau phay gop.
final class JobSearchDescriptionBuilder {

    private JobSearchDescriptionBuilder() {}

    // R-D3 - dau cach dung truoc dau cau.
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile(" +([,.;:!?])");
    // R-D3 - chuoi tu 2 dau [,;:] lien tiep (co the xen dau cach) -> mot dau phay. Mot dau don le giu nguyen (L24).
    private static final Pattern PUNCTUATION_RUN = Pattern.compile("[,;:](?:\\s*[,;:])+");
    // R-D3 - dau cau va gach noi o hai dau.
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s,.;:\\-–—]+|[\\s,.;:\\-–—]+$");
    // L56 - phan con lai TRUNG TOAN BO mot muc nay (sau ha chu thuong + gop khoang trang) -> khong con noi dung mo ta.
    // Khong co nhom "tuyen" ("tuyển dụng" co the chinh la nganh ung vien muon tim). Chi so trung toan bo, khong cat tu
    // dem khoi mo ta dai hon ("việc nhẹ" giu nguyen).
    static final Set<String> FILLER_DESCRIPTIONS = Set.of(
            "việc", "việc làm", "công việc", "tìm việc", "tìm việc làm", "tìm công việc", "cần tìm việc",
            "muốn tìm việc", "làm", "làm việc",
            "viec", "viec lam", "cong viec", "tim viec", "tim viec lam", "lam viec");

    // q: cau DA chuan hoa R-I2. Tra null khi khong con chu nao (R-D3).
    static String build(String q, Resolution resolution) {
        return clean(cut(q, textsToCut(resolution)));
    }

    // R-D5.
    static boolean personalInfoIgnored(Resolution resolution) {
        return !resolution.personalInfoTexts().isEmpty();
    }

    // R-D1 - moi doan nhan than; moi doan co it nhat mot dieu kien NHAN tro toi va KHONG dieu kien BO nao tro toi.
    static List<String> textsToCut(Resolution resolution) {
        Set<String> texts = new LinkedHashSet<>();
        for (String text : resolution.personalInfoTexts()) {
            texts.add(VerbatimQuoteVerifier.normalizeWhitespace(text));
        }
        for (String text : resolution.acceptedSourceTexts()) {
            if (!resolution.droppedSourceTexts().contains(text)) {
                texts.add(text);
            }
        }
        return new ArrayList<>(texts);
    }

    // R-D2 - doan dai cat truoc (sort on dinh: bang do dai giu thu tu vao); moi doan thay lan xuat hien DAU bang mot dau
    // cach; khong thay (da nam trong doan dai hon da cat) -> bo qua.
    static String cut(String q, List<String> texts) {
        List<String> ordered = new ArrayList<>();
        for (String text : texts) {
            String normalized = VerbatimQuoteVerifier.normalizeWhitespace(text);
            if (!normalized.isEmpty()) {
                ordered.add(normalized);
            }
        }
        ordered.sort(Comparator.comparingInt(String::length).reversed());

        String current = q;
        for (String text : ordered) {
            int index = current.indexOf(text);
            if (index >= 0) {
                current = current.substring(0, index) + " " + current.substring(index + text.length());
            }
        }
        return current;
    }

    // R-D3 - don; rong hoac khong con chu cai/chu so nao (chi con dau cau) -> null; chi con tu dem (L56) -> null.
    static String clean(String value) {
        String s = VerbatimQuoteVerifier.normalizeWhitespace(value);
        s = SPACE_BEFORE_PUNCTUATION.matcher(s).replaceAll("$1");
        s = PUNCTUATION_RUN.matcher(s).replaceAll(",");
        s = EDGE_PUNCTUATION.matcher(s).replaceAll("");
        s = s.trim();
        if (s.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return null;
        }
        if (isFiller(s)) {
            return null;
        }
        return s;
    }

    // L56 - ban chuan hoa CHI de so; description tra ra van la chuoi sau R-D3 (R-D4 khong doi).
    static boolean isFiller(String value) {
        String key = VerbatimQuoteVerifier.normalizeWhitespace(value.toLowerCase(Locale.ROOT));
        return FILLER_DESCRIPTIONS.contains(key);
    }
}
