package com.recruitment.jobrecommendation;

import java.util.List;

// FR-U15 R-S - response moi cua GET /api/candidates/job-recommendations, thay han noi dung cu
// (List<JobSummaryResponse> doc tu bo dem). source = null CHI khi status = NO_DATA (R-S2). items
// rong khi status khac READY.
public record JobRecommendationResponse(
        JobRecommendationStatus status, JobRecommendationSource source, List<JobRecommendationItemResponse> items) {
}
