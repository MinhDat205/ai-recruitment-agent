package com.recruitment.messaging;

import java.util.regex.Pattern;

// FR-C06 R-N2 - MOT ham duy nhat tao doan trich, dung cho ca hop thu (M5) va thong bao (dot 4). Gop moi day
// khoang trang (ke ca xuong dong) thanh mot dau cach, bo khoang trang hai dau, dai qua 120 thi lay 120 dau +
// "…". "Ky tu" o day la CODE POINT (khong cat doi emoji/cap surrogate) - khac R-M3 dem bang String.length().
public final class MessageExcerpt {

    static final int MAX_CODE_POINTS = 120;
    private static final String ELLIPSIS = "…";
    // UNICODE_CHARACTER_CLASS: \s gom ca khoang trang Unicode (vd U+00A0, U+2028), khong chi ASCII.
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private MessageExcerpt() {}

    // null khi tin khong co chu (null hoac chi khoang trang).
    public static String of(String body) {
        if (body == null) {
            return null;
        }
        String collapsed = WHITESPACE_RUN.matcher(body).replaceAll(" ").strip();
        if (collapsed.isEmpty()) {
            return null;
        }
        if (collapsed.codePointCount(0, collapsed.length()) <= MAX_CODE_POINTS) {
            return collapsed;
        }
        int end = collapsed.offsetByCodePoints(0, MAX_CODE_POINTS);
        return collapsed.substring(0, end) + ELLIPSIS;
    }
}
