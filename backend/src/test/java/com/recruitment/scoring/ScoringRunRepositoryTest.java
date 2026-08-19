package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.recruitment.rubric.ScaleLevelDescription;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// KHONG @Transactional o muc class - test kiem tra DataIntegrityViolationException (o cac file
// khac) can moi saveAndFlush() tu commit doc lap, khong bi mot transaction bao ngoai cuon vao
// rollback-only. Rieng hai test claim_* o duoi PHAI @Transactional o muc method: claimForProcessing
// la @Modifying nen doi hoi mot transaction dang mo de thuc thi (o Dot 4, ScoringRunStateService.
// claim() se la noi mo transaction do qua @Transactional cua chinh no - o Dot 1 chua co state
// service nen test tu mo transaction rieng cho hai test nay).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScoringRunRepositoryTest {

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

    @Autowired
    private ScoreExplanationRepository scoreExplanationRepository;

    @Autowired
    private ScoreExplanationAttemptRepository scoreExplanationAttemptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createJobWithRubric() {
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

    @Test
    void saveAndReload_roundTripsRubricSnapshotJsonbExactly() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);

        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Backend",
                List.of(new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(),
                        "Kinh nghiem Docker",
                        "Danh gia muc do thanh thao Docker",
                        new BigDecimal("40.00"),
                        5,
                        List.of(
                                new ScaleLevelDescription(1, "Chua co kinh nghiem"),
                                new ScaleLevelDescription(5, "Chuyen gia")))));

        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        run.setRubricSnapshot(snapshot);
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();

        ScoringRun reloaded = scoringRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ScoringRunStatus.PENDING);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getStartedAt()).isNull();
        assertThat(reloaded.getFinishedAt()).isNull();

        RubricSnapshot reloadedSnapshot = reloaded.getRubricSnapshot();
        assertThat(reloadedSnapshot.name()).isEqualTo("Rubric Backend");
        assertThat(reloadedSnapshot.criteria()).hasSize(1);
        RubricSnapshot.CriterionSnapshot criterionSnapshot = reloadedSnapshot.criteria().get(0);
        assertThat(criterionSnapshot.name()).isEqualTo("Kinh nghiem Docker");
        assertThat(criterionSnapshot.weight()).isEqualByComparingTo("40.00");
        assertThat(criterionSnapshot.maxScore()).isEqualTo(5);
        assertThat(criterionSnapshot.scaleDescription()).hasSize(2);
        assertThat(criterionSnapshot.scaleDescription().get(0).level()).isEqualTo(1);
        assertThat(criterionSnapshot.scaleDescription().get(0).description()).isEqualTo("Chua co kinh nghiem");
        assertThat(criterionSnapshot.scaleDescription().get(1).level()).isEqualTo(5);
    }

    @Test
    @Transactional
    void claim_pendingRun_transitionsToRunningAndSetsStartedAt_secondClaimReturnsZero() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        scoringRunRepository.saveAndFlush(run);

        int firstClaim = scoringRunRepository.claimForProcessing(run.getId());
        assertThat(firstClaim).isEqualTo(1);

        // Doc lai tu DB, khong tin object Java dang cam san trong tay.
        entityManager.clear();
        ScoringRun afterFirstClaim = scoringRunRepository.findById(run.getId()).orElseThrow();
        assertThat(afterFirstClaim.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(afterFirstClaim.getStartedAt()).isNotNull();

        int secondClaim = scoringRunRepository.claimForProcessing(run.getId());
        assertThat(secondClaim).isEqualTo(0);
    }

    @Test
    @Transactional
    void claim_runAlreadyRunning_returnsZero() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        scoringRunRepository.saveAndFlush(run);

        int claim = scoringRunRepository.claimForProcessing(run.getId());

        assertThat(claim).isEqualTo(0);
    }

    @Test
    void isSafeToUnlock_noCriterionScoresAndNoRunInProgress_returnsTrue() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun failedRun = new ScoringRun();
        failedRun.setApplicationId(applicationId);
        failedRun.setStatus(ScoringRunStatus.FAILED);
        failedRun.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(failedRun);

        boolean safe = scoringRunRepository.isSafeToUnlock(jobId);

        assertThat(safe).isTrue();
    }

    @Test
    void isSafeToUnlock_anotherRunOfSameJobStillInProgress_returnsFalse() {
        UUID jobId = createJobWithRubric();
        UUID applicationA = createApplicationFor(jobId);
        UUID applicationB = createApplicationFor(jobId);

        ScoringRun failedRun = new ScoringRun();
        failedRun.setApplicationId(applicationA);
        failedRun.setStatus(ScoringRunStatus.FAILED);
        failedRun.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(failedRun);

        // Luot nay dang thuc su chay (RUNNING, finished_at con NULL) - chua ghi criterion_scores
        // nao, nhung van phai chan mo khoa vi no co the ghi duoc bat cu luc nao.
        ScoringRun stillRunning = new ScoringRun();
        stillRunning.setApplicationId(applicationB);
        stillRunning.setStatus(ScoringRunStatus.RUNNING);
        stillRunning.setStartedAt(Instant.now());
        scoringRunRepository.saveAndFlush(stillRunning);

        boolean safe = scoringRunRepository.isSafeToUnlock(jobId);

        assertThat(safe).isFalse();
    }

    @Test
    void isSafeToUnlock_criterionScoresAlreadyExistFromAnotherRun_returnsFalse() {
        UUID jobId = createJobWithRubric();
        UUID applicationA = createApplicationFor(jobId);
        UUID applicationB = createApplicationFor(jobId);

        ScoringRun succeededRun = new ScoringRun();
        succeededRun.setApplicationId(applicationA);
        succeededRun.setStatus(ScoringRunStatus.RUNNING);
        succeededRun.setStartedAt(Instant.now());
        succeededRun.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(succeededRun);

        CriterionScore score = new CriterionScore();
        score.setScoringRunId(succeededRun.getId());
        score.setCriterionNameSnapshot("Kinh nghiem Java");
        score.setWeightSnapshot(new BigDecimal("100"));
        score.setMaxScoreSnapshot(5);
        score.setScore(new BigDecimal("4"));
        score.setReasoning("Ung vien co 3 nam kinh nghiem Java trong CV");
        score.setEvidence(List.of(new EvidenceEntry("3 nam kinh nghiem Java", "experience")));
        criterionScoreRepository.saveAndFlush(score);

        // Mot luot MOI, khac han, vua fail ma chua ghi duoc gi - nhung rubric van phai giu khoa vi
        // da co criterion_scores that tu luot truoc do.
        ScoringRun newFailedRun = new ScoringRun();
        newFailedRun.setApplicationId(applicationB);
        newFailedRun.setStatus(ScoringRunStatus.FAILED);
        newFailedRun.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(newFailedRun);

        boolean safe = scoringRunRepository.isSafeToUnlock(jobId);

        assertThat(safe).isFalse();
    }

    // Hai test duoi day chung minh CHOT CHAN LA DB (uq_scoring_run_in_progress, V4), khong phai
    // code Java - goi thang repository, bo qua ScoringRunService.requireNoRunInProgress hoan toan.

    @Test
    void saveAndFlush_secondActiveRunForSameApplication_violatesUniqueConstraint() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);

        ScoringRun first = new ScoringRun();
        first.setApplicationId(applicationId);
        first.setStatus(ScoringRunStatus.PENDING);
        scoringRunRepository.saveAndFlush(first);

        // Co y khac status (RUNNING thay vi PENDING) - index chi phan biet theo application_id
        // trong pham vi predicate, khong phan biet PENDING/RUNNING voi nhau.
        ScoringRun second = new ScoringRun();
        second.setApplicationId(applicationId);
        second.setStatus(ScoringRunStatus.RUNNING);
        second.setStartedAt(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> scoringRunRepository.saveAndFlush(second));
    }

    @Test
    void saveAndFlush_newRunAfterPreviousOneFinished_isNotBlockedByUniqueConstraint() {
        // Dung sau khi them uq_scoring_run_in_progress (V4): mot luot da co finished_at != NULL bi
        // loai khoi predicate cua index, nen KHONG chan luot moi cho cung don - giu dung cau tra
        // loi (i) cua Q1 (HR duoc cham lai don da cham xong) sau khi da them chot chan DB.
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);

        ScoringRun finished = new ScoringRun();
        finished.setApplicationId(applicationId);
        finished.setStatus(ScoringRunStatus.RUNNING);
        finished.setStartedAt(Instant.now());
        finished.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(finished);

        ScoringRun newRun = new ScoringRun();
        newRun.setApplicationId(applicationId);
        newRun.setStatus(ScoringRunStatus.PENDING);

        scoringRunRepository.saveAndFlush(newRun);

        assertThat(scoringRunRepository.findById(newRun.getId())).isPresent();
    }

    // Cac test duoi day thuoc Dot 2 cua ke hoach D3 (FR-H05): map total_score, query nhat luot,
    // finishAggregation.

    @Test
    void saveAndReload_totalScoreRoundTripsAtScaleThree() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalScore(new BigDecimal("66.667"));
        scoringRunRepository.saveAndFlush(run);
        entityManager.clear();

        ScoringRun reloaded = scoringRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getTotalScore()).isEqualByComparingTo("66.667");
        assertThat(reloaded.getTotalScore().scale()).isEqualTo(3);
    }

    // Nam tinh huong cua query nhat luot can tong hop (Q1, ke hoach D3): dung MOT dieu kien
    // status='RUNNING' AND finished_at IS NOT NULL AND total_score IS NULL - chi dung mot lot duy
    // nhat thoa CA BA dieu kien duoc tra ve.
    @Test
    void findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull_returnsOnlyEligibleRun() {
        UUID jobId = createJobWithRubric();

        ScoringRun eligible = new ScoringRun();
        eligible.setApplicationId(createApplicationFor(jobId));
        eligible.setStatus(ScoringRunStatus.RUNNING);
        eligible.setStartedAt(Instant.now());
        eligible.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(eligible);

        // FAILED, du finished_at khac NULL - khong bao gio duoc D3 dong toi.
        ScoringRun failed = new ScoringRun();
        failed.setApplicationId(createApplicationFor(jobId));
        failed.setStatus(ScoringRunStatus.FAILED);
        failed.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(failed);

        // PENDING - chua claim, khong lien quan D3.
        ScoringRun pending = new ScoringRun();
        pending.setApplicationId(createApplicationFor(jobId));
        pending.setStatus(ScoringRunStatus.PENDING);
        scoringRunRepository.saveAndFlush(pending);

        // RUNNING nhung finished_at CON NULL - D2 dang cham do, chua toi luot D3.
        ScoringRun stillScoring = new ScoringRun();
        stillScoring.setApplicationId(createApplicationFor(jobId));
        stillScoring.setStatus(ScoringRunStatus.RUNNING);
        stillScoring.setStartedAt(Instant.now());
        scoringRunRepository.saveAndFlush(stillScoring);

        // RUNNING + finished_at khac NULL nhung DA co total_score - da duoc D3 xu ly truoc do roi
        // (truong hop ly thuyet vi finishAggregation doi status sang DONE cung luc, nhung van kiem
        // dung dieu kien SQL doc lap voi hanh vi cua finishAggregation).
        ScoringRun alreadyAggregated = new ScoringRun();
        alreadyAggregated.setApplicationId(createApplicationFor(jobId));
        alreadyAggregated.setStatus(ScoringRunStatus.RUNNING);
        alreadyAggregated.setStartedAt(Instant.now());
        alreadyAggregated.setFinishedAt(Instant.now());
        alreadyAggregated.setTotalScore(new BigDecimal("50.000"));
        scoringRunRepository.saveAndFlush(alreadyAggregated);

        // Class nay khong @Transactional o muc class (xem comment dau file), nen scoring_runs do
        // cac test KHAC trong cung lop tao ra van con nguyen trong DB khi test nay chay tren CI -
        // loc lai chi con nam ban ghi tu test nay truoc khi containsExactly, de khong phu thuoc
        // trang thai DB do test khac de lai.
        Set<UUID> ownIds = Set.of(
                eligible.getId(),
                failed.getId(),
                pending.getId(),
                stillScoring.getId(),
                alreadyAggregated.getId());

        List<ScoringRun> pickedUp = scoringRunRepository.findByStatusAndFinishedAtIsNotNullAndTotalScoreIsNull(
                ScoringRunStatus.RUNNING, PageRequest.of(0, 100));
        List<ScoringRun> pickedUpFromThisTest =
                pickedUp.stream().filter(run -> ownIds.contains(run.getId())).toList();

        assertThat(pickedUpFromThisTest).extracting(ScoringRun::getId).containsExactly(eligible.getId());
    }

    @Test
    @Transactional
    void finishAggregation_eligibleRun_setsStatusDoneAndTotalScore_secondCallReturnsZero() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(run);

        int firstCall = scoringRunRepository.finishAggregation(run.getId(), new BigDecimal("66.667"));
        assertThat(firstCall).isEqualTo(1);

        entityManager.clear();
        ScoringRun afterFirstCall = scoringRunRepository.findById(run.getId()).orElseThrow();
        assertThat(afterFirstCall.getStatus()).isEqualTo(ScoringRunStatus.DONE);
        assertThat(afterFirstCall.getTotalScore()).isEqualByComparingTo("66.667");

        // Nhip poll thu hai (ly thuyet, xem ly do tai ScoringRunRepository.finishAggregation) -
        // WHERE total_score IS NULL khong con dung nua, UPDATE khong anh huong dong nao.
        int secondCall = scoringRunRepository.finishAggregation(run.getId(), new BigDecimal("66.667"));
        assertThat(secondCall).isEqualTo(0);
    }

    @Test
    @Transactional
    void finishAggregation_runFinishedAtStillNull_returnsZero() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun stillScoring = new ScoringRun();
        stillScoring.setApplicationId(applicationId);
        stillScoring.setStatus(ScoringRunStatus.RUNNING);
        stillScoring.setStartedAt(Instant.now());
        scoringRunRepository.saveAndFlush(stillScoring);

        int result = scoringRunRepository.finishAggregation(stillScoring.getId(), new BigDecimal("66.667"));

        assertThat(result).isEqualTo(0);
    }

    // Cac test duoi day thuoc Dot 3 cua ke hoach D4 (FR-H06): findRunsReadyForExplanation - dung
    // DUNG ba dieu kien da chot (status='DONE', chua co score_explanations, chua vuot nguong
    // attempt_count). Khong can index moi - da xac minh bang EXPLAIN that, xem comment tai
    // ScoringRunRepository.findRunsReadyForExplanation.

    private UUID createDoneRun(UUID applicationId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalScore(new BigDecimal("72.500"));
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    private void saveExplanationFor(UUID scoringRunId) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(scoringRunId);
        explanation.setSummary("Tom tat gia lap");
        explanation.setStrengths(List.of());
        explanation.setWeaknesses(List.of());
        explanation.setMetCriteria(List.of());
        explanation.setMissingCriteria(List.of());
        explanation.setModel("claude-sonnet-4-6");
        explanation.setPromptVersion("score-explanation-v1");
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    private void recordAttempts(UUID scoringRunId, int count) {
        for (int i = 0; i < count; i++) {
            scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi gia lap");
        }
    }

    // batchSize lon (khong phai 10) + contains() (khong phai containsExactly()) o hai test "duoc
    // nhat" duoi day: class nay CO Y KHONG @Transactional o muc class (de cac test kiem
    // DataIntegrityViolationException tu commit doc lap - xem comment dau file), nen cac dong DONE
    // khong explanation/attempts do CAC TEST KHAC trong CUNG file (vd
    // saveAndReload_totalScoreRoundTripsAtScaleThree, hoac chinh cac test findRunsReadyForExplanation
    // khac chay truoc) tao ra van con nguyen trong DB khi test nay chay - containsExactly() voi
    // batchSize nho se vo tinh doi ca nhung dong "la" do vao LIMIT truoc khi toi duoc dong cua chinh
    // test nay.
    @Test
    void findRunsReadyForExplanation_doneRunNoExplanationNoAttempts_isPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 1000);

        assertThat(ready).extracting(ScoringRun::getId).contains(runId);
    }

    @Test
    void findRunsReadyForExplanation_failedRun_isNotPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun failed = new ScoringRun();
        failed.setApplicationId(applicationId);
        failed.setStatus(ScoringRunStatus.FAILED);
        failed.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(failed);

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 10);

        assertThat(ready).extracting(ScoringRun::getId).doesNotContain(failed.getId());
    }

    @Test
    void findRunsReadyForExplanation_runningRunFinishedAtNull_isNotPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun stillScoring = new ScoringRun();
        stillScoring.setApplicationId(applicationId);
        stillScoring.setStatus(ScoringRunStatus.RUNNING);
        stillScoring.setStartedAt(Instant.now());
        scoringRunRepository.saveAndFlush(stillScoring);

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 10);

        assertThat(ready).extracting(ScoringRun::getId).doesNotContain(stillScoring.getId());
    }

    // RUNNING + finished_at KHAC NULL nghia la D2 da cham xong, dang cho D3 tong hop (CLAUDE.md muc
    // 2b) - CHUA phai DONE, D4 khong duoc dong toi cho toi khi D3 xong.
    @Test
    void findRunsReadyForExplanation_runningRunFinishedAtNotNull_isNotPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        ScoringRun waitingForD3 = new ScoringRun();
        waitingForD3.setApplicationId(applicationId);
        waitingForD3.setStatus(ScoringRunStatus.RUNNING);
        waitingForD3.setStartedAt(Instant.now());
        waitingForD3.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(waitingForD3);

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 10);

        assertThat(ready).extracting(ScoringRun::getId).doesNotContain(waitingForD3.getId());
    }

    @Test
    void findRunsReadyForExplanation_doneRunAlreadyHasExplanation_isNotPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        saveExplanationFor(runId);

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 10);

        assertThat(ready).extracting(ScoringRun::getId).doesNotContain(runId);
    }

    @Test
    @Transactional
    void findRunsReadyForExplanation_attemptCountOneBelowMax_isPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        recordAttempts(runId, 2); // maxAttempts=3, 2 < 3.

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 1000);

        assertThat(ready).extracting(ScoringRun::getId).contains(runId);
    }

    @Test
    @Transactional
    void findRunsReadyForExplanation_attemptCountAtMax_isNotPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        recordAttempts(runId, 3); // maxAttempts=3, 3 >= 3.

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 10);

        assertThat(ready).extracting(ScoringRun::getId).doesNotContain(runId);
    }

    @Test
    @Transactional
    void findRunsReadyForExplanation_attemptCountOneAboveMax_isNotPickedUp() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRun(applicationId);
        recordAttempts(runId, 4); // maxAttempts=3, 4 > 3.

        List<ScoringRun> ready = scoringRunRepository.findRunsReadyForExplanation(3, 10);

        assertThat(ready).extracting(ScoringRun::getId).doesNotContain(runId);
    }
}
