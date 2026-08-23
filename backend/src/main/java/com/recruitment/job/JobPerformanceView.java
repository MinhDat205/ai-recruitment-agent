package com.recruitment.job;

import java.math.BigDecimal;
import java.util.UUID;

// Interface projection cho native query (JobRepository.findJobPerformanceForCompany, F3 FR-H08).
// getStatus() phai la String, KHONG phai JobStatus - cung ly do voi StatusCountView/
// ApplicationSummaryView (native query khong tu convert String->enum cho projection). Convert
// sang enum o tang DashboardService.
//
// averageScore la BigDecimal (wrapper, co the null): AVG() tren tap rong (job chua co luot DONE
// nao) tra NULL trong Postgres - dung wrapper de phan biet "chua co diem nao" voi "diem trung
// binh bang 0" (cung quy uoc voi ScoringRun.totalScore, xem CLAUDE.md muc 4).
public interface JobPerformanceView {

    UUID getJobId();

    String getTitle();

    String getStatus();

    int getRecruitmentCycle();

    long getTotalApplications();

    long getScoredApplications();

    BigDecimal getAverageScore();

    long getEverInvitedCount();

    long getEverHiredCount();
}
