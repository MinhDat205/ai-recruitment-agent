package com.recruitment.ai.jobdraft;

// FR-H11 R-P1 - loai hinh trong schema AI phan JOB, dung 5 gia tri cua JobRequest.employmentType (@Pattern). Enum
// rieng cua ai/jobdraft (khong import job/): gia tri ngoai enum -> BeanOutputConverter khong parse duoc -> output
// hong (R-P3).
public enum DraftEmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERNSHIP,
    FREELANCE
}
