package com.recruitment.resume;

import com.recruitment.ai.cvimprovement.CvImprovementErrorCode;
import com.recruitment.ai.cvimprovement.CvImprovementPayload;
import com.recruitment.ai.cvimprovement.CvImprovementResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho hang doi goi y cai thien CV - xem CLAUDE.md muc 3c va ResumeParsingStateService/
// ScoreExplanationStateService. KHONG method nao o day duoc goi qua self-invocation tu
// CvImprovementOrchestrator - phai qua bean nay de @Transactional di qua proxy Spring (bay da can o
// D1 va E1).
@Service
public class CvImprovementRequestStateService {

    private final CvImprovementRequestRepository cvImprovementRequestRepository;
    private final CvImprovementSuggestionRepository cvImprovementSuggestionRepository;

    public CvImprovementRequestStateService(
            CvImprovementRequestRepository cvImprovementRequestRepository,
            CvImprovementSuggestionRepository cvImprovementSuggestionRepository) {
        this.cvImprovementRequestRepository = cvImprovementRequestRepository;
        this.cvImprovementSuggestionRepository = cvImprovementSuggestionRepository;
    }

    @Transactional
    public boolean claim(UUID requestId) {
        return cvImprovementRequestRepository.claimForProcessing(requestId) == 1;
    }

    // Hai cau ghi (cv_improvement_requests + cv_improvement_suggestions) phai cung thanh cong hoac
    // cung rollback - mau ResumeParsingStateService.markDone. Dung save() thuong, KHONG saveAndFlush +
    // bat DataIntegrityViolationException nhu ScoreExplanationOrchestrator: cv_improvement_suggestions
    // (Dot 2, SUA 2) KHONG con UNIQUE(resume_id) - V1 co y cho phep nhieu ban ghi lich su moi resume -
    // nen KHONG con rang buoc nao o INSERT nay co the vi pham do hai vong poll dung nhau (claim() da
    // dam bao dung MOT orchestrator xu ly mot requestId). Bat mot exception khong bao gio duoc nem la
    // code chet, gay hieu lam ve mot race khong ton tai.
    @Transactional
    public void markDone(UUID requestId, UUID resumeId, CvImprovementResult result) {
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        request.setStatus(CvImprovementRequestStatus.DONE);
        request.setFinishedAt(Instant.now());
        cvImprovementRequestRepository.save(request);

        CvImprovementSuggestion suggestion = new CvImprovementSuggestion();
        suggestion.setResumeId(resumeId);
        suggestion.setMissingKeywords(result.payload().missingKeywords());
        suggestion.setSectionSuggestions(toSectionSuggestions(result.payload().sectionSuggestions()));
        suggestion.setLearningPath(toLearningPathItems(result.payload().learningPath()));
        suggestion.setModel(result.model());
        suggestion.setPromptVersion(result.promptVersion());
        cvImprovementSuggestionRepository.save(suggestion);
    }

    @Transactional
    public void markFailed(UUID requestId, CvImprovementErrorCode errorCode) {
        CvImprovementRequest request = cvImprovementRequestRepository.findById(requestId).orElseThrow();
        request.setStatus(CvImprovementRequestStatus.FAILED);
        request.setErrorMessage(errorCode.formatted());
        request.setFinishedAt(Instant.now());
        cvImprovementRequestRepository.save(request);
    }

    // Anh xa CvImprovementPayload.SectionSuggestion/LearningPathItem (ai.cvimprovement, doc lap DB)
    // -> CvImprovementSectionSuggestion/CvImprovementLearningPathItem (resume, JSONB cua
    // cv_improvement_suggestions) - cung tinh than voi ScoreExplanationStateService.toExplanationPoints.
    private static List<CvImprovementSectionSuggestion> toSectionSuggestions(
            List<CvImprovementPayload.SectionSuggestion> items) {
        return items.stream().map(i -> new CvImprovementSectionSuggestion(i.section(), i.suggestion())).toList();
    }

    private static List<CvImprovementLearningPathItem> toLearningPathItems(
            List<CvImprovementPayload.LearningPathItem> items) {
        return items.stream().map(i -> new CvImprovementLearningPathItem(i.topic(), i.reason())).toList();
    }
}
