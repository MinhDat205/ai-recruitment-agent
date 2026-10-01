package com.recruitment.catalog;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// Chuan hoa chuoi cho bo khop danh muc (FR-C05 R-M1, R-M2). Class Java THUAN, khong phu thuoc
// Spring (R-M5) - dung chung cho Java migration V9 (khong co Spring context) lan service luc chay.
// KHONG viet lai logic nay bang SQL (unaccent...) o bat ky dau: hai ban cai dat se lech nhau.
public final class CatalogTextNormalizer {

    // UNICODE_CHARACTER_CLASS de \s khop ca khoang trang Unicode (vd U+00A0 dan tu Word/PDF), khong
    // chi khoang trang ASCII.
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern LONG_DASHES = Pattern.compile("[–—]");
    private static final Pattern DASH_WITH_SPACES = Pattern.compile(" ?- ?");

    // Thu tu co y nghia: "tp. " phai thu truoc "tp." de "tp. hcm" khong con lai " hcm" (du trim cuoi
    // cung cung xu ly duoc, giu dung thu tu R-M2 cho de doi chieu dac ta).
    private static final List<String> PROVINCE_PREFIXES = List.of("thanh pho ", "tp. ", "tp.", "tp ", "tinh ");

    private CatalogTextNormalizer() {
    }

    // R-M1a: bo dau, d/D -> d, chu thuong, gop khoang trang, cat hai dau. Rong/null -> null.
    // R-E3/R-E4 (doc moc thoi gian) chi dung buoc nay, KHONG dung R-M1b - buoc gach noi bien
    // "01-2020" thanh "01 - 2020".
    public static String normalizeText(String input) {
        if (input == null) {
            return null;
        }
        // NFD tach chu co dau thanh chu goc + dau ket hop; "đ/Đ" KHONG tach duoc qua NFD (la ky tu
        // rieng, khong phai d + dau) nen phai thay tay.
        String text = Normalizer.normalize(input, Normalizer.Form.NFD);
        text = COMBINING_MARKS.matcher(text).replaceAll("");
        text = text.replace('đ', 'd').replace('Đ', 'd');
        text = text.toLowerCase(Locale.ROOT);
        text = WHITESPACE.matcher(text).replaceAll(" ").strip();
        return text.isEmpty() ? null : text;
    }

    // R-M1 day du = R-M1a roi R-M1b: gach dai -> "-", moi "-" (co/khong khoang trang hai ben) ->
    // dung " - ". Chay SAU R-M1a nen khoang trang da duoc gop con toi da mot dau cach moi ben.
    public static String normalize(String input) {
        String text = normalizeText(input);
        if (text == null) {
            return null;
        }
        text = LONG_DASHES.matcher(text).replaceAll("-");
        text = DASH_WITH_SPACES.matcher(text).replaceAll(" - ").strip();
        return text.isEmpty() ? null : text;
    }

    // R-M1 + R-M2 cho danh muc tinh/thanh: bo MOT tien to hanh chinh o dau neu co.
    public static String normalizeProvince(String input) {
        String text = normalize(input);
        if (text == null) {
            return null;
        }
        for (String prefix : PROVINCE_PREFIXES) {
            if (text.startsWith(prefix)) {
                text = text.substring(prefix.length()).strip();
                break;
            }
        }
        return text.isEmpty() ? null : text;
    }
}
