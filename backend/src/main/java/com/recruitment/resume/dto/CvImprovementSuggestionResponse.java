package com.recruitment.resume.dto;

import com.recruitment.resume.CvImprovementLearningPathItem;
import com.recruitment.resume.CvImprovementRequestStatus;
import com.recruitment.resume.CvImprovementSectionSuggestion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// KHONG errorMessage/requestId/model/promptVersion - da chot o Plan Mode muc F: errorMessage la ma
// loi noi bo, khong phai thu candidate can hanh dong duoc; requestId la khoa noi bo cua co che hang
// doi; model/promptVersion la du lieu audit (tien le ResumeParsedDataResponse da bo cung ly do).
public record CvImprovementSuggestionResponse(
        UUID resumeId,
        CvImprovementRequestStatus status,
        List<String> missingKeywords,
        List<CvImprovementSectionSuggestion> sectionSuggestions,
        List<CvImprovementLearningPathItem> learningPath,
        Instant generatedAt) {
}
