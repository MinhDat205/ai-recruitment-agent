package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.criterion.CriterionScoringErrorCode;
import com.recruitment.ai.criterion.CriterionScorePayload;
import com.recruitment.ai.criterion.CriterionScoringResult;
import com.recruitment.common.LlmRetryPolicy;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobOwnerService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.job.dto.JobOwnerResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// KHONG @Transactional o muc class hay method (tru khi ghi chu rieng): markFailed() phai TU commit
// transaction rieng cua no de test quan sat duoc state SAU KHI no da chay xong - giong ly do
// ResumeParsingStateServiceTest/ScoringRunRepositoryTest khong dung @Transactional bao ngoai.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScoringRunStateServiceTest {

    @Autowired
    private ScoringRunStateService stateService;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private CriterionScoreRepository criterionScoreRepository;

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
    private JobOwnerService jobOwnerService;

    @Autowired
    private LlmRetryPolicy retryPolicy;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID hrOwnerId;

    private UUID createJobWithLockedRubric() {
        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hr = userRepository.save(hr);
        hrOwnerId = hr.getId();

        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle("Backend Developer");
        job.setDescription("Mo ta cong viec");
        // FR-C05 R-J3: co ma danh muc de changeStatus -> OPEN chi con phu thuoc dieu kien rubric.
        job.setCategoryCode("IT_SOFTWARE");
        job.setLocationCode("HA_NOI");
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);

        Rubric rubric = new Rubric();
        rubric.setJobId(job.getId());
        rubric.setName("Rubric Backend");
        rubric.setLocked(true);
        rubricRepository.save(rubric);

        RubricCriterion criterion = new RubricCriterion();
        criterion.setRubricId(rubric.getId());
        criterion.setName("Kinh nghiem Java");
        criterion.setWeight(new BigDecimal("100"));
        criterion.setMaxScore(5);
        rubricCriterionRepository.saveAndFlush(criterion);

        return job.getId();
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

    private UUID createRun(UUID applicationId, ScoringRunStatus status) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(status);
        if (status != ScoringRunStatus.PENDING) {
            run.setStartedAt(Instant.now());
        }
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    @Test
    void markFailed_noCriterionScoresAndNoOtherRunInProgress_unlocksRubric() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);

        stateService.markFailed(runId, ScoringRunErrorCode.UNEXPECTED_ERROR);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getErrorMessage()).isEqualTo(ScoringRunErrorCode.UNEXPECTED_ERROR.formatted());

        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow();
        assertThat(rubric.isLocked()).isFalse();
    }

    @Test
    void markFailed_anotherRunOfSameJobStillRunning_keepsRubricLocked() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationA = createApplicationFor(jobId);
        UUID applicationB = createApplicationFor(jobId);
        UUID runToFail = createRun(applicationA, ScoringRunStatus.RUNNING);
        // Luot khac cung job dang thuc su chay (RUNNING, finished_at con NULL) - chua ghi
        // criterion_scores nao, nhung van phai chan mo khoa vi no co the ghi duoc bat cu luc nao.
        createRun(applicationB, ScoringRunStatus.RUNNING);

        stateService.markFailed(runToFail, ScoringRunErrorCode.UNEXPECTED_ERROR);

        entityManager.clear();
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow();
        assertThat(rubric.isLocked()).isTrue();
    }

    @Test
    void markFailed_criterionScoresAlreadyExistFromAnotherRun_keepsRubricLocked() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationA = createApplicationFor(jobId);
        UUID applicationB = createApplicationFor(jobId);
        UUID succeededRun = createRun(applicationA, ScoringRunStatus.RUNNING);

        CriterionScoringResult result = new CriterionScoringResult(
                new CriterionScorePayload(
                        4.0,
                        "Ung vien co 3 nam kinh nghiem Java",
                        List.of(new CriterionScorePayload.EvidenceQuote("3 nam kinh nghiem Java", "experience"))),
                "claude-sonnet-4-6",
                150,
                "criterion-score-v1");
        RubricCriterion criterion = rubricCriterionRepository
                .findByRubricIdOrderByDisplayOrderAsc(rubricRepository.findByJobId(jobId).orElseThrow().getId())
                .get(0);
        RubricSnapshot.CriterionSnapshot snapshot = new RubricSnapshot.CriterionSnapshot(
                criterion.getId(), criterion.getName(), null, criterion.getWeight(), criterion.getMaxScore(), null);
        stateService.recordCriterionScore(succeededRun, snapshot, result);
        stateService.markFinished(succeededRun);

        // Mot luot MOI, khac han, vua fail ma chua ghi duoc gi - nhung rubric van phai giu khoa vi
        // da co criterion_scores that tu luot truoc do.
        UUID newFailedRun = createRun(applicationB, ScoringRunStatus.RUNNING);
        stateService.markFailed(newFailedRun, ScoringRunErrorCode.UNEXPECTED_ERROR);

        entityManager.clear();
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow();
        assertThat(rubric.isLocked()).isTrue();
    }

    // Ly do ton tai cua yeu cau khoa rubric (Q6, ke hoach D2): mot job DA cham xong (rubric
    // is_locked=true) van phai mo lai duoc OPEN neu trong so van du 100% - JobOwnerService.
    // changeStatus da co san logic "bo qua kiem 100% khi rubric dang khoa" (xem
    // JobOwnerService.java dong 124-130), o day kiem THAT qua service that (khong mock) de chung
    // minh logic do khong bi D2 lam gay.
    @Test
    void jobOwnerService_changeStatusToOpen_rubricLockedButWeightComplete_reopensSuccessfully() {
        UUID jobId = createJobWithLockedRubric();
        Rubric rubric = rubricRepository.findByJobId(jobId).orElseThrow();
        assertThat(rubric.isLocked()).isTrue();

        JobOwnerResponse response = jobOwnerService.changeStatus(hrOwnerId, jobId, JobStatus.OPEN);

        assertThat(response.status()).isEqualTo(JobStatus.OPEN);
    }

    // criterion_id co FK ON DELETE SET NULL toi rubric_criteria nhung KHONG the INSERT tham chieu
    // toi mot id da khong con ton tai - phai existsById() truoc khi set FK (xem
    // ScoringRunStateService.recordCriterionScore). Xoa tieu chi TRUOC khi ghi diem, mo phong
    // truong hop HR xoa tieu chi giua luc chup snapshot va luc AI cham xong (du rubric da khoa
    // trong luong thuc te, day la lop phong thu tang du lieu, khong phu thuoc duong nguoi dung).
    @Test
    void recordCriterionScore_criterionDeletedBeforeScoring_setsFkNullButKeepsSnapshotFields() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        RubricCriterion criterion = rubricCriterionRepository
                .findByRubricIdOrderByDisplayOrderAsc(rubricRepository.findByJobId(jobId).orElseThrow().getId())
                .get(0);
        RubricSnapshot.CriterionSnapshot snapshot = new RubricSnapshot.CriterionSnapshot(
                criterion.getId(), criterion.getName(), null, criterion.getWeight(), criterion.getMaxScore(), null);

        rubricCriterionRepository.deleteById(criterion.getId());
        rubricCriterionRepository.flush();

        CriterionScoringResult result = new CriterionScoringResult(
                new CriterionScorePayload(
                        3.5,
                        "Ung vien co kinh nghiem Java o muc kha",
                        List.of(new CriterionScorePayload.EvidenceQuote("kinh nghiem Java", "experience"))),
                "claude-sonnet-4-6",
                120,
                "criterion-score-v1");
        stateService.recordCriterionScore(runId, snapshot, result);

        entityManager.clear();
        CriterionScore saved = criterionScoreRepository.findAll().stream()
                .filter(s -> s.getScoringRunId().equals(runId))
                .findFirst()
                .orElseThrow();
        assertThat(saved.getCriterionId()).isNull();
        assertThat(saved.getCriterionNameSnapshot()).isEqualTo("Kinh nghiem Java");
        assertThat(saved.getWeightSnapshot()).isEqualByComparingTo("100");
        assertThat(saved.getMaxScoreSnapshot()).isEqualTo(5);
        assertThat(saved.getScore()).isEqualByComparingTo("3.50");
    }

    private RubricSnapshot.CriterionSnapshot firstCriterionSnapshot(UUID jobId) {
        RubricCriterion criterion = rubricCriterionRepository
                .findByRubricIdOrderByDisplayOrderAsc(rubricRepository.findByJobId(jobId).orElseThrow().getId())
                .get(0);
        return new RubricSnapshot.CriterionSnapshot(
                criterion.getId(), criterion.getName(), null, criterion.getWeight(), criterion.getMaxScore(), null);
    }

    private CriterionScoringResult criterionResult(double score) {
        return new CriterionScoringResult(
                new CriterionScorePayload(
                        score,
                        "Ly do gia lap trong test",
                        List.of(new CriterionScorePayload.EvidenceQuote("kinh nghiem Java", "experience"))),
                "claude-sonnet-4-6",
                100,
                "criterion-score-v1");
    }

    private CriterionScore onlyScoreFor(UUID scoringRunId) {
        return criterionScoreRepository.findAll().stream()
                .filter(s -> s.getScoringRunId().equals(scoringRunId))
                .findFirst()
                .orElseThrow();
    }

    // Bien cua viec quy doi Double (CriterionScorePayload.score, tu LLM) sang BigDecimal
    // NUMERIC(5,2) (CriterionScore.score, entity) - xem ScoringRunStateService.toScoreScale:
    // BigDecimal.valueOf(double).setScale(2, RoundingMode.HALF_UP). 4.567 co 3 chu so thap phan,
    // vuot qua scale 2 cua cot - phai lam tron HALF_UP thanh 4.57, khong cat cut (truncate) thanh
    // 4.56.
    @Test
    void recordCriterionScore_scoreWithThreeDecimalDigits_roundsHalfUpToTwoDecimals() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);

        stateService.recordCriterionScore(runId, firstCriterionSnapshot(jobId), criterionResult(4.567));

        entityManager.clear();
        BigDecimal savedScore = onlyScoreFor(runId).getScore();
        assertThat(savedScore).isEqualByComparingTo("4.57");
        assertThat(savedScore.scale()).isEqualTo(2);
    }

    // score = maxScore, khong co phan thap phan (Double 5.0) - van phai duoc luu voi DUNG scale 2
    // cua cot (5.00), khong phai 5 hay 5.0 - BigDecimal.valueOf(5.0) tra ve scale 1 ("5.0"),
    // setScale(2, ...) la buoc BAT BUOC de khop dung NUMERIC(5,2), khong the bo qua chi vi gia tri
    // "tron".
    @Test
    void recordCriterionScore_scoreEqualsMaxScoreWithNoDecimals_storesWithScaleTwo() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);

        stateService.recordCriterionScore(runId, firstCriterionSnapshot(jobId), criterionResult(5.0));

        entityManager.clear();
        BigDecimal savedScore = onlyScoreFor(runId).getScore();
        assertThat(savedScore).isEqualByComparingTo("5.00");
        assertThat(savedScore.scale()).isEqualTo(2);
    }

    // Hai test duoi day bao ve sua guard finished_at trong markFailed (ke hoach D3, sua mot method
    // D2 da merge - CLAUDE.md yeu cau phai co test bao ve khi sua file cua nhanh da merge).

    // Mo phong call site D2 (ScoringRunOrchestrator.doProcess): tieu chi loi GIUA CHUNG khi luot
    // con dang cham (finished_at CON NULL). Guard "chi set khi dang null" phai cho ra hanh vi Y
    // HET truoc khi sua: van la lan dau tien finished_at duoc set.
    @Test
    void markFailed_finishedAtNullBeforeCall_setsFinishedAtNow() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        Instant before = Instant.now();

        stateService.markFailed(runId, ScoringRunErrorCode.UNEXPECTED_ERROR);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isNotNull().isAfterOrEqualTo(before);
    }

    // Mo phong call site MOI cua D3 (AggregationOrchestrator, Dot 3): luot DA CO finished_at do D2
    // set tu truoc (moc "D2 cham xong toan bo tieu chi", CLAUDE.md muc 2b) khi D3 phat hien loi
    // toan ven (CRITERIA_MISMATCH) TRUOC khi cong. Guard PHAI GIU NGUYEN moc cu - khong ghi de bang
    // thoi diem D3 phat hien loi, neu khong se mat dau vet that su "khi nao D2 cham xong" (sai
    // lech audit, phat hien luc lap ke hoach D3).
    @Test
    void markFailed_finishedAtAlreadySetBeforeCall_keepsOriginalTimestamp() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        stateService.markFinished(runId);
        entityManager.clear();
        Instant originalFinishedAt =
                scoringRunRepository.findById(runId).orElseThrow().getFinishedAt();
        assertThat(originalFinishedAt).isNotNull();

        stateService.markFailed(runId, ScoreAggregationErrorCode.CRITERIA_MISMATCH);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isEqualTo(originalFinishedAt);
        assertThat(run.getErrorMessage()).isEqualTo(ScoreAggregationErrorCode.CRITERIA_MISMATCH.formatted());
    }

    // Ghi ket qua tong hop (Dot 2, ke hoach D3): total_score va status=DONE phai cung xuat hien -
    // khong co trang thai trung gian nao ma mot cai da ghi con cai kia chua.
    @Test
    void finishAggregation_eligibleRun_setsStatusDoneAndTotalScoreTogether() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        stateService.markFinished(runId);

        boolean written = stateService.finishAggregation(runId, new BigDecimal("68.667"));

        assertThat(written).isTrue();
        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        assertThat(run.getTotalScore()).isEqualByComparingTo("68.667");
    }

    // Luot chua co finished_at (D2 con dang cham do) - D3 KHONG duoc phep ghi de, phai tra false.
    @Test
    void finishAggregation_runNotYetFinishedByD2_returnsFalseAndDoesNotWrite() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);

        boolean written = stateService.finishAggregation(runId, new BigDecimal("68.667"));

        assertThat(written).isFalse();
        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(run.getTotalScore()).isNull();
    }

    // Dot 4e (chore/hardening) - loi TAM THOI lan DAU (attempt_count 0 -> 1, max-attempts=3 trong
    // application-test.yml): VE PENDING (khong FAILED) de scheduler nhat lai, kem next_attempt_at
    // trong TUONG LAI. Bang chung duong re chay duoc that, khong chi map dung ma loi.
    @Test
    void markTemporaryFailure_firstFailure_returnsToPendingWithAttemptCountOneAndFutureNextAttempt() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        Instant before = Instant.now();

        stateService.markTemporaryFailure(runId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.PENDING);
        assertThat(run.getAttemptCount()).isEqualTo(1);
        assertThat(run.getErrorMessage()).isEqualTo(CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE.formatted());
        assertThat(run.getNextAttemptAt())
                .isAfter(before.plusMillis(retryPolicy.backoffMillisAfterAttempt(1) - 20));
        // Chua het luot thu - rubric van phai khoa, KHONG goi unlockRubricIfSafe.
        assertThat(rubricRepository.findByJobId(jobId).orElseThrow().isLocked()).isTrue();
    }

    // Bien duoi: lan thu THU HAI = max-attempts - 1 (2 trong 3) - van con mot lan thu nua.
    @Test
    void markTemporaryFailure_secondFailure_atMaxAttemptsMinusOne_stillPendingWithAttemptCountTwo() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        stateService.markTemporaryFailure(runId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        entityManager.clear();
        resetToRunning(runId);

        stateService.markTemporaryFailure(runId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.PENDING);
        assertThat(run.getAttemptCount()).isEqualTo(2);
        assertThat(run.getNextAttemptAt()).isNotNull();
    }

    // Bien tren: lan thu THU BA = dung max-attempts (3) - HET luot, FAILED han voi LLM_RETRY_EXHAUSTED,
    // next_attempt_at ve NULL, VA rubric duoc mo khoa (khong con luot nao khac dang chay/cho).
    @Test
    void markTemporaryFailure_thirdFailure_atMaxAttempts_marksFailedWithRetryExhaustedAndUnlocksRubric() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        stateService.markTemporaryFailure(runId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        entityManager.clear();
        resetToRunning(runId);
        stateService.markTemporaryFailure(runId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);
        entityManager.clear();
        resetToRunning(runId);

        stateService.markTemporaryFailure(runId, CriterionScoringErrorCode.LLM_TEMPORARILY_UNAVAILABLE);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.FAILED);
        assertThat(run.getAttemptCount()).isEqualTo(3);
        assertThat(run.getErrorMessage()).isEqualTo(ScoringRunErrorCode.LLM_RETRY_EXHAUSTED.formatted());
        assertThat(run.getNextAttemptAt()).isNull();
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(rubricRepository.findByJobId(jobId).orElseThrow().isLocked()).isFalse();
    }

    private void resetToRunning(UUID runId) {
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        run.setStatus(ScoringRunStatus.RUNNING);
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();
    }

    // Viec 3 (plan Dot 4, chore/hardening) - markFailed doi dieu kien status='RUNNING'. Mo phong mot
    // luot da bi doi khoi RUNNING boi mot luong khac (o day dat truc tiep ve PENDING, gia lap ket
    // qua cua stale-claim reaper Dot 4h reap+claim lai) GIUA luc mot worker "zombie" cua lan claim
    // CU van dang xu ly va cuoi cung goi markFailed. Rowcount phai la 0: KHONG duoc ghi de (status
    // van PENDING, khong thanh FAILED), va KHONG duoc goi unlockRubricIfSafe (rubric van khoa).
    @Test
    void markFailed_runNotInRunningStatus_doesNotOverwriteOrUnlockRubric() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.PENDING);

        stateService.markFailed(runId, ScoringRunErrorCode.UNEXPECTED_ERROR);

        entityManager.clear();
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ScoringRunStatus.PENDING);
        assertThat(run.getErrorMessage()).isNull();
        assertThat(run.getFinishedAt()).isNull();
        assertThat(rubricRepository.findByJobId(jobId).orElseThrow().isLocked()).isTrue();
    }

    // Dot 4f (chore/hardening) - claimForProcessing() them dieu kien next_attempt_at: mot luot PENDING
    // con dang cho backoff (moc TUONG LAI xa, +10 phut de tranh flaky do timing) KHONG duoc claim.
    // @Transactional o muc method - claimForProcessing() la @Modifying, doi hoi mot transaction
    // dang mo de thuc thi executeUpdate() (tien le ScoringRunRepositoryTest, Dot 1).
    @Test
    @Transactional
    void claimForProcessing_nextAttemptInFuture_doesNotClaimAndKeepsPending() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.PENDING);
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        run.setNextAttemptAt(Instant.now().plusSeconds(600));
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();

        int updated = scoringRunRepository.claimForProcessing(runId);

        assertThat(updated).isZero();
        entityManager.clear();
        assertThat(scoringRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo(ScoringRunStatus.PENDING);
    }

    @Test
    @Transactional
    void claimForProcessing_nextAttemptInPast_claimsSuccessfully() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.PENDING);
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        run.setNextAttemptAt(Instant.now().minusSeconds(60));
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();

        int updated = scoringRunRepository.claimForProcessing(runId);

        assertThat(updated).isEqualTo(1);
        entityManager.clear();
        assertThat(scoringRunRepository.findById(runId).orElseThrow().getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
    }

    // findReadyForProcessing la nguon quet cua ScoringRunScheduler (Dot 4f) - phai loai luot con
    // dang cho backoff ra khoi lo xu ly, cung ly do voi ResumeRepository.findReadyForProcessing.
    @Test
    void findReadyForProcessing_pendingWithFutureNextAttempt_excludedFromBatch() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationA = createApplicationFor(jobId);
        UUID applicationB = createApplicationFor(jobId);
        UUID readyId = createRun(applicationA, ScoringRunStatus.PENDING);
        UUID waitingId = createRun(applicationB, ScoringRunStatus.PENDING);
        ScoringRun waiting = scoringRunRepository.findById(waitingId).orElseThrow();
        waiting.setNextAttemptAt(Instant.now().plusSeconds(600));
        scoringRunRepository.saveAndFlush(waiting);
        entityManager.clear();

        List<ScoringRun> ready = scoringRunRepository.findReadyForProcessing(10);

        assertThat(ready).extracting(ScoringRun::getId).contains(readyId).doesNotContain(waitingId);
    }

    // Dot 4h (chore/hardening) - danh sach nguon cua stale-claim reaper. createRun(status khac
    // PENDING) da tu set startedAt=now() - phai ghi de lai startedAt cho tung kich ban.
    @Test
    void findStaleClaimIds_runningStartedBeforeThresholdAndNotFinished_includesIt() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        run.setStartedAt(Instant.now().minusSeconds(3600));
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();

        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(60));

        assertThat(staleIds).contains(runId);
    }

    @Test
    void findStaleClaimIds_startedAfterThreshold_excluded() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);

        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(600));

        assertThat(staleIds).doesNotContain(runId);
    }

    // Loai tru luot DA cham xong dang cho D3 tong hop (finished_at khac NULL) - day la diem khac
    // biet quan trong voi D1 (Resume khong co trang thai trung gian tuong duong).
    @Test
    void findStaleClaimIds_finishedAtAlreadySet_excludedEvenIfStartedAtOld() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        stateService.markFinished(runId);
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        run.setStartedAt(Instant.now().minusSeconds(3600));
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();

        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(60));

        assertThat(staleIds).doesNotContain(runId);
    }

    // Dot 4h - kich ban "worker goc cham xong toan bo tieu chi GIUA luc reaper doc va ghi": lay id
    // tu findStaleClaimIds() TRUOC (dai dien danh sach reaper da doc, tai thoi diem do finished_at
    // CON NULL), roi cho worker goc goi markFinished() that su (set finished_at), CUOI CUNG moi goi
    // markTemporaryFailure tren chinh id do (dai dien buoc ghi cua reaper). finished_at gio KHAC
    // NULL nen dieu kien UPDATE (them o Dot 4h) khong khop - rowcount 0, KHONG duoc keo luot DA
    // XONG nay quay ve PENDING (se lam mat dau vet cho D3 tong hop).
    @Test
    void markTemporaryFailure_calledWithStaleClaimIdAfterOriginalWorkerFinished_doesNotOverwriteFinishedRun() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        ScoringRun run = scoringRunRepository.findById(runId).orElseThrow();
        run.setStartedAt(Instant.now().minusSeconds(3600));
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();
        List<UUID> staleIds = stateService.findStaleClaimIds(Instant.now().minusSeconds(60));
        assertThat(staleIds).contains(runId);

        stateService.markFinished(runId);
        entityManager.clear();
        Instant finishedAtBefore =
                scoringRunRepository.findById(runId).orElseThrow().getFinishedAt();

        stateService.markTemporaryFailure(runId, ScoringRunErrorCode.STALE_CLAIM_TIMEOUT);

        entityManager.clear();
        ScoringRun reloaded = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(reloaded.getFinishedAt()).isEqualTo(finishedAtBefore);
        assertThat(reloaded.getAttemptCount()).isZero();
    }

    // Dot 4g (chore/hardening) - xac nhan uq_score_per_criterion (V1) THAT SU chan duoc ghi trung:
    // hai lan recordCriterionScore cho CUNG scoringRunId + CUNG criterionNameSnapshot (mo phong
    // worker thua race) - lan hai phai nem DataIntegrityViolationException ngay tai saveAndFlush
    // (Dot 4g doi tu save -> saveAndFlush), chi MOT dong ton tai trong criterion_scores.
    @Test
    void recordCriterionScore_duplicateCriterionForSameRun_violatesUniqueConstraintOnSecondCall() {
        UUID jobId = createJobWithLockedRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING);
        RubricSnapshot.CriterionSnapshot snapshot = firstCriterionSnapshot(jobId);
        stateService.recordCriterionScore(runId, snapshot, criterionResult(4.0));
        entityManager.clear();

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> stateService.recordCriterionScore(runId, snapshot, criterionResult(3.0)));

        entityManager.clear();
        List<CriterionScore> scores = criterionScoreRepository.findAll().stream()
                .filter(s -> s.getScoringRunId().equals(runId))
                .toList();
        assertThat(scores).hasSize(1);
        assertThat(scores.get(0).getScore()).isEqualByComparingTo("4.00");
    }
}
