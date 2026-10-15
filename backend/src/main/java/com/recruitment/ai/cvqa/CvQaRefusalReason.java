package com.recruitment.ai.cvqa;

// FR-C08 R-G3, R-O2 - ly do khong tra loi. Tap ly do hop le theo phia (S3): HR {PERSONAL_INFO, HIRING_JUDGEMENT,
// OUT_OF_SCOPE}; ung vien {HIRING_JUDGEMENT, CV_IMPROVEMENT, OUT_OF_SCOPE} - kiem o CvQaService.isValidPayload.
public enum CvQaRefusalReason {
    PERSONAL_INFO,
    HIRING_JUDGEMENT,
    CV_IMPROVEMENT,
    OUT_OF_SCOPE
}
