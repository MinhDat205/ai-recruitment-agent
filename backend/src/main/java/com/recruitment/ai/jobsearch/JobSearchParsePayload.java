package com.recruitment.ai.jobsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// ai/jobsearch/ - output tho cua LLM (FR-U13 muc 4.5). Schema co dinh cho BeanOutputConverter (constructor 1 tham so).
// Kiem R-V o JobSearchParseValidator; chap nhan dieu kien (R-C) o jobsearch/. So dung wrapper (CLAUDE.md muc 4).
// List null -> List.of(); KHONG List.copyOf - phan tu null phai con nguyen de validator bat (R-V1).
// Annotation Jackson o package com.fasterxml la DUNG (CLAUDE.md muc 3b).
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobSearchParsePayload(
        List<CodeCondition> locations,
        List<CodeCondition> industries,
        SalaryCondition salary,
        List<CodeCondition> workModes,
        List<String> personalInfoTexts) {

    public JobSearchParsePayload {
        locations = locations == null ? List.of() : locations;
        industries = industries == null ? List.of() : industries;
        workModes = workModes == null ? List.of() : workModes;
        personalInfoTexts = personalInfoTexts == null ? List.of() : personalInfoTexts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CodeCondition(String code, String sourceText) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalaryCondition(Integer minMillionVnd, Integer maxMillionVnd, String sourceText) {
    }
}
