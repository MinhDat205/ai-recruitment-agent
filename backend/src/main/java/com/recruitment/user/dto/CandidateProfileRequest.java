package com.recruitment.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// FR-U14 dot 1 - 5 field dau giu nguyen khong them validate (ngoai pham vi, khong pha hanh vi
// FR-U01 cu). 4 field List KHONG @Size o day: so luong/dedupe kiem o service (R-F2/R-F3/R-K1/R-K2,
// dot 2) vi phai dedupe TRUOC khi dem, Bean Validation khong lam duoc buoc dedupe.
public record CandidateProfileRequest(
        String headline,
        String location,
        String currentTitle,
        BigDecimal yearsExperience,
        LocalDate dateOfBirth,
        List<String> desiredIndustryCodes,
        List<String> desiredLocationCodes,
        List<String> desiredWorkModes,
        List<String> skills,
        @Min(value = 0, message = "Lương mong muốn phải là số không âm.")
                @Max(value = 1000, message = "Lương mong muốn tối đa 1000 triệu.")
                Integer desiredSalaryMinMillions,
        @Size(max = 500, message = "Giới thiệu ngắn tối đa 500 ký tự.") String bio) {
}
