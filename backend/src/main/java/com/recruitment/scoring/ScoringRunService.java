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
import com.recruitment.job.JobOwnerService;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.ApplicationStatus;
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
import com.recruitment.scoring.dto.BulkScoringRunResponse;
import com.recruitment.scoring.dto.ScoringRunResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// KHONG @Transactional o class nay: moi method o day chi DOC (nhieu repository khac nhau, khong
// can atomic voi nhau), roi giao cau GHI DUY NHAT cho ScoringRunStateService.create() (da
// @Transactional rieng cua no) - cung tinh than voi ResumeParsingOrchestrator khong @Transactional
// nhung goi sang ResumeParsingStateService cho phan ghi.
@Service
public class ScoringRunService {

    private static final BigDecimal FULL_WEIGHT = new BigDecimal("100");

    // FR-H04 mo rong R-C1 - so don toi da duoc THU tao luot trong mot request B1. Khai mot cho duy nhat.
    public static final int MAX_BULK_SCORING_RUNS = 50;

    // R-E6, L3: applied_at ASC, hoa thi id ASC so bang CHUOI - UUID.compareTo so hai long co dau nen lech thu tu uuid
    // cua Postgres; chuoi hex chu thuong thi khop.
    private static final Comparator<JobApplication> BULK_ORDER = Comparator.comparing(JobApplication::getAppliedAt)
            .thenComparing(application -> application.getId().toString());

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
    private final JobOwnerService jobOwnerService;

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
            ScoringRunStateService stateService,
            JobOwnerService jobOwnerService) {
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
        this.jobOwnerService = jobOwnerService;
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

    // FR-H04 mo rong B1 - POST /api/hr/jobs/{jobId}/scoring-runs/bulk. Chi doc + tao luot PENDING, KHONG goi AI
    // (R-T2); khong @Transactional (R-T1): moi don mot lan stateService.create, transaction ngan va commit rieng.
    // Thu tu kiem R-P5: so huu -> tin xoa mem -> rubric -> phan loai don -> tao.
    public BulkScoringRunResponse createScoringRunsForJob(UUID ownerId, UUID jobId) {
        Job job = jobOwnerService.loadOwned(jobId, ownerId);
        // R-P3 - loadOwned khong loc deleted_at; khuon endpoint ghi FR-H16 (BlindReviewService).
        if (job.getDeletedAt() != null) {
            throw new JobNotFoundException(jobId);
        }
        // R-R1 - cung ham, cung ma loi va cau voi nut don le; loi o day thi chua tao, chua khoa gi.
        Rubric rubric = requireRubricComplete(jobId);

        // R-E1 - chi don chu ky hien tai; don chu ky cu khong tao luot, khong vao skipped.
        List<JobApplication> considered = jobApplicationRepository.findByJobIdOrderByAppliedAtDesc(jobId).stream()
                .filter(application -> application.getRecruitmentCycle() == job.getRecruitmentCycle())
                .sorted(BULK_ORDER)
                .toList();
        if (considered.isEmpty()) {
            return new BulkScoringRunResponse(0, 0, List.of());
        }

        Map<UUID, BulkScoringSkipReason> skipReasonById = classifyIneligible(considered);
        List<JobApplication> eligible = considered.stream()
                .filter(application -> !skipReasonById.containsKey(application.getId()))
                .toList();
        // R-C1, R-C2 - chi thu 50 don dau; phan con lai chi dem vao remaining, khong bu cho khi mot don bi race.
        List<JobApplication> toTry = eligible.subList(0, Math.min(eligible.size(), MAX_BULK_SCORING_RUNS));
        int remaining = eligible.size() - toTry.size();

        int created = 0;
        if (!toTry.isEmpty()) {
            // R-R2, L2 - chup MOT lan, dung chung cho moi create; khong co don de thu thi khong doc rubric_criteria.
            List<RubricCriterion> criteria = rubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc(rubric.getId());
            RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(rubric, criteria);
            for (JobApplication application : toTry) {
                try {
                    stateService.create(application.getId(), snapshot, rubric.getId());
                    created++;
                } catch (DataIntegrityViolationException e) {
                    // R-T3 - chi vi pham uq_scoring_run_in_progress (race voi nut don le) moi thanh RUN_IN_PROGRESS;
                    // moi vi pham khac nem tiep (500). R-T4: cac luot da tao truoc do da commit, giu nguyen.
                    if (!isRunInProgressViolation(e)) {
                        throw e;
                    }
                    skipReasonById.put(application.getId(), BulkScoringSkipReason.RUN_IN_PROGRESS);
                }
            }
        }

        // Duyet lai tap xet de skipped giu thu tu applied_at ASC, ke ca don roi vao RUN_IN_PROGRESS do race.
        List<BulkScoringRunResponse.SkippedApplication> skipped = considered.stream()
                .filter(application -> skipReasonById.containsKey(application.getId()))
                .map(application -> new BulkScoringRunResponse.SkippedApplication(
                        application.getId(), skipReasonById.get(application.getId())))
                .toList();
        return new BulkScoringRunResponse(created, remaining, skipped);
    }

    // R-E2..R-E5 - ly do khong du dieu kien cua tung don, doc bang 4 cau theo lo (cung findByJobIdOrderByAppliedAtDesc
    // o tren la 5), khong N+1. Don khong co trong map tra ve la du dieu kien. Map tra ve sua duoc (race them vao sau).
    private Map<UUID, BulkScoringSkipReason> classifyIneligible(List<JobApplication> considered) {
        List<UUID> applicationIds = considered.stream().map(JobApplication::getId).toList();
        List<UUID> resumeIds = considered.stream().map(JobApplication::getResumeId).distinct().toList();

        Map<UUID, ParseStatus> parseStatusByResumeId = resumeRepository.findAllById(resumeIds).stream()
                .collect(Collectors.toMap(Resume::getId, Resume::getParseStatus));
        // L1 - chi can biet CV co dong resume_parsed_data (dieu kien thu hai cua requireResumeParsed); projection nay
        // khong nap raw_text/data.
        Set<UUID> resumeIdsWithParsedData = resumeParsedDataRepository.findPromptVersionsByResumeIds(resumeIds).stream()
                .map(ResumeParsedDataRepository.PromptVersionView::getResumeId)
                .collect(Collectors.toSet());
        // Projection native tra status dang String (xem LatestScoringRunView).
        Map<UUID, ScoringRunStatus> latestRunStatusByApplicationId =
                scoringRunRepository.findLatestByApplicationIdIn(applicationIds).stream()
                        .collect(Collectors.toMap(
                                LatestScoringRunView::getApplicationId, view -> ScoringRunStatus.valueOf(view.getStatus())));
        Set<UUID> scoredApplicationIds = scoringRunRepository.findLatestDoneByApplicationIdIn(applicationIds).stream()
                .map(LatestDoneScoringRunView::getApplicationId)
                .collect(Collectors.toSet());

        Map<UUID, BulkScoringSkipReason> skipReasonById = new HashMap<>();
        for (JobApplication application : considered) {
            BulkScoringSkipReason reason = skipReason(
                    application,
                    parseStatusByResumeId.get(application.getResumeId()),
                    resumeIdsWithParsedData.contains(application.getResumeId()),
                    latestRunStatusByApplicationId.get(application.getId()),
                    scoredApplicationIds.contains(application.getId()));
            if (reason != null) {
                skipReasonById.put(application.getId(), reason);
            }
        }
        return skipReasonById;
    }

    // R-E3 - mot don trung nhieu ly do lay ly do dung truoc theo thu tu nay (Q2). null = du dieu kien.
    private static BulkScoringSkipReason skipReason(
            JobApplication application,
            ParseStatus parseStatus,
            boolean hasParsedData,
            ScoringRunStatus latestRunStatus,
            boolean hasDoneRun) {
        if (application.getStatus() != ApplicationStatus.PENDING) {
            return BulkScoringSkipReason.NOT_PENDING;
        }
        if (parseStatus != ParseStatus.DONE || !hasParsedData) {
            return BulkScoringSkipReason.RESUME_NOT_PARSED;
        }
        // R-E4 - luot MOI NHAT PENDING/RUNNING bat ke finished_at: rong hon requireNoRunInProgress dung mot ca (RUNNING
        // cho D3) de khong tao luot thu hai cho don sap co diem. Nut don le giu dinh nghia cu (R-G4).
        if (latestRunStatus == ScoringRunStatus.PENDING || latestRunStatus == ScoringRunStatus.RUNNING) {
            return BulkScoringSkipReason.RUN_IN_PROGRESS;
        }
        // R-E2.4 - co bat ky luot DONE nao (ke ca khi luot moi nhat FAILED).
        if (hasDoneRun) {
            return BulkScoringSkipReason.ALREADY_SCORED;
        }
        return null;
    }

    // Cung cach doc ten index voi GlobalExceptionHandler.handleDataIntegrityViolation.
    private static boolean isRunInProgressViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("uq_scoring_run_in_progress");
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
