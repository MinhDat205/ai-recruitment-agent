package com.recruitment.jobsearch.dto;

import java.util.List;

// FR-U13 S1 - response. KHONG them field nao khac (khong diem, khong do tuong dong, khong khoang cach).
public record JobSearchResponse(
        JobSearchInterpretation interpretation,
        JobSearchRanking ranking,                    // DESCRIPTION | NEWEST (R-K1)
        List<JobSearchItem> items) {                 // toi da 50, dung thu tu xep
}
