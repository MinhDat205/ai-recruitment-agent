package com.recruitment.scoring;

import java.util.UUID;

// Interface projection cho query gom nhom (CriterionScoreRepository.countByScoringRunIdIn) - JPQL
// (khong phai native) nen Spring Data tu anh xa duoc kieu tra ve binh thuong, khong can String
// nhu LatestScoringRunView.
public interface ScoringRunCriteriaCountView {

    UUID getScoringRunId();

    long getCount();
}
