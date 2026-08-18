package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.scoring.CriterionScore;
import com.recruitment.scoring.CriterionScoreRepository;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoreExplanation;
import com.recruitment.scoring.ScoreExplanationAttemptRepository;
import com.recruitment.scoring.ScoreExplanationRepository;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Goi thang applicationOwnerService.listApplications() (khong qua MockMvc/JSON) de assert truc
// tiep tren record da go kieu (BigDecimal, Integer rank...) - de va chinh xac hon parse chuoi JSON
// bang regex nhu ApplicationOwnerControllerIntegrationTest. Ownership (403/404) va shape JSON da co
// rieng o file do, KHONG lap lai o day - file nay tap trung vao logic xep hang (Q5, ke hoach D3)
// va nguon diem (lot DONE moi nhat).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ApplicationOwnerServiceTest {

    @Autowired
    private ApplicationOwnerService applicationOwnerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

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

    private UUID hrOwnerId;

    // KHONG tao Rubric/RubricCriterion that - scoring_runs/job_applications khong FK toi rubrics,
    // va cac test o day khong can mutate trong so song (khac AggregationOrchestratorTest, noi mot
    // test can mutate RubricCriterion that de kiem "sua rubric sau khi DONE khong lam sai diem").
    private UUID createJob() {
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
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);

        return job.getId();
    }

    private UUID createApplicationFor(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Ung Vien " + UUID.randomUUID());
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

    private static RubricSnapshot.CriterionSnapshot criterionSnapshot(String name, BigDecimal weight, int maxScore) {
        return new RubricSnapshot.CriterionSnapshot(UUID.randomUUID(), name, null, weight, maxScore, null);
    }

    // DONE voi rubric_snapshot RONG (khong tieu chi nao) - du cho cac test chi quan tam toi
    // totalScore/rank, khong quan tam criterionScores. totalScore GHI THANG (khong qua
    // ScoreAggregator) - phep cong da co ScoreAggregatorTest/AggregationOrchestratorTest rieng,
    // file nay chi kiem logic xep hang tren gia tri totalScore cho san.
    private UUID createDoneRunWithTotalScore(UUID applicationId, BigDecimal totalScore) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(new RubricSnapshot("Rubric Test", List.of()));
        run.setTotalScore(totalScore);
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    private UUID createDoneRun(UUID applicationId, RubricSnapshot snapshot, BigDecimal totalScore) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(totalScore);
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    private void insertCriterionScore(UUID runId, String name, BigDecimal weight, int maxScore, BigDecimal score) {
        insertCriterionScore(runId, name, weight, maxScore, score, List.of(new EvidenceEntry("doan trich gia lap", "experience")));
    }

    private void insertCriterionScore(
            UUID runId, String name, BigDecimal weight, int maxScore, BigDecimal score, List<EvidenceEntry> evidence) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(runId);
        criterionScore.setCriterionNameSnapshot(name);
        criterionScore.setWeightSnapshot(weight);
        criterionScore.setMaxScoreSnapshot(maxScore);
        criterionScore.setScore(score);
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(evidence);
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    // Ghi thang bao cao giai thich (FR-H06) xuong DB - khong chay lai pipeline D4/Dot 3 that (khong
    // LLM trong test), mau tinh than createDoneRunWithTotalScore o tren.
    private void createExplanation(UUID runId, String summary) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(runId);
        explanation.setSummary(summary);
        explanation.setStrengths(List.of(new ExplanationPoint("Kinh nghiem Java", "Diem manh gia lap")));
        explanation.setWeaknesses(List.of());
        explanation.setMetCriteria(List.of("Kinh nghiem Java"));
        explanation.setMissingCriteria(List.of());
        explanation.setModel("claude-sonnet-4-6");
        explanation.setPromptVersion("score-explanation-v1");
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    // Goi upsert nguyen tu THAT (khong ghi entity truc tiep) dung so lan can, de attempt_count phan
    // anh dung ngu nghia "da thu N lan" - mau cach ScoreExplanationOrchestrator (Dot 3) tu ghi.
    private void recordFailedAttempts(UUID runId, int times) {
        for (int i = 0; i < times; i++) {
            scoreExplanationAttemptRepository.recordFailedAttempt(runId, "LLM_ERROR: loi gia lap trong test");
        }
    }

    private void createPendingRun(UUID applicationId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        scoringRunRepository.saveAndFlush(run);
    }

    private void createFailedRun(UUID applicationId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.FAILED);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        scoringRunRepository.saveAndFlush(run);
    }

    @Test
    void listApplications_fourApplicationsDifferentScores_ranksSequentially() {
        UUID jobId = createJob();
        UUID app1 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app1, new BigDecimal("60.000"));
        UUID app2 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app2, new BigDecimal("90.000"));
        UUID app3 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app3, new BigDecimal("70.000"));
        UUID app4 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app4, new BigDecimal("80.000"));

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result).extracting(ApplicationHrListItemResponse::id).containsExactly(app2, app4, app3, app1);
        assertThat(result).extracting(ApplicationHrListItemResponse::rank).containsExactly(1, 2, 3, 4);
    }

    @Test
    void listApplications_twoApplicationsTiedScore_shareRankAndNextRankSkipsToFour() {
        UUID jobId = createJob();
        UUID app1 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app1, new BigDecimal("90.000"));
        UUID app2 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app2, new BigDecimal("80.000"));
        UUID app3 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app3, new BigDecimal("80.000"));
        UUID app4 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app4, new BigDecimal("70.000"));

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        // Kieu 1-2-2-4, KHONG PHAI 1-2-2-3: don thu tu (70) o VI TRI thu 4 nen nhan hang 4.
        assertThat(result).extracting(ApplicationHrListItemResponse::id).containsExactly(app1, app2, app3, app4);
        assertThat(result).extracting(ApplicationHrListItemResponse::rank).containsExactly(1, 2, 2, 4);
    }

    @Test
    void listApplications_unscoredAndFailedApplications_haveNullRankAndSortAfterRankedOnes() {
        UUID jobId = createJob();
        UUID scoredApp = createApplicationFor(jobId);
        createDoneRunWithTotalScore(scoredApp, new BigDecimal("50.000"));
        UUID pendingApp = createApplicationFor(jobId);
        createPendingRun(pendingApp);
        UUID failedApp = createApplicationFor(jobId);
        createFailedRun(failedApp);

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(scoredApp);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(1).totalScore()).isNull();
        assertThat(result.get(1).rank()).isNull();
        assertThat(result.get(2).totalScore()).isNull();
        assertThat(result.get(2).rank()).isNull();
    }

    // Nguon diem = lot DONE MOI NHAT (Q5) - mot lot FAILED xay ra SAU lot DONE (HR cham lai nhung
    // lan nay loi) khong duoc xoa mat diem cu: don van con diem tu lot DONE, chi rieng
    // latestScoringRunStatus (tien do) phan anh dung lot MOI NHAT la FAILED.
    @Test
    void listApplications_doneRunOlderThanFailedRun_usesDoneRunScoreButShowsFailedAsLatestStatus() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        createDoneRunWithTotalScore(applicationId, new BigDecimal("65.000"));
        createFailedRun(applicationId);

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalScore()).isEqualByComparingTo("65.000");
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).latestScoringRunStatus()).isEqualTo(ScoringRunStatus.FAILED);
    }

    @Test
    void listApplications_applicationHasTwoDoneRuns_usesNewerDoneRunScore() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        createDoneRunWithTotalScore(applicationId, new BigDecimal("40.000"));
        createDoneRunWithTotalScore(applicationId, new BigDecimal("95.000"));

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalScore()).isEqualByComparingTo("95.000");
    }

    // Goi listApplications() HAI LAN tren CUNG du lieu co hoa diem - thu tu dong phai giong het
    // nhau (khoa phu appliedAt on dinh thu tu, khong phu thuoc thu tu bam bam/random cua Set/Map
    // trung gian trong qua trinh xu ly).
    @Test
    void listApplications_calledTwiceWithTiedScores_returnsIdenticalOrderBothTimes() {
        UUID jobId = createJob();
        UUID app1 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app1, new BigDecimal("80.000"));
        UUID app2 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app2, new BigDecimal("80.000"));
        UUID app3 = createApplicationFor(jobId);
        createDoneRunWithTotalScore(app3, new BigDecimal("80.000"));

        List<UUID> firstCallOrder = applicationOwnerService
                .listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC)
                .stream()
                .map(ApplicationHrListItemResponse::id)
                .toList();
        List<UUID> secondCallOrder = applicationOwnerService
                .listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC)
                .stream()
                .map(ApplicationHrListItemResponse::id)
                .toList();

        assertThat(secondCallOrder).containsExactlyElementsOf(firstCallOrder);
    }

    // Diem 3 (Dot 4): thu tu tieu chi hien thi theo THU TU trong rubric_snapshot cua chinh lot DONE
    // (Docker truoc Java), KHONG phai thu tu da INSERT criterion_scores (Java truoc Docker) va cang
    // khong phai thu tu alphabet (Docker < Java neu sap chu cai thi trung ngau nhien voi ket qua
    // mong doi - vi vay insert co y NGUOC thu tu ca hai de tach bach that su).
    @Test
    void listApplications_criterionScoresOrderedByRubricSnapshotOrder_notInsertionOrAlphabetical() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Test",
                List.of(
                        criterionSnapshot("Kinh nghiem Docker", new BigDecimal("40.00"), 5),
                        criterionSnapshot("Kinh nghiem Java", new BigDecimal("60.00"), 5)));
        UUID runId = createDoneRun(applicationId, snapshot, new BigDecimal("70.000"));
        insertCriterionScore(runId, "Kinh nghiem Java", new BigDecimal("60.00"), 5, new BigDecimal("4.00"));
        insertCriterionScore(runId, "Kinh nghiem Docker", new BigDecimal("40.00"), 5, new BigDecimal("3.00"));

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result.get(0).criterionScores())
                .extracting(ApplicationHrListItemResponse.CriterionScoreItem::criterionNameSnapshot)
                .containsExactly("Kinh nghiem Docker", "Kinh nghiem Java");
        assertThat(result.get(0).criterionScores())
                .extracting(ApplicationHrListItemResponse.CriterionScoreItem::reasoning)
                .containsExactly("Ly do gia lap trong test", "Ly do gia lap trong test");
    }

    // sort=APPLIED_AT_DESC: thu tu hien thi giu nguyen thu tu nop don (KHONG sap lai theo diem),
    // du totalScore/rank van tra day du cho tung dong.
    @Test
    void listApplications_appliedAtSort_keepsApplicationOrderRegardlessOfScore() {
        UUID jobId = createJob();
        UUID earlierLowScore = createApplicationFor(jobId);
        createDoneRunWithTotalScore(earlierLowScore, new BigDecimal("20.000"));
        UUID laterHighScore = createApplicationFor(jobId);
        createDoneRunWithTotalScore(laterHighScore, new BigDecimal("90.000"));

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.APPLIED_AT_DESC);

        // findByJobIdOrderByAppliedAtDesc: don nop SAU (laterHighScore) dung TRUOC trong danh sach.
        assertThat(result).extracting(ApplicationHrListItemResponse::id).containsExactly(laterHighScore, earlierLowScore);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(1).rank()).isEqualTo(2);
    }

    // Diem 3 (Dot 4, FR-H06): evidence cua MOI tieu chi phai dan dung tieu chi do, khong lan sang
    // tieu chi khac - va tieu chi diem 0 phai co evidence RONG (mang rong, khong phai null), dung
    // luat D2 "evidence rong <=> score = 0".
    @Test
    void listApplications_multipleCriteria_evidenceNotMixedBetweenCriteriaAndEmptyForZeroScore() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        RubricSnapshot snapshot = new RubricSnapshot(
                "Rubric Test",
                List.of(
                        criterionSnapshot("Kinh nghiem Docker", new BigDecimal("40.00"), 5),
                        criterionSnapshot("Kinh nghiem Java", new BigDecimal("60.00"), 5)));
        UUID runId = createDoneRun(applicationId, snapshot, new BigDecimal("70.000"));
        insertCriterionScore(
                runId,
                "Kinh nghiem Java",
                new BigDecimal("60.00"),
                5,
                new BigDecimal("4.00"),
                List.of(new EvidenceEntry("3 nam kinh nghiem Java", "experience")));
        insertCriterionScore(runId, "Kinh nghiem Docker", new BigDecimal("40.00"), 5, new BigDecimal("0.00"), List.of());

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        List<ApplicationHrListItemResponse.CriterionScoreItem> items = result.get(0).criterionScores();
        ApplicationHrListItemResponse.CriterionScoreItem docker = items.stream()
                .filter(i -> i.criterionNameSnapshot().equals("Kinh nghiem Docker"))
                .findFirst()
                .orElseThrow();
        ApplicationHrListItemResponse.CriterionScoreItem java = items.stream()
                .filter(i -> i.criterionNameSnapshot().equals("Kinh nghiem Java"))
                .findFirst()
                .orElseThrow();

        assertThat(docker.evidence()).isEmpty();
        assertThat(java.evidence()).extracting(EvidenceEntry::quote).containsExactly("3 nam kinh nghiem Java");
    }

    // Test quan trong nhat cua dot (theo yeu cau): don co HAI lot DONE, ca totalScore LAN explanation
    // phai cung lay tu lot MOI HON - khong duoc ghep totalScore cua lot nay voi explanation cua lot
    // khac (Diem 2, Dot 4).
    @Test
    void listApplications_applicationHasTwoDoneRunsWithExplanations_explanationComesFromNewerDoneRun() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        UUID olderRunId = createDoneRunWithTotalScore(applicationId, new BigDecimal("40.000"));
        createExplanation(olderRunId, "Bao cao cua lot cu");
        UUID newerRunId = createDoneRunWithTotalScore(applicationId, new BigDecimal("95.000"));
        createExplanation(newerRunId, "Bao cao cua lot moi");

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalScore()).isEqualByComparingTo("95.000");
        assertThat(result.get(0).explanation()).isNotNull();
        assertThat(result.get(0).explanation().summary()).isEqualTo("Bao cao cua lot moi");
        assertThat(result.get(0).explanationStatus()).isNull();
    }

    // Shape: don co bao cao -> explanation du 5 phan, explanationStatus null (da xong, khong can tin
    // hieu trang thai nua - xem comment enum trong DTO).
    @Test
    void listApplications_doneRunWithExplanation_explanationHasAllFiveParts() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRunWithTotalScore(applicationId, new BigDecimal("85.000"));
        createExplanation(runId, "Tom tat gia lap");

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        ApplicationHrListItemResponse.Explanation explanation = result.get(0).explanation();
        assertThat(explanation).isNotNull();
        assertThat(explanation.summary()).isEqualTo("Tom tat gia lap");
        assertThat(explanation.strengths()).extracting(ExplanationPoint::criterionName).containsExactly("Kinh nghiem Java");
        assertThat(explanation.weaknesses()).isEmpty();
        assertThat(explanation.metCriteria()).containsExactly("Kinh nghiem Java");
        assertThat(explanation.missingCriteria()).isEmpty();
        assertThat(result.get(0).explanationStatus()).isNull();
    }

    // Shape: don chua co lot DONE nao thi ca explanation lan explanationStatus deu null - giong quy
    // uoc totalScore/rank (khong co gi de "cho" khi chua co gi de giai thich).
    @Test
    void listApplications_unscoredApplication_explanationAndStatusAreNull() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        createPendingRun(applicationId);

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result.get(0).explanation()).isNull();
        assertThat(result.get(0).explanationStatus()).isNull();
    }

    // Diem 4/tin hieu trang thai (Dot 4): lot DONE chua co explanation VA chua tung thu lan nao ->
    // PENDING (con co the duoc thu lai o vong poll tiep theo).
    @Test
    void listApplications_doneRunNoExplanationNoAttempts_explanationStatusPending() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        createDoneRunWithTotalScore(applicationId, new BigDecimal("50.000"));

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result.get(0).explanation()).isNull();
        assertThat(result.get(0).explanationStatus()).isEqualTo(ApplicationHrListItemResponse.ExplanationStatus.PENDING);
    }

    // @Transactional o MUC METHOD (khong phai lop) - cung ly do voi ScoreExplanationAttemptRepositoryTest:
    // recordFailedAttempt la @Modifying, can mot transaction dang mo de thuc thi, va o day goi truc
    // tiep khong qua mot state service @Transactional nao (khac production, ScoreExplanationStateService
    // lam viec do). Khong dat o muc LOP vi cac test khac trong file nay dua vao now() PHAN BIET duoc
    // giua nhieu ban ghi trong CUNG mot test method (CLAUDE.md muc 3c) - ba test bien nay chi tao MOT
    // don/mot lot nen an toan.
    //
    // Bien duoi nguong (app.explanation.max-attempts=3, application.yml): da thu 2 lan (< 3) ->
    // van con co the duoc thu lai -> PENDING.
    @Test
    @Transactional
    void listApplications_attemptCountOneBelowMax_explanationStatusPending() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRunWithTotalScore(applicationId, new BigDecimal("50.000"));
        recordFailedAttempts(runId, 2);

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result.get(0).explanationStatus()).isEqualTo(ApplicationHrListItemResponse.ExplanationStatus.PENDING);
    }

    // Dung nguong: da thu DUNG 3 lan (== maxAttempts) -> ScoreExplanationScheduler (Dot 3) se khong
    // con nhat lot nay nua -> FAILED.
    @Test
    @Transactional
    void listApplications_attemptCountAtMax_explanationStatusFailed() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRunWithTotalScore(applicationId, new BigDecimal("50.000"));
        recordFailedAttempts(runId, 3);

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result.get(0).explanationStatus()).isEqualTo(ApplicationHrListItemResponse.ExplanationStatus.FAILED);
    }

    // Vuot nguong: da thu 4 lan (> maxAttempts, ve ly thuyet khong xay ra qua scheduler binh thuong
    // vi da bi loai tu vong truoc do, nhung du lieu ton tai duoc thi van phai xu ly dung) -> van FAILED
    // (dieu kien la >=, khong phai ==).
    @Test
    @Transactional
    void listApplications_attemptCountOneAboveMax_explanationStatusFailed() {
        UUID jobId = createJob();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createDoneRunWithTotalScore(applicationId, new BigDecimal("50.000"));
        recordFailedAttempts(runId, 4);

        List<ApplicationHrListItemResponse> result =
                applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC);

        assertThat(result.get(0).explanationStatus()).isEqualTo(ApplicationHrListItemResponse.ExplanationStatus.FAILED);
    }
}
