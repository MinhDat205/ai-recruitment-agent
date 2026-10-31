package com.recruitment.reconsideration;

// FR-C09 R-V1, R-V2 - ly do (C1) va phan hoi (H2) xu ly GIONG noi dung tin nhan FR-C06 R-M2, R-M3
// (MessageService.normalizeLineBreaks - package-private o messaging/, nen chep rieng o day, Plan Mode L3).
final class ReconsiderationText {

    static final int MAX_LENGTH = 1000;

    private ReconsiderationText() {}

    // Chuan hoa DUY NHAT duoc phep: CRLF va CR -> LF. Khong trim, khong cat, khong loc ky tu, khong escape. null, thieu
    // hoac chi gom khoang trang (isBlank) -> null (luu NULL).
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        return text.isBlank() ? null : text;
    }

    // Do dai SAU chuan hoa, dem bang String.length() nhu C06 R-M3; 1000 hop le, 1001 vuot.
    static boolean tooLong(String normalized) {
        return normalized != null && normalized.length() > MAX_LENGTH;
    }
}
