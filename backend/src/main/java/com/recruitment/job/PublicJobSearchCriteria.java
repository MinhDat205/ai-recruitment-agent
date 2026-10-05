package com.recruitment.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// FR-U07 R-Q1: dieu kien "cung" dung chung cho U07 va FR-U15 - gom R-N (categoryCodes/
// locationCodes, ma NULL van hien), R-S (khoang luong, quy doi sang VND), R-W (workMode), R-T
// (postedWithin, nguong da tinh san o Java). KHONG gom keyword/category/location (R-F1, chi U07
// dung) hay sort/phan trang (chi U07 co, U15 tu xep theo embedding) - de FR-U15 goi lai dung phan
// dieu kien cung ma khong phai mang theo tim kiem van ban cua U07.
//
// FR-U15 R-H1: categoryCode/locationCode (String, 1 gia tri) -> categoryCodes/locationCodes
// (List<String>, toi da 3 sau dedupe - validate so luong o JobPublicService, record nay chi chuan
// hoa null -> rong, giong workModes da lam tu truoc).
record PublicJobSearchCriteria(
        List<String> categoryCodes,
        List<String> locationCodes,
        BigDecimal salaryMinVnd,
        BigDecimal salaryMaxVnd,
        boolean hideUnlisted,
        List<String> workModes,
        Instant sinceTimestamp) {

    PublicJobSearchCriteria {
        categoryCodes = categoryCodes == null ? List.of() : List.copyOf(categoryCodes);
        locationCodes = locationCodes == null ? List.of() : List.copyOf(locationCodes);
        workModes = workModes == null ? List.of() : List.copyOf(workModes);
    }
}
