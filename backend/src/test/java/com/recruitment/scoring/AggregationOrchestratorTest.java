package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterion;
import com.recruitment.rubric.RubricCriterionRepository;
import com.recruitment.rubric.RubricRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

// KHONG mock ChatModel, KHONG @Import(LlmTestConfiguration.class) - AggregationOrchestrator KHONG
// goi LLM o bat ky dau (rang buoc cung nhat cua D3), nen khong co gi de mock. Mau cau truc test
// giong ScoringRunOrchestratorTest nhung don gian hon vi khong can du lieu CV/resume_parsed_data.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class AggregationOrchestratorTest {

    @Autowired
    private AggregationOrchestrator orchestrator;

    @Autowired
    private ScoringRunStateService stateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private RubricCriterionRepository rubricCriterionRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createJobWithRubric(List<CriterionSpec> criteria) {
        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hr = userRepository.save(hr);

        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle("Backend Developer");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);

        Rubric rubric = new Rubric();
        rubric.setJobId(job.getId());
        rubric.setName("Rubric Backend");
        rubric.setLocked(true);
        rubricRepository.save(rubric);

        int order = 0;
        for (CriterionSpec spec : criteria) {
            RubricCriterion criterion = new RubricCriterion();
            criterion.setRubricId(rubric.getId());
            criterion.setName(spec.name());
            criterion.setWeight(spec.weight());
            criterion.setMaxScore(spec.maxScore());
            criterion.setDisplayOrder(order++);
            rubricCriterionRepository.saveAndFlush(criterion);
        }

        return job.getId();
    }

    private record CriterionSpec(String name, BigDecimal weight, int maxScore) {
    }

    private UUID createApplicationFor(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien Test");
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        return application.getId();
    }

    // finished_at DA duoc set (mo phong D2 da cham xong toan bo tieu chi) - dung
    // stateService.markFinished that thay vi tu gan tay, de khop dung con duong that D2 di qua.
    private UUID createRunningRunWithSnapshot(UUID applicationId, RubricSnapshot snapshot) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        UUID runId = scoringRunRepository.saveAndFlush(run).getId();
        stateService.markFinished(runId);
        return runId;
    }

    private static RubricSnapshot.CriterionSnapshot criterionSnapshot(String name, BigDecimal weight, int maxScore) {
        return new RubricSnapshot.CriterionSnapshot(UUID.randomUUID(), name, null, weight, maxScore, null);
    }

    private void insertCriterionScore(UUID runId, String name, BigDecimal weight, int maxScore, BigDecimal score) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(runId);
        criterionScore.setCriterionNameSnapshot(name);
        criterionScore.setWeightSnapshot(weight);
        criterionScore.setMaxScoreSnapshot(maxScore);
        criterionScore.setScore(score);
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    @Test
    void processOne_eligibleRun_computesHandVerifiedTotalAndKeepsOriginalFinishedAt() {
        UUID jobId = createJobWithRubric(List.of(
                new CriterionSpec("Kinh nghiem Java", new BigDecimal("60.00"), 5),
                new CriterionSpec("Kinh nghiem Docker", new BigDecimal("40.00"), 5)));
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Backend",
                List.of(
                        criterionSnapshot("Kinh nghiem Java", new BigDecimal("60.00"), 5),
                        criterionSnapshot("Kinh nghiem Docker", new BigDecimal("40.00"), 5)));
        UUID runId = createRunningRunWithSnapshot(applicationId, snapshot);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("60.00"), 5, new BigDecimal("4.00"));
        insertCriterionScore(runId, "Kinh nghiem Docker", new BigDecimal("40.00"), 5, new BigDecimal("3.00"));
        entityManager.clear();
        Instant finishedAtBeforeD3 =
                scoringRunRepository.findById(runId).orElseThrow().getFinishedAt();

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        // 4/5*60=48 ; 3/5*40=24 ; tong=72, weightSum=100 -> total=72.000
        assertThat(run.getTotalScore()).isEqualByComparingTo("72.000");
        assertThat(run.getFinishedAt()).isEqualTo(finishedAtBeforeD3);
    }

    @Test
    void processOne_oneCriterionScoreMissing_marksFailedCriteriaMismatchKeepsFinishedAt() {
        UUID jobId = createJobWithRubric(List.of(
                new CriterionSpec("Kinh nghiem Java", new BigDecimal("60.00"), 5),
                new CriterionSpec("Kinh nghiem Docker", new BigDecimal("40.00"), 5)));
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Backend",
                List.of(
                        criterionSnapshot("Kinh nghiem Java", new BigDecimal("60.00"), 5),
                        criterionSnapshot("Kinh nghiem Docker", new BigDecimal("40.00"), 5)));
        UUID runId = createRunningRunWithSnapshot(applicationId, snapshot);
        // CHI ghi 1 trong 2 tieu chi - mo phong loi toan ven ma D2 khong the tao ra duoc trong thuc
        // te (mot tieu chi loi la ca luot loi), nhung D3 van phai kiem phong thu.
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("60.00"), 5, new BigDecimal("4.00"));
        entityManager.clear();
        Instant finishedAtBeforeD3 =
                scoringRunRepository.findById(runId).orElseThrow().getFinishedAt();

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo(ScoreAggregationErrorCode.CRITERIA_MISMATCH.formatted());
        assertThat(run.getTotalScore()).isNull();
        assertThat(run.getFinishedAt()).isEqualTo(finishedAtBeforeD3);
    }

    @Test
    void processOne_weightSumZero_marksFailedInvalidWeightSum() {
        // KHONG dung createJobWithRubric voi weight=0 - rubric_criteria co CHECK (weight > 0), o
        // day chi can Job+Rubric de co FK hop le, khong can dong nao trong rubric_criteria vi
        // AggregationOrchestrator chi doc rubric_snapshot (JSONB tren scoring_runs, khong rang
        // buoc CHECK nao giong bang song), khong bao gio doc lai rubric_criteria.
        UUID jobId = createJobWithRubric(List.of());
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Backend", List.of(criterionSnapshot("Kinh nghiem Java", new BigDecimal("0.00"), 5)));
        UUID runId = createRunningRunWithSnapshot(applicationId, snapshot);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("0.00"), 5, new BigDecimal("4.00"));

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo(ScoreAggregationErrorCode.INVALID_WEIGHT_SUM.formatted());
        assertThat(run.getTotalScore()).isNull();
    }

    // Chung minh luoi an toan cuoi cung trong processOne() hoat dong that: rubric_snapshot = NULL
    // (cot DB cho phep NULL du app luon dien day du luc tao that - xem ScoringRun.java) khien
    // run.getRubricSnapshot().criteria() nem NullPointerException tu doProcess(). Neu khong duoc
    // bat lai, luot van con nam trong pham vi query nhat luot moi vong poll (khong claim nhu D2)
    // nen se bi thu lai vo han - markFailed dung vong lap do lai va de lai tin hieu ro rang.
    @Test
    void processOne_rubricSnapshotNull_marksFailedInsteadOfRetryingForever() {
        UUID jobId = createJobWithRubric(List.of(new CriterionSpec("Kinh nghiem Java", new BigDecimal("100"), 5)));
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRunningRunWithSnapshot(applicationId, null);

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo(ScoreAggregationErrorCode.UNEXPECTED_ERROR.formatted());
    }

    // Nhip poll "thu hai" tren CUNG mot luot (Q3, ke hoach D3): goi processOne() lan nua sau khi
    // lan dau da ghi xong - finishAggregation() lan hai khong con thoa dieu kien WHERE
    // (total_score IS NULL sai), khong ghi de, khong loi.
    @Test
    void processOne_calledTwiceInARow_secondCallDoesNotRewrite() {
        UUID jobId = createJobWithRubric(List.of(new CriterionSpec("Kinh nghiem Java", new BigDecimal("100.00"), 5)));
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Backend", List.of(criterionSnapshot("Kinh nghiem Java", new BigDecimal("100.00"), 5)));
        UUID runId = createRunningRunWithSnapshot(applicationId, snapshot);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("100.00"), 5, new BigDecimal("4.00"));

        orchestrator.processOne(runId);
        entityManager.clear();
        ScoringRun afterFirst = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        assertThat(afterFirst.getTotalScore()).isEqualByComparingTo("80.000");

        orchestrator.processOne(runId);

        entityManager.clear();
        ScoringRun afterSecond = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        assertThat(afterSecond.getTotalScore()).isEqualByComparingTo("80.000");
    }

    // Test nguyen tac (Q4/CLAUDE.md muc 2): doi trong so rubric SONG sau khi luot da DONE khong
    // duoc lam sai lich su da tinh - vua vi luot DONE khong con thoa dieu kien nhat cua poller, vua
    // vi (phong khi bi goi lai) aggregator doc weight_snapshot da chup, khong doc rubric_criteria
    // song.
    @Test
    void processOne_rubricWeightChangedAfterDone_totalScoreUnaffectedAndRunNoLongerPickedUp() {
        UUID jobId = createJobWithRubric(List.of(new CriterionSpec("Kinh nghiem Java", new BigDecimal("100"), 5)));
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Backend", List.of(criterionSnapshot("Kinh nghiem Java", new BigDecimal("100.00"), 5)));
        UUID runId = createRunningRunWithSnapshot(applicationId, snapshot);
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("100.00"), 5, new BigDecimal("4.00"));

        orchestrator.processOne(runId);
        entityManager.clear();
        BigDecimal originalTotal =
                scoringRunRepository.findById(runId).orElseThrow().getTotalScore();
        assertThat(originalTotal).isEqualByComparingTo("80.000");

        // Sua rubric SONG sau khi da DONE - di thang qua repository (mau
        // ScoringRunOrchestratorTest#processOne_editingRubricAfterScoring...).
        UUID rubricId = rubricRepository.findByJobId(jobId).orElseThrow().getId();
        RubricCriterion criterion =
                rubricCriterionRepository.findByRubricIdOrderByDisplayOrderAsc(rubricId).get(0);
        criterion.setWeight(new BigDecimal("55"));
        rubricCriterionRepository.saveAndFlush(criterion);
        entityManager.clear();

        // Poller khong con nhat duoc luot nay nua vi status da la DONE (khong con RUNNING).
        List<ScoringRun> pickedUp = scoringRunRepository.findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull(
                ScoringRunStatus.RUNNING, PageRequest.of(0, 10));
        assertThat(pickedUp).extracting(ScoringRun::getId).doesNotContain(runId);

        // Du co bi goi lai (gia dinh phong thu), aggregator van doc weight_snapshot da chup - ket
        // qua tinh lai VAN GIONG HET, khong bi keo theo trong so 55 vua sua.
        orchestrator.processOne(runId);
        entityManager.clear();
        BigDecimal totalAfterRubricChanged =
                scoringRunRepository.findById(runId).orElseThrow().getTotalScore();
        assertThat(totalAfterRubricChanged).isEqualByComparingTo(originalTotal);
    }
}
