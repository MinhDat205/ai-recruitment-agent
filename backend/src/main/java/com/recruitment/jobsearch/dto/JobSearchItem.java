package com.recruitment.jobsearch.dto;

import com.recruitment.job.dto.JobSummaryResponse;
import java.util.List;

// FR-U13 muc 4.5 - mot viec trong ket qua.
public record JobSearchItem(
        JobSummaryResponse job,                      // co san (FR-U07) - khong doi
        List<String> matchedConditions,              // R-M
        boolean rankedByDescription) {               // R-K4
}
