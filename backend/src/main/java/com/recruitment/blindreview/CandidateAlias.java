package com.recruitment.blindreview;

import java.util.Locale;
import java.util.UUID;

// FR-H16 R-N1 - nhan thay ten: "Ung vien #" + 8 ky tu hex cuoi cua id don (bo dau '-'), viet hoa. Ham thuan, tinh tai cho,
// khong luu. On dinh o moi man vi cung id don; id don da nam tren URL nen khong lo them gi [Q8].
public final class CandidateAlias {

    static final String PREFIX = "Ứng viên #";
    private static final int SUFFIX_LENGTH = 8;

    private CandidateAlias() {
    }

    public static String of(UUID applicationId) {
        String hex = applicationId.toString().replace("-", "");
        return PREFIX + hex.substring(hex.length() - SUFFIX_LENGTH).toUpperCase(Locale.ROOT);
    }
}
