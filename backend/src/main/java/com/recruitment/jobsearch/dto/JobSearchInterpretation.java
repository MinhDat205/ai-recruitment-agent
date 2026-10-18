package com.recruitment.jobsearch.dto;

import java.util.List;

// FR-U13 muc 4.5 - cach he thong hieu cau. CHI ma danh muc: frontend tu doi ma sang chu bang danh muc C05 da tai.
public record JobSearchInterpretation(
        List<String> locationCodes,                  // <= 3 (R-C1)
        List<String> industryCodes,                  // <= 3, khong OTHER (R-C2)
        Integer salaryMin,                           // trieu VND, null = khong co (R-C4)
        Integer salaryMax,
        List<String> workModes,                      // ONSITE | HYBRID | REMOTE (R-C3)
        String description,                          // nguyen van con lai cua cau, null = khong co (R-D)
        boolean personalInfoIgnored) {               // R-D5
}
