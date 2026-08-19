package com.recruitment.scoring;

import java.time.Instant;
import java.util.UUID;

// Interface projection cho native query (ScoringRunRepository.findLatestByApplicationIdIn) - mau
// ApplicationSummaryView (package jobapplication). getStatus() phai la String, KHONG phai
// ScoringRunStatus: Spring Data khong tu convert String->enum cho projection cua native query (nem
// ConverterNotFoundException luc chay). Convert sang enum o tang service goi query nay.
public interface LatestScoringRunView {

    UUID getApplicationId();

    UUID getId();

    String getStatus();

    Instant getFinishedAt();
}
