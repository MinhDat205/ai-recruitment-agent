package com.recruitment.user.dto;

import java.math.BigDecimal;
import java.util.List;

// FR-U14 R-A1 - ket qua "Dien tu CV", doc xac dinh tu resume_parsed_data cua CV chinh da DONE,
// khong goi AI. yearsExperience null khi resume_parsed_data.experience_months null (chua tinh duoc
// muc nao) - KHONG tu quy uoc 0.
public record ResumeAutofillResponse(String currentTitle, List<String> skills, BigDecimal yearsExperience) {
}
