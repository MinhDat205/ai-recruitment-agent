package com.recruitment.ai.jobsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.jspecify.annotations.Nullable;

// ai/jobsearch/ - output tho cua LLM (FR-U13 muc 4.5). Schema co dinh cho BeanOutputConverter (qua AiOutputConverters).
// Kiem R-V o JobSearchParseValidator; chap nhan dieu kien (R-C) o jobsearch/. So dung wrapper (CLAUDE.md muc 4).
// List null -> List.of(); KHONG List.copyOf - phan tu null phai con nguyen de validator bat (R-V1).
// Annotation Jackson o package com.fasterxml la DUNG (CLAUDE.md muc 3b).
// L57 - BeanOutputConverter (Spring AI 2.0.0) danh dau MOI field la required tru field @Nullable: thieu @Nullable thi
// schema bat salary la object va bat CA HAI moc -> AI dien moc gia (vd max = 0) cho cau chi co mot moc -> R-C4 bo luong.
// @Nullable chi gan cho salary va hai moc; 4 danh sach giu required.
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobSearchParsePayload(
        List<CodeCondition> locations,
        List<CodeCondition> industries,
        @Nullable SalaryCondition salary,
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
    public record SalaryCondition(
            @Nullable Integer minMillionVnd, @Nullable Integer maxMillionVnd, String sourceText) {
    }
}
