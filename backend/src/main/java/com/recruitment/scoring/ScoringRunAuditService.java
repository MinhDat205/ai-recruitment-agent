package com.recruitment.scoring;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.scoring.dto.ScoringRunAuditItemResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// GET /api/hr/candidates/{applicationId}/audit/scoring-runs (F3, FR-H08). KHONG @Transactional:
// chi DOC, cung tinh than voi ApplicationSearchService/DashboardService.
@Service
public class ScoringRunAuditService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;
    private final ScoreExplanationRepository scoreExplanationRepository;

    public ScoringRunAuditService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository,
            ScoreExplanationRepository scoreExplanationRepository) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
        this.scoreExplanationRepository = scoreExplanationRepository;
    }

    public List<ScoringRunAuditItemResponse> listAudit(UUID ownerId, UUID applicationId) {
        loadOwnedApplication(applicationId, ownerId);

        // KHONG dua vao thu tu tra ve cua findByApplicationIdOrderByCreatedAtDesc - derived query
        // nay ORDER BY created_at DESC KHONG co khoa cuoi, da xac minh thuc nghiem tren Postgres
        // that: hai luot cham CUNG created_at (cung transaction) co the doi thu tu giua hai lan
        // doc du CUNG mot kieu ke hoach truy van, chi khac vi tri vat ly trong heap/index (xem bao
        // cao duyet Dot 4). KHONG sua repository nay o day - thuoc FR-H04 (D2), dang duoc
        // ScoringRunService.listScoringRuns dung va co 19 test khang dinh hinh dang hien tai; sua
        // se cham code ngoai pham vi FR-H08 (no ky thuat da ghi vao ROADMAP, xem Dot 7). Sort lai
        // bang Java o day: moi don chi co vai luot cham, khong ton kem gi. reversed() dao CA HAI
        // khoa (createdAt roi id) - "moi nhat truoc, id lon hon truoc", nhat quan voi
        // ORDER BY created_at DESC, id DESC da dung o Dot 2/3.
        List<ScoringRun> runs = scoringRunRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .sorted(Comparator.comparing(ScoringRun::getCreatedAt)
                        .thenComparing(ScoringRun::getId)
                        .reversed())
                .toList();
        if (runs.isEmpty()) {
            return List.of();
        }

        List<UUID> runIds = runs.stream().map(ScoringRun::getId).toList();

        Map<UUID, List<CriterionScore>> criterionScoresByRunId = criterionScoreRepository
                .findByScoringRunIdIn(runIds)
                .stream()
                .collect(Collectors.groupingBy(CriterionScore::getScoringRunId));

        Map<UUID, ScoreExplanation> explanationByRunId = scoreExplanationRepository
                .findByScoringRunIdIn(runIds)
                .stream()
                .collect(Collectors.toMap(ScoreExplanation::getScoringRunId, e -> e));

        return runs.stream()
                .map(run -> toResponse(
                        run,
                        criterionScoresByRunId.getOrDefault(run.getId(), List.of()),
                        explanationByRunId.get(run.getId())))
                .toList();
    }

    private ScoringRunAuditItemResponse toResponse(
            ScoringRun run, List<CriterionScore> criterionScores, ScoreExplanation explanation) {
        return new ScoringRunAuditItemResponse(
                run.getId(),
                run.getStatus(),
                run.getTotalScore(),
                run.getModel(),
                run.getPromptVersion(),
                run.getTokenUsage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt(),
                criterionScores.stream().map(ScoringRunAuditService::toCriterionScoreItem).toList(),
                explanation == null
                        ? null
                        : new ScoringRunAuditItemResponse.ExplanationMeta(
                                explanation.getModel(), explanation.getPromptVersion(), explanation.getGeneratedAt()));
    }

    // criterion_scores KHONG co cot model/prompt_version (chi scoring_runs va score_explanations
    // co) - CriterionScoreItem tai su dung tu D3/D4 khong co hai truong do, dung nguyen khong can
    // bien doi gi them.
    private static ApplicationHrListItemResponse.CriterionScoreItem toCriterionScoreItem(CriterionScore c) {
        return new ApplicationHrListItemResponse.CriterionScoreItem(
                c.getCriterionNameSnapshot(),
                c.getScore(),
                c.getMaxScoreSnapshot(),
                c.getWeightSnapshot(),
                c.getReasoning(),
                c.getEvidence());
    }

    // KHAC ApplicationStatusService.loadOwnedApplication/ApplicationOwnerService.loadOwnedJob (ca
    // hai tra cuu application/job TRUOC roi moi requireOwnCompany) - o day requireOwnCompany chay
    // TRUOC: neu HR chua co cong ty thi phai nhan 404 COMPANY_NOT_FOUND dung nguyen nhan, khong
    // phai 404 APPLICATION_NOT_FOUND/JOB_NOT_FOUND gay hieu nham, va tranh doc thua job_applications/
    // jobs khi da biet chac se loi. KHONG sua lai hai file kia cho dong bo - thuoc FR-H07 (E1,
    // ApplicationStatusService)/FR-H05 (D3, ApplicationOwnerService), ngoai pham vi FR-H08 (no ky
    // thuat da ghi vao ROADMAP, xem Dot 7).
    private JobApplication loadOwnedApplication(UUID applicationId, UUID ownerId) {
        Company company = requireOwnCompany(ownerId);
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Không có quyền xem lịch sử đánh giá của đơn ứng tuyển này");
        }
        return application;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }
}
