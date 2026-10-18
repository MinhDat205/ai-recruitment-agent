package com.recruitment.jobapplication;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.CandidateAlias;
import com.recruitment.blindreview.IdentityMaskSource;
import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.jobapplication.HrApplicationAccess.OwnedApplication;
import com.recruitment.jobapplication.dto.ApplicationExplanationResponse;
import com.recruitment.jobapplication.dto.ApplicationHistoryEntryResponse;
import com.recruitment.jobapplication.dto.ApplicationHrDetailResponse;
import com.recruitment.jobapplication.dto.ApplicationScoresResponse;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H09 - doc du lieu trang ho so don phia HR (/hr/applications/:id). Chi DOC (readOnly), khong
// goi LLM/embedding, khong tao luot cham (REQUIREMENT muc 5). Quyen so huu kiem DUY NHAT qua
// HrApplicationAccess (R-Q3).
@Service
public class ApplicationHrDetailService {

    private final HrApplicationAccess hrApplicationAccess;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final ApplicationOwnerService applicationOwnerService;
    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public ApplicationHrDetailService(
            HrApplicationAccess hrApplicationAccess,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            ApplicationOwnerService applicationOwnerService,
            BlindReviewResolver blindReviewResolver,
            IdentityMaskSource identityMaskSource) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.applicationOwnerService = applicationOwnerService;
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
    }

    // E1 (R-D1). resumeId luon suy ra o server tu don (khong nhan tu client) - CV cua DUNG don nay,
    // khong phai CV chinh hien tai cua ung vien.
    @Transactional(readOnly = true)
    public ApplicationHrDetailResponse getDetail(UUID ownerId, UUID applicationId) {
        OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId);
        JobApplication application = owned.application();
        Resume resume = resumeRepository
                .findById(application.getResumeId())
                .orElseThrow(() -> new ResumeNotFoundException(application.getResumeId()));
        // FR-H16 R-N2, R-V2, R-V3 - don dang an danh: nhan thay ten; an toan bo thu gioi thieu va ten tep CV (khong thay
        // chuoi); khong doc ho ten tu users.
        boolean masked = blindReviewResolver.isMasked(applicationId);
        String candidateName = masked
                ? CandidateAlias.of(applicationId)
                : userRepository
                        .findById(application.getCandidateId())
                        .map(User::getFullName)
                        .orElse(null);
        return new ApplicationHrDetailResponse(
                application.getId(),
                owned.job().getId(),
                owned.job().getTitle(),
                candidateName,
                application.getStatus(),
                application.getAppliedAt(),
                masked ? null : application.getCoverLetter(),
                resume.getParseStatus(),
                resume.getParseError(),
                masked ? null : resume.getFileName(),
                masked);
    }

    // E3 (R-D3..R-D5) - diem cua lot DONE moi nhat + hang, tinh bang dung code FR-H05 cua danh sach
    // theo Job (Q1). Chi doc: khong tao lot cham, khong goi LLM (R-S5, REQUIREMENT muc 5).
    @Transactional(readOnly = true)
    public ApplicationScoresResponse getScores(UUID ownerId, UUID applicationId) {
        OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId);
        ApplicationEvaluation evaluation = applicationOwnerService.evaluateApplication(owned.job(), applicationId);
        // FR-H16 R-M4 - che sau khi tinh diem/hang (R-A2).
        IdentityMasker masker = maskerIfMasked(applicationId);
        return new ApplicationScoresResponse(
                evaluation.scoringRunId(),
                evaluation.scoredAt(),
                evaluation.totalScore(),
                evaluation.rank(),
                masker == null
                        ? evaluation.criterionScores()
                        : ScoreTextMasking.criterionScores(evaluation.criterionScores(), masker));
    }

    // E4 (R-D6) - giai thich cua CUNG lot DONE voi E3 (cung evaluateApplication -> cung scoringRunId va
    // scoredAt).
    @Transactional(readOnly = true)
    public ApplicationExplanationResponse getExplanation(UUID ownerId, UUID applicationId) {
        OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId);
        ApplicationEvaluation evaluation = applicationOwnerService.evaluateApplication(owned.job(), applicationId);
        IdentityMasker masker = maskerIfMasked(applicationId);
        return new ApplicationExplanationResponse(
                evaluation.scoringRunId(),
                evaluation.scoredAt(),
                evaluation.explanationStatus(),
                masker == null
                        ? evaluation.explanation()
                        : ScoreTextMasking.explanation(evaluation.explanation(), masker));
    }

    // FR-H16 - null khi don khong an danh (khong chay cau nap gia tri danh tinh nao).
    private IdentityMasker maskerIfMasked(UUID applicationId) {
        return blindReviewResolver.isMasked(applicationId) ? identityMaskSource.load(applicationId) : null;
    }

    // E5 (R-D7) - dung lai DTO + cach map cua lich su phia ung vien (khong co changed_by).
    @Transactional(readOnly = true)
    public List<ApplicationHistoryEntryResponse> getHistory(UUID ownerId, UUID applicationId) {
        hrApplicationAccess.loadOwned(ownerId, applicationId);
        return statusHistoryRepository.findByApplicationIdOrderByChangedAtAsc(applicationId).stream()
                .map(ApplicationService::toHistoryResponse)
                .toList();
    }
}
