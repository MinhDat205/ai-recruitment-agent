package com.recruitment.ai.citation;

// ai/citation/ - K2. Tach tu CriterionScoringService.validate (FR-H04) - HANH VI GIU NGUYEN.
// Khong bean Spring, khong doc DB, khong phu thuoc package nghiep vu nao. K2 KHONG chan quote rong,
// KHONG chuan hoa NFC, KHONG doi hoa/thuong (R-K2-3) - dieu kien chat hon cua tung FR dat o FR do.
public final class VerbatimQuoteVerifier {

    private VerbatimQuoteVerifier() {}

    // Gop moi chuoi khoang trang lien tiep (ke ca xuong dong, tab) thanh mot space, trim hai dau.
    // KHONG lowercase, KHONG bo dau tieng Viet, KHONG chuan hoa Unicode - ha thap yeu cau khop theo
    // huong do de khien mot cau bia noi dung khac nhung na na lot qua, nguy hiem hon la chan nham.
    public static String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    // quote == null -> false. source == null -> NullPointerException (loi lap trinh: raw_text NOT NULL).
    // Chuan hoa source TRUOC khi xet quote - giu dung thu tu cua vong kiem cu o H04 (rawText chuan hoa
    // truoc vong lap), nen source null luon nem NPE ke ca khi quote null.
    // true khi normalizeWhitespace(source).contains(normalizeWhitespace(quote)).
    public static boolean isVerbatim(String quote, String source) {
        String normalizedSource = normalizeWhitespace(source);
        return quote != null && normalizedSource.contains(normalizeWhitespace(quote));
    }
}
