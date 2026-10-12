package com.recruitment.jobdraft;

// FR-H11 - ba phan goi y AI (R-N1) va cung la gia tri cot job_ai_generations.part (V15). Ten phai khop CHECK cua V15.
public enum JobDraftPart {
    JOB,
    CRITERIA,
    QUESTIONS;

    // R-K4-1 - muc dich cua token K4 cho tung phan; K4 khong biet gia tri nay (FR tu dat).
    public String tokenPurpose() {
        return "FR-H11/" + name();
    }
}
