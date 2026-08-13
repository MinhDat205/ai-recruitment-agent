package com.recruitment.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandidateProfileRequest(
        String headline,
        String location,
        String currentTitle,
        BigDecimal yearsExperience,
        LocalDate dateOfBirth) {
}
