package com.recruitment.job;

import com.recruitment.common.exception.InvalidJobFilterException;

// FR-U07 R-F5/R-O1/R-O2/R-O4: enum kin (khong suy tu chuoi tu do) de JobPublicService chon MOT
// trong HAI cau ORDER BY co dinh bang switch tren enum nay - tuyet doi khong noi chuoi tham so
// nguoi dung vao SQL (chong SQL injection qua tham so sort), khong dung Pageable.getSort().
enum JobSortOption {
    NEWEST,
    SALARY_DESC;

    // null/rong -> mac dinh NEWEST (R-O1). Gia tri la -> 400 INVALID_JOB_FILTER (R-F5).
    static JobSortOption fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        try {
            return JobSortOption.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw InvalidJobFilterException.invalidSort();
        }
    }
}
