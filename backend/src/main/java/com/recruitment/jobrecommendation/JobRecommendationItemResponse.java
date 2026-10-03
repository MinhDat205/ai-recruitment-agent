package com.recruitment.jobrecommendation;

import com.recruitment.job.dto.JobSummaryResponse;
import java.util.List;

// FR-U15 R-M1 - matchedConditions do BACKEND tinh (R-M2), frontend khong tu so job voi mong muon.
// KHONG co field diem so nao (similarityScore/matchScore/rank...) - R-S3.
public record JobRecommendationItemResponse(JobSummaryResponse job, List<String> matchedConditions) {
}
