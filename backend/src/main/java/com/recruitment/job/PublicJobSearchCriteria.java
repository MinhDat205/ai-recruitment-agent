package com.recruitment.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// FR-U07 R-Q1: dieu kien "cung" dung chung cho U07 va (sau nay) U15 - gom R-N (categoryCode/
// locationCode, ma NULL van hien), R-S (khoang luong, quy doi sang VND), R-W (workMode), R-T
// (postedWithin, nguong da tinh san o Java). KHONG gom keyword/category/location (R-F1, chi U07
// dung) hay sort/phan trang (chi U07 co, U15 tu xep theo embedding) - de FR-U15 goi lai dung phan
// dieu kien cung ma khong phai mang theo tim kiem van ban cua U07.
record PublicJobSearchCriteria(
        String categoryCode,
        String locationCode,
        BigDecimal salaryMinVnd,
        BigDecimal salaryMaxVnd,
        boolean hideUnlisted,
        List<String> workModes,
        Instant sinceTimestamp) {

    PublicJobSearchCriteria {
        workModes = workModes == null ? List.of() : List.copyOf(workModes);
    }
}
