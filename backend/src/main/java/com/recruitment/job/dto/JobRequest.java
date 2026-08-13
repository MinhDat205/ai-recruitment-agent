package com.recruitment.job.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record JobRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        String requirements,
        @Size(max = 120) String category,
        @Size(max = 150) String location,
        @Pattern(regexp = "FULL_TIME|PART_TIME|CONTRACT|INTERNSHIP|FREELANCE") String employmentType,
        @Pattern(regexp = "ONSITE|HYBRID|REMOTE") String workMode,
        @DecimalMin(value = "0", inclusive = true) BigDecimal salaryMin,
        @DecimalMin(value = "0", inclusive = true) BigDecimal salaryMax,
        @Size(min = 3, max = 3) String salaryCurrency,
        LocalDate deadline) {
}
