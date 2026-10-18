package com.recruitment.blindreview.dto;

// FR-H16 B1. Boolean (wrapper): enabled vang mat/null phan biet duoc voi false -> 400 INVALID_BLIND_REVIEW_REQUEST.
public record BlindReviewRequest(Boolean enabled) {
}
