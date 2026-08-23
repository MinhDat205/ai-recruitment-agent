package com.recruitment.jobapplication;

// Interface projection cho native query (JobApplicationRepository.countByStatusForCompany, F3
// FR-H08). getStatus() phai la String, KHONG phai ApplicationStatus - cung ly do voi
// ApplicationSummaryView/LatestScoringRunView: Spring Data khong tu convert String->enum cho
// projection cua native query (nem ConverterNotFoundException luc chay). Convert sang enum o
// tang DashboardService.
public interface StatusCountView {

    String getStatus();

    long getCount();
}
