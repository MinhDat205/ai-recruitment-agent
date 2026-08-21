package com.recruitment.jobapplication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Interface projection cho native query (JobApplicationRepository.searchCandidates/
// searchCandidatesByCriterion, F3 FR-H08). getStatus() phai la String, KHONG phai
// ApplicationStatus - cung ly do voi StatusCountView/ApplicationSummaryView: Spring Data khong tu
// convert String->enum cho projection cua native query (nem ConverterNotFoundException luc chay).
// Convert sang enum o tang ApplicationSearchService.
//
// totalScore la BigDecimal, co the null khi don chua co luot DONE nao (nhanh searchCandidates,
// LEFT JOIN LATERAL) - cung quy uoc voi ApplicationHrListItemResponse.totalScore cua D3/D4, KHONG
// suy dien gia tri thay the.
public interface CandidateSearchRow {

    UUID getId();

    UUID getJobId();

    String getJobTitle();

    UUID getCandidateId();

    UUID getResumeId();

    Instant getAppliedAt();

    String getStatus();

    UUID getLatestScoringRunId();

    BigDecimal getTotalScore();
}
