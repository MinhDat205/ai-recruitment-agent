package com.recruitment.blindreview;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// FR-H16 R-M2, R-M3 - thay chuoi theo cac gia tri danh tinh DA BIET cua mot don. Khong dung AI, khong doan (R-A4).
// - Gia tri: strip(), NFC; bo qua null hoac it hon 3 code point.
// - Van ban dau vao cung NFC; so khop chuoi con khong phan biet hoa thuong (Unicode), gia tri duoc quote (khong la regex).
// - Gia tri dai thay truoc (email chua ho ten thi ca email thanh "[da an]", khong con mau email).
// Bat bien, dung chung duoc giua cac luong. Chi chay tren du lieu tra cho HR, khong ghi ket qua xuong DB (R-A3).
public final class IdentityMasker {

    public static final String REPLACEMENT = "[đã ẩn]";
    static final int MIN_CODE_POINTS = 3;

    private static final IdentityMasker NONE = new IdentityMasker(List.of());
    private static final String QUOTED_REPLACEMENT = Matcher.quoteReplacement(REPLACEMENT);

    private final List<Pattern> patterns;

    private IdentityMasker(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    public static IdentityMasker of(Collection<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
            if (normalized.codePointCount(0, normalized.length()) >= MIN_CODE_POINTS) {
                distinct.add(normalized);
            }
        }
        if (distinct.isEmpty()) {
            return NONE;
        }
        List<String> ordered = new ArrayList<>(distinct);
        ordered.sort(Comparator.comparingInt((String v) -> v.codePointCount(0, v.length())).reversed());
        List<Pattern> patterns = new ArrayList<>(ordered.size());
        for (String value : ordered) {
            patterns.add(Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        return new IdentityMasker(List.copyOf(patterns));
    }

    // Khong co gia tri nao du dieu kien (vd don khong co CV da trich xuat va tai khoan thieu thong tin): mask() tra
    // nguyen van.
    public static IdentityMasker none() {
        return NONE;
    }

    public String mask(String text) {
        if (text == null || patterns.isEmpty()) {
            return text;
        }
        String result = Normalizer.normalize(text, Normalizer.Form.NFC);
        for (Pattern pattern : patterns) {
            result = pattern.matcher(result).replaceAll(QUOTED_REPLACEMENT);
        }
        return result;
    }
}
