package com.recruitment.resume;

import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.common.exception.ResumeParsedDataNotFoundException;
import com.recruitment.resume.dto.CvImprovementSuggestionResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Quyen so huu dung DUNG pattern ResumeService.getParsedData: resumeRepository.findByIdAndCandidateId
// -> 404 (ResumeNotFoundException), KHONG 403, tranh lo resume cua nguoi khac co ton tai hay khong.
// TUYET DOI khong dung ApplicationOwnerService (do la phia HR). Tien kiem resume_parsed_data ton tai
// tai dung ResumeParsedDataNotFoundException da co san (404 RESUME_PARSED_DATA_NOT_FOUND), khong tao
// exception moi. KHONG inject ScoringRunRepository/CriterionScoreRepository/ScoreExplanationRepository
// - xem import list, khong co gi tu package scoring/.
@Service
public class CvImprovementSuggestionService {

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final CvImprovementSuggestionRepository cvImprovementSuggestionRepository;
    private final CvImprovementRequestRepository cvImprovementRequestRepository;

    public CvImprovementSuggestionService(
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            CvImprovementSuggestionRepository cvImprovementSuggestionRepository,
            CvImprovementRequestRepository cvImprovementRequestRepository) {
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.cvImprovementSuggestionRepository = cvImprovementSuggestionRepository;
        this.cvImprovementRequestRepository = cvImprovementRequestRepository;
    }

    // Thu tu idempotent DUNG NHU da chot o Plan Mode muc D: (1) da co suggestion -> tra ngay, KHONG
    // tao request, KHONG goi LLM; (2) da co request PENDING/RUNNING -> tra trang thai do, khong tao
    // them; (3) con lai -> tao request PENDING.
    @Transactional
    public CvImprovementSuggestionResponse requestSuggestions(UUID candidateId, UUID resumeId) {
        requireOwnedParsedResume(candidateId, resumeId);

        Optional<CvImprovementSuggestion> suggestion =
                cvImprovementSuggestionRepository.findFirstByResumeIdOrderByGeneratedAtDescIdDesc(resumeId);
        if (suggestion.isPresent()) {
            return toDoneResponse(resumeId, suggestion.get());
        }

        Optional<CvImprovementRequest> activeRequest = cvImprovementRequestRepository
                .findFirstByResumeIdAndStatusInOrderByRequestedAtDescIdDesc(
                        resumeId, List.of(CvImprovementRequestStatus.PENDING, CvImprovementRequestStatus.RUNNING));
        if (activeRequest.isPresent()) {
            return toStatusResponse(resumeId, activeRequest.get().getStatus());
        }

        CvImprovementRequest request = new CvImprovementRequest();
        request.setResumeId(resumeId);
        request.setStatus(CvImprovementRequestStatus.PENDING);
        try {
            cvImprovementRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // Race: mot request khac (candidate bam 2 lan gan nhau, hoac mo 2 tab) da tao truoc, chan
            // boi uq_cv_improvement_request_active (V6) - khong phai loi that, doc lai trang thai
            // hien hanh va tra ve thay vi de 500 noi len.
            return currentStatusResponse(resumeId);
        }
        return toStatusResponse(resumeId, CvImprovementRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public CvImprovementSuggestionResponse getSuggestions(UUID candidateId, UUID resumeId) {
        requireOwnedParsedResume(candidateId, resumeId);
        return currentStatusResponse(resumeId);
    }

    private void requireOwnedParsedResume(UUID candidateId, UUID resumeId) {
        resumeRepository
                .findByIdAndCandidateId(resumeId, candidateId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        resumeParsedDataRepository
                .findByResumeId(resumeId)
                .orElseThrow(() -> new ResumeParsedDataNotFoundException(resumeId));
    }

    private CvImprovementSuggestionResponse currentStatusResponse(UUID resumeId) {
        Optional<CvImprovementSuggestion> suggestion =
                cvImprovementSuggestionRepository.findFirstByResumeIdOrderByGeneratedAtDescIdDesc(resumeId);
        if (suggestion.isPresent()) {
            return toDoneResponse(resumeId, suggestion.get());
        }
        return cvImprovementRequestRepository
                .findFirstByResumeIdOrderByRequestedAtDescIdDesc(resumeId)
                .map(request -> toStatusResponse(resumeId, request.getStatus()))
                .orElseGet(() -> toStatusResponse(resumeId, CvImprovementRequestStatus.NOT_REQUESTED));
    }

    private static CvImprovementSuggestionResponse toDoneResponse(UUID resumeId, CvImprovementSuggestion suggestion) {
        return new CvImprovementSuggestionResponse(
                resumeId,
                CvImprovementRequestStatus.DONE,
                suggestion.getMissingKeywords(),
                suggestion.getSectionSuggestions(),
                suggestion.getLearningPath(),
                suggestion.getGeneratedAt());
    }

    private static CvImprovementSuggestionResponse toStatusResponse(UUID resumeId, CvImprovementRequestStatus status) {
        return new CvImprovementSuggestionResponse(resumeId, status, List.of(), List.of(), List.of(), null);
    }
}
