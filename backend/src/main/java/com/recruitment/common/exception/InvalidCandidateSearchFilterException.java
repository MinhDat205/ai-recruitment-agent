package com.recruitment.common.exception;

// F3 (FR-H08) - bo loc GET /api/hr/candidates khong hop le: criterionName/minCriterionScore
// thieu mot trong hai, hoac minTotalScore > maxTotalScore.
public class InvalidCandidateSearchFilterException extends RuntimeException {

    public InvalidCandidateSearchFilterException(String message) {
        super(message);
    }
}
