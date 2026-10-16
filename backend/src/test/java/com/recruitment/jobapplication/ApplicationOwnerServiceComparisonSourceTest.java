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
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoringRun;
import com.recruitment.scoring.ScoringRunRepository;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-H14 dot 2 - ApplicationOwnerService.evaluateApplications (nguon du lieu bang so sanh, phan nguon cua T12, T13,
// T14). Lop RIENG, khong them vao ApplicationOwnerServiceTest (giu nguyen lop cu, khong dung khang dinh cu). Goi thang
// service, assert tren record da go kieu. KHONG @Transactional o cap lop (cung ApplicationOwnerServiceTest): moi
// saveAndFlush tu commit; thu tu "lot moi hon" van chot bang backdate tuong minh (CLAUDE.md muc 3c). Helper tao du lieu
// chep theo khuon ApplicationOwnerServiceTest (du an khong tach tien ich test dung chung).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ApplicationOwnerServiceComparisonSourceTest {

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
    private JdbcTemplate jdbcTemplate;

    private UUID hrOwnerId;

    private Job createJob() {
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
        return jobRepository.save(job);
    }

    // Mo lai Job sang chu ky moi (gia lap CLOSED -> OPEN cua JobOwnerService.changeStatus) - don tao sau do mang chu
    // ky moi qua createApplicationFor.
    private void bumpRecruitmentCycle(UUID jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setRecruitmentCycle(job.getRecruitmentCycle() + 1);
        jobRepository.save(job);
    }

    private JobApplication createApplicationFor(UUID jobId) {
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
        return jobApplicationRepository.save(application);
    }

    private static RubricSnapshot snapshotOf(String... criterionNames) {
        List<RubricSnapshot.CriterionSnapshot> criteria = Arrays.stream(criterionNames)
                .map(name -> new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), name, null, new BigDecimal("100.00"), 5, null))
                .toList();
        return new RubricSnapshot("Rubric Test", criteria);
    }

    private ScoringRun createDoneRun(UUID applicationId, RubricSnapshot snapshot, BigDecimal totalScore) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setRubricSnapshot(snapshot);
        run.setTotalScore(totalScore);
        return scoringRunRepository.saveAndFlush(run);
    }

    private UUID createDoneRunWithTotalScore(UUID applicationId, BigDecimal totalScore) {
        return createDoneRun(applicationId, new RubricSnapshot("Rubric Test", List.of()), totalScore).getId();
    }

    private UUID createFailedRun(UUID applicationId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.FAILED);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    private void insertCriterionScore(UUID runId, String name, BigDecimal score) {
        CriterionScore criterionScore = new CriterionScore();
        criterionScore.setScoringRunId(runId);
        criterionScore.setCriterionNameSnapshot(name);
        criterionScore.setWeightSnapshot(new BigDecimal("100.00"));
        criterionScore.setMaxScoreSnapshot(5);
        criterionScore.setScore(score);
        criterionScore.setReasoning("Ly do gia lap trong test");
        criterionScore.setEvidence(List.of(new EvidenceEntry("doan trich gia lap", "experience")));
        criterionScoreRepository.saveAndFlush(criterionScore);
    }

    // Backdate tuong minh created_at (CLAUDE.md muc 3c) - khong dua vao thu tu commit.
    private void setRunCreatedAt(UUID runId, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE scoring_runs SET created_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                runId);
    }

    private Map<UUID, ApplicationHrListItemResponse> listById(UUID jobId) {
        return applicationOwnerService.listApplications(hrOwnerId, jobId, ApplicationSortOption.TOTAL_SCORE_DESC).stream()
                .collect(Collectors.toMap(ApplicationHrListItemResponse::id, Function.identity()));
    }

    // T12 (phan nguon) + hoa diem: rank va totalScore bang DUNG gia tri cua listApplications cho cung don; kieu
    // 1-2-2-4 tinh tren toan Job, khong tinh lai trong nhom duoc chon.
    @Test
    void evaluateApplications_tiedScores_rankAndTotalScoreEqualListApplications() {
        Job job = createJob();
        UUID app80 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(app80, new BigDecimal("80.000"));
        UUID app70a = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(app70a, new BigDecimal("70.000"));
        UUID app70b = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(app70b, new BigDecimal("70.000"));
        UUID app60 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(app60, new BigDecimal("60.000"));
        UUID unscored = createApplicationFor(job.getId()).getId();

        Map<UUID, ApplicationComparisonSource> result =
                applicationOwnerService.evaluateApplications(job, List.of(app80, app70a, app70b, app60, unscored));
        Map<UUID, ApplicationHrListItemResponse> list = listById(job.getId());

        assertThat(result.get(app80).rank()).isEqualTo(1);
        assertThat(result.get(app70a).rank()).isEqualTo(2);
        assertThat(result.get(app70b).rank()).isEqualTo(2);
        assertThat(result.get(app60).rank()).isEqualTo(4);
        for (UUID id : List.of(app80, app70a, app70b, app60, unscored)) {
            assertThat(result.get(id).rank()).isEqualTo(list.get(id).rank());
            assertThat(result.get(id).totalScore()).isEqualTo(list.get(id).totalScore());
        }
        // Don chua cham van thuoc Job -> co trong map, nhung moi gia tri cua lot DONE la null/rong.
        assertThat(result.get(unscored).rank()).isNull();
        assertThat(result.get(unscored).scoringRunId()).isNull();
        assertThat(result.get(unscored).criterionScores()).isEmpty();
        assertThat(result.get(unscored).rubricCriteria()).isEmpty();
    }

    // Chon chi hai don 70 va 60 -> hang van [2, 4] (hang toan Job), khong thanh [1, 2].
    @Test
    void evaluateApplications_subsetSelected_keepsJobWideRankNotGroupRank() {
        Job job = createJob();
        createDoneRunWithTotalScore(createApplicationFor(job.getId()).getId(), new BigDecimal("80.000"));
        UUID app70 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(app70, new BigDecimal("70.000"));
        createDoneRunWithTotalScore(createApplicationFor(job.getId()).getId(), new BigDecimal("70.000"));
        UUID app60 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(app60, new BigDecimal("60.000"));

        Map<UUID, ApplicationComparisonSource> result =
                applicationOwnerService.evaluateApplications(job, List.of(app70, app60));

        assertThat(result).containsOnlyKeys(app70, app60);
        assertThat(result.get(app70).rank()).isEqualTo(2);
        assertThat(result.get(app60).rank()).isEqualTo(4);
    }

    // T13 (phan nguon, M1): hang tinh tren toan Job, KE CA don chu ky khac; recruitmentCycle la chu ky cua tung don.
    @Test
    void evaluateApplications_applicationsAcrossCycles_rankComputedOverWholeJob() {
        Job job = createJob();
        UUID cycleOne90 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(cycleOne90, new BigDecimal("90.000"));
        bumpRecruitmentCycle(job.getId());
        UUID cycleTwo80 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(cycleTwo80, new BigDecimal("80.000"));
        UUID cycleTwo70 = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(cycleTwo70, new BigDecimal("70.000"));

        Map<UUID, ApplicationComparisonSource> result =
                applicationOwnerService.evaluateApplications(job, List.of(cycleTwo80, cycleTwo70));

        assertThat(result.get(cycleTwo80).rank()).isEqualTo(2);
        assertThat(result.get(cycleTwo70).rank()).isEqualTo(3);
        assertThat(result.get(cycleTwo80).recruitmentCycle()).isEqualTo(2);
        assertThat(result.get(cycleTwo70).recruitmentCycle()).isEqualTo(2);
        assertThat(applicationOwnerService.evaluateApplications(job, List.of(cycleOne90)).get(cycleOne90).recruitmentCycle())
                .isEqualTo(1);
    }

    // T14 (phan nguon): hai lot DONE - scoringRunId, scoredAt, totalScore, criterionScores va rubricCriteria deu cua lot
    // co created_at MOI HON (backdate tuong minh); rubricCriteria la snapshot cua CHINH lot do (criterionId khop).
    @Test
    void evaluateApplications_twoDoneRuns_everythingComesFromNewerDoneRun() {
        Job job = createJob();
        UUID applicationId = createApplicationFor(job.getId()).getId();
        Instant now = Instant.now();

        RubricSnapshot oldSnapshot = snapshotOf("Tieu chi cu");
        ScoringRun oldRun = createDoneRun(applicationId, oldSnapshot, new BigDecimal("50.000"));
        insertCriterionScore(oldRun.getId(), "Tieu chi cu", new BigDecimal("2.50"));
        RubricSnapshot newSnapshot = snapshotOf("Tieu chi moi A", "Tieu chi moi B");
        ScoringRun newRun = createDoneRun(applicationId, newSnapshot, new BigDecimal("70.000"));
        insertCriterionScore(newRun.getId(), "Tieu chi moi A", new BigDecimal("3.50"));
        insertCriterionScore(newRun.getId(), "Tieu chi moi B", new BigDecimal("3.50"));
        setRunCreatedAt(oldRun.getId(), now.minusSeconds(3600));
        setRunCreatedAt(newRun.getId(), now.minusSeconds(60));

        ApplicationComparisonSource source =
                applicationOwnerService.evaluateApplications(job, List.of(applicationId)).get(applicationId);

        assertThat(source.scoringRunId()).isEqualTo(newRun.getId());
        assertThat(source.totalScore()).isEqualByComparingTo("70.000");
        assertThat(source.scoredAt()).isEqualTo(scoringRunRepository.findById(newRun.getId()).orElseThrow().getFinishedAt());
        assertThat(source.criterionScores())
                .extracting(ApplicationHrListItemResponse.CriterionScoreItem::criterionNameSnapshot)
                .containsExactly("Tieu chi moi A", "Tieu chi moi B");
        assertThat(source.rubricCriteria())
                .extracting(RubricSnapshot.CriterionSnapshot::criterionId)
                .containsExactlyElementsOf(newSnapshot.criteria().stream()
                        .map(RubricSnapshot.CriterionSnapshot::criterionId)
                        .toList());
        assertThat(source.latestRunStatus()).isEqualTo(ScoringRunStatus.DONE);
    }

    // Ca am cua T14: dao backdate (lot 70 cu hon lot 50) -> nguon la lot 50. Chung minh created_at quyet dinh, khong
    // phai thu tu chen hay diem cao hon.
    @Test
    void evaluateApplications_higherScoreRunIsOlder_usesNewerLowerScoreRun() {
        Job job = createJob();
        UUID applicationId = createApplicationFor(job.getId()).getId();
        Instant now = Instant.now();
        UUID run50 = createDoneRunWithTotalScore(applicationId, new BigDecimal("50.000"));
        UUID run70 = createDoneRunWithTotalScore(applicationId, new BigDecimal("70.000"));
        setRunCreatedAt(run70, now.minusSeconds(3600));
        setRunCreatedAt(run50, now.minusSeconds(60));

        ApplicationComparisonSource source =
                applicationOwnerService.evaluateApplications(job, List.of(applicationId)).get(applicationId);

        assertThat(source.scoringRunId()).isEqualTo(run50);
        assertThat(source.totalScore()).isEqualByComparingTo("50.000");
    }

    // Lot FAILED moi hon lot DONE: nguon diem van la lot DONE; latestRunStatus = FAILED (lot moi nhat bat ke trang
    // thai - nguon newerRunInProgress o dot 3).
    @Test
    void evaluateApplications_failedRunNewerThanDoneRun_keepsDoneRunAndReportsFailedAsLatest() {
        Job job = createJob();
        UUID applicationId = createApplicationFor(job.getId()).getId();
        Instant now = Instant.now();
        UUID doneRun = createDoneRunWithTotalScore(applicationId, new BigDecimal("65.000"));
        UUID failedRun = createFailedRun(applicationId);
        setRunCreatedAt(doneRun, now.minusSeconds(3600));
        setRunCreatedAt(failedRun, now.minusSeconds(60));

        ApplicationComparisonSource source =
                applicationOwnerService.evaluateApplications(job, List.of(applicationId)).get(applicationId);

        assertThat(source.scoringRunId()).isEqualTo(doneRun);
        assertThat(source.totalScore()).isEqualByComparingTo("65.000");
        assertThat(source.latestRunStatus()).isEqualTo(ScoringRunStatus.FAILED);
    }

    // Id khong thuoc Job (don cua Job khac, id khong ton tai) bi bo qua - map chi gom id cua Job (R-S3 kiem o dot 3).
    @Test
    void evaluateApplications_idsNotInJob_areAbsentFromResult() {
        Job job = createJob();
        UUID own = createApplicationFor(job.getId()).getId();
        createDoneRunWithTotalScore(own, new BigDecimal("60.000"));
        Job otherJob = createJob();
        UUID foreign = createApplicationFor(otherJob.getId()).getId();
        createDoneRunWithTotalScore(foreign, new BigDecimal("90.000"));
        UUID missing = UUID.randomUUID();

        Map<UUID, ApplicationComparisonSource> result =
                applicationOwnerService.evaluateApplications(job, List.of(own, foreign, missing));

        assertThat(result).containsOnlyKeys(own);
        assertThat(applicationOwnerService.evaluateApplications(job, List.of())).isEmpty();
    }

    // Field doc tu JobApplication da nap: resumeId, status, candidateName, appliedAt, screeningCapturedAt (null khi
    // chua chup; gia tri cot khi da chup).
    @Test
    void evaluateApplications_mapsApplicationFieldsIncludingScreeningCapturedAt() {
        Job job = createJob();
        JobApplication notCaptured = createApplicationFor(job.getId());
        JobApplication captured = createApplicationFor(job.getId());
        Instant capturedAt = Instant.parse("2026-10-05T17:35:00Z");
        jdbcTemplate.update(
                "UPDATE job_applications SET screening_captured_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(capturedAt, ZoneOffset.UTC),
                captured.getId());

        Map<UUID, ApplicationComparisonSource> result =
                applicationOwnerService.evaluateApplications(job, List.of(notCaptured.getId(), captured.getId()));

        ApplicationComparisonSource first = result.get(notCaptured.getId());
        assertThat(first.resumeId()).isEqualTo(notCaptured.getResumeId());
        assertThat(first.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(first.candidateName())
                .isEqualTo(userRepository.findById(notCaptured.getCandidateId()).orElseThrow().getFullName());
        assertThat(first.appliedAt()).isNotNull();
        assertThat(first.screeningCapturedAt()).isNull();
        assertThat(result.get(captured.getId()).screeningCapturedAt()).isEqualTo(capturedAt);
    }
}
