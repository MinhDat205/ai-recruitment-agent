package com.recruitment.scoring;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.common.exception.ResumeNotFoundException;
import com.recruitment.common.exception.ResumeNotParsedException;
import com.recruitment.common.exception.RubricIncompleteException;
import com.recruitment.common.exception.RubricNotFoundException;
import com.recruitment.common.exception.ScoringRunInProgressException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterion;
import com.recruitment.rubric.RubricCriterionRepository;
import com.recruitment.rubric.RubricRepository;
import com.recruitment.scoring.dto.ScoringRunResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// KHONG @Transactional o class nay: moi method o day chi DOC (nhieu repository khac nhau, khong
// can atomic voi nhau), roi giao cau GHI DUY NHAT cho ScoringRunStateService.create() (da
// @Transactional rieng cua no) - cung tinh than voi ResumeParsingOrchestrator khong @Transactional
// nhung goi sang ResumeParsingStateService cho phan ghi.
@Service
public class ScoringRunService {

    private static final BigDecimal FULL_WEIGHT = new BigDecimal("100");

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final RubricRepository rubricRepository;
    private final RubricCriterionRepository rubricCriterionRepository;
    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;
    private final ScoringRunStateService stateService;

    public ScoringRunService(
            JobApplicationRepository jobApplicationRepository,
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            RubricRepository rubricRepository,
            RubricCriterionRepository rubricCriterionRepository,
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository,
            ScoringRunStateService stateService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.rubricRepository = rubricRepository;
        this.rubricCriterionRepository = rubricCriterionRepository;
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
        this.stateService = stateService;
    }

    public ScoringRunResponse createScoringRun(UUID ownerId, UUID applicationId) {
        JobApplication application = loadOwnedApplication(applicationId, ownerId);

        requireResumeParsed(application.getResumeId());
        Rubric rubric = requireRubricComplete(application.getJobId());
        requireNoRunInProgress(applicationId);

        // Lan DUY NHAT trong ca D2 doc rubric_criteria SONG - tu Dot 3 tro di, moi noi khac chi
        // doc lai rubric_snapshot da chup o day, khong bao gio query lai bang nay (yeu cau bat
        // buoc #7 cua D2).
        List<RubricCriterion> criteria = rubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc(rubric.getId());
        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(rubric, criteria);

        ScoringRun run = stateService.create(applicationId, snapshot, rubric.getId());
        // Luot vua tao chac chan chua co dong criterion_scores nao (0) - khong can query dem, khac
        // voi listScoringRuns() ben duoi phai dem that vi doc lai cac luot da co san, co the dang
        // do do hoac da cham xong mot phan.
        return toResponse(run, 0L);
    }

    // GET /api/hr/applications/{id}/scoring-runs (Dot 5) - lich su cac luot cham cua MOT don, moi
    // nhat truoc. Ownership kiem giong het POST (loadOwnedApplication).
    public List<ScoringRunResponse> listScoringRuns(UUID ownerId, UUID applicationId) {
        loadOwnedApplication(applicationId, ownerId);
        List<ScoringRun> runs = scoringRunRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId);
        if (runs.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> criteriaScoredByRunId = countScoredCriteriaByRun(runs.stream().map(ScoringRun::getId).toList());
        return runs.stream()
                .map(run -> toResponse(run, criteriaScoredByRunId.getOrDefault(run.getId(), 0L)))
                .toList();
    }

    // MOT query GROUP BY cho ca danh sach, KHONG dem tung luot rieng trong vong lap (N+1) - xem
    // ghi chu tai CriterionScoreRepository.countByScoringRunIdIn.
    private Map<UUID, Long> countScoredCriteriaByRun(List<UUID> scoringRunIds) {
        return criterionScoreRepository.countByScoringRunIdIn(scoringRunIds).stream()
                .collect(Collectors.toMap(ScoringRunCriteriaCountView::getScoringRunId, ScoringRunCriteriaCountView::getCount));
    }

    // Mau ownership giong het RubricOwnerService.loadOwnedRubric: 404 khi don khong ton tai, 403
    // (AccessDeniedException chuan cua Spring Security -> JsonAccessDeniedHandler) khi don ton tai
    // nhung job cua no khong thuoc cong ty cua HR dang dang nhap.
    private JobApplication loadOwnedApplication(UUID applicationId, UUID ownerId) {
        JobApplication application = jobApplicationRepository
                .findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        Job job = jobRepository
                .findById(application.getJobId())
                .orElseThrow(() -> new JobNotFoundException(application.getJobId()));
        Company company = requireOwnCompany(ownerId);
        if (!job.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap don ung tuyen nay");
        }
        return application;
    }

    private Company requireOwnCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    // Kiem CA HAI: parse_status = DONE VA co ban ghi resume_parsed_data - thieu ban ghi du
    // parse_status da DONE la tinh huong khong nen xay ra binh thuong, nhung neu xay ra (vd du
    // lieu hong) ma khong kiem o day, luot cham se duoc tao PENDING roi CHET o tang nen (Dot 3
    // can raw_text tu chinh bang nay) ma khong bao gio bao loi cho HR.
    private void requireResumeParsed(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElseThrow(() -> new ResumeNotFoundException(resumeId));
        if (resume.getParseStatus() != ParseStatus.DONE) {
            throw new ResumeNotParsedException();
        }
        if (resumeParsedDataRepository.findByResumeId(resumeId).isEmpty()) {
            throw new ResumeNotParsedException();
        }
    }

    // compareTo, KHONG equals - 100 va 100.00 la BANG NHAU ve gia tri nhung KHAC NHAU voi equals
    // cua BigDecimal (scale khac nhau). Rubric khong co tieu chi nao -> sumWeightByRubricId tra ve
    // 0 (COALESCE) -> compareTo(100) != 0 -> cung nem RubricIncompleteException, khong can kiem
    // rieng "danh sach rong".
    private Rubric requireRubricComplete(UUID jobId) {
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow(() -> new RubricNotFoundException(jobId));
        BigDecimal total = rubricCriterionRepository.sumWeightByRubricId(rubric.getId());
        if (total.compareTo(FULL_WEIGHT) != 0) {
            throw RubricIncompleteException.forScoring(total);
        }
        return rubric;
    }

    // Q1 (ke hoach D2): chi chan khi co lot PENDING hoac RUNNING ma finished_at con NULL - "dang
    // thuc su chay", khong phai "da tung chay". Mot lot RUNNING da co finished_at (cho D3) KHONG
    // chan lot moi (cau tra loi (i) cua Q1).
    private void requireNoRunInProgress(UUID applicationId) {
        boolean inProgress = scoringRunRepository.existsByApplicationIdAndStatusInAndFinishedAtIsNull(
                applicationId, List.of(ScoringRunStatus.PENDING, ScoringRunStatus.RUNNING));
        if (inProgress) {
            throw new ScoringRunInProgressException();
        }
    }

    private ScoringRunResponse toResponse(ScoringRun run, long criteriaScored) {
        return new ScoringRunResponse(
                run.getId(),
                run.getApplicationId(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getErrorMessage(),
                (int) criteriaScored,
                run.getRubricSnapshot().criteria().size());
    }
}
