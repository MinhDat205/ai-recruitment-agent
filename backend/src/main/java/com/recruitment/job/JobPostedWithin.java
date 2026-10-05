package com.recruitment.job;

import com.recruitment.common.exception.InvalidJobFilterException;

// FR-U07 R-T2: nguong thoi gian tinh o TANG SERVICE (Java) bang Clock inject duoc, KHONG viet
// "NOW() - INTERVAL" trong SQL - de test dieu khien duoc thoi diem bang Clock.fixed thay vi phu
// thuoc dong ho that cua Postgres luc chay.
enum JobPostedWithin {
    LAST_24H(24),
    LAST_7D(7 * 24),
    LAST_30D(30 * 24);

    private final long hours;

    JobPostedWithin(long hours) {
        this.hours = hours;
    }

    long hours() {
        return hours;
    }

    // null/rong -> khong loc theo thoi gian (khong tra ve gia tri mac dinh nao, khac JobSortOption).
    // Gia tri la -> 400 INVALID_JOB_FILTER (R-F5).
    static JobPostedWithin fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JobPostedWithin.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw InvalidJobFilterException.invalidPostedWithin();
        }
    }
}
