package com.recruitment.cvbuilder.dto;

import com.recruitment.cvbuilder.CvBuilderForm;
import com.recruitment.cvbuilder.CvTemplate;

// FR-U12 B1, B3 - than request (R-B1). template: CLASSIC | MODERN; gia tri la -> body khong doc duoc (400
// INVALID_CV_BUILDER_REQUEST). template null -> 400 INVALID_CV_BUILDER_FORM (R-W1); form null -> 400 CV_BUILDER_EMPTY.
public record CvBuilderRequest(CvTemplate template, String versionLabel, CvBuilderForm form) {
}
