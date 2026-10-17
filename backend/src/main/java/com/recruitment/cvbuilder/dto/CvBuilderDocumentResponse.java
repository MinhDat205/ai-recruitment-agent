package com.recruitment.cvbuilder.dto;

import com.recruitment.cvbuilder.CvBuilderForm;
import com.recruitment.cvbuilder.CvTemplate;
import java.time.Instant;
import java.util.UUID;

// FR-U12 B2 - response (R-B2). KHONG them field nao khac (T23): khong rawText, model, promptVersion, fileUrl...
public record CvBuilderDocumentResponse(UUID resumeId, CvTemplate template, String versionLabel, CvBuilderForm form,
        Instant createdAt) {
}
