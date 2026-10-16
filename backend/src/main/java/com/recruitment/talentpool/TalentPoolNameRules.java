package com.recruitment.talentpool;

import java.util.Optional;

// FR-H15 R-K2 - chuan hoa ten kho: strip() hai dau roi kiem 1-100 ky tu, dem theo CODE POINT (khop VARCHAR(100) va
// char_length cua Postgres), khong theo String.length() (UTF-16: ky tu ngoai BMP dem thanh 2). Rong = ten khong hop le;
// service quyet dinh ma loi (INVALID_TALENT_POOL_NAME). Trung ten (R-K3) KHONG kiem o day.
public final class TalentPoolNameRules {

    public static final int MAX_LENGTH = 100;

    private TalentPoolNameRules() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String stripped = raw.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < 1 || length > MAX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(stripped);
    }
}
