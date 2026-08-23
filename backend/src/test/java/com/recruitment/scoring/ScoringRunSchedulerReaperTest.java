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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Dot 4h (chore/hardening) - test wiring DAY DU cua ScoringRunScheduler.reapStaleClaims(), khac
// ScoringRunStateServiceTest (chi kiem findStaleClaimIds()/markTemporaryFailure() rieng le). Bean
// ScoringRunScheduler KHONG ton tai trong context test (tat qua app.scoring.enabled=false) - "new"
// mot instance rieng bang tay, mau ResumeParsingSchedulerReaperTest.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScoringRunSchedulerReaperTest {

    private static final long STALE_TIMEOUT_MS = 900_000; // 15 phut, giong mac dinh production

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private ScoringRunOrchestrator orchestrator;

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

    @PersistenceContext
    private EntityManager entityManager;

    private ScoringRunScheduler newScheduler() {
        return new ScoringRunScheduler(scoringRunRepository, orchestrator, stateService, 10, STALE_TIMEOUT_MS);
    }

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

    private UUID createRun(UUID applicationId, ScoringRunStatus status, Instant startedAt) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(status);
        run.setStartedAt(startedAt);
        return scoringRunRepository.saveAndFlush(run).getId();
    }

    // Luot claim qua lau (2 gio truoc, vuot xa 15 phut), finished_at con NULL - reapStaleClaims()
    // phai dua ve PENDING (con luot thu, max-attempts=3), KHONG FAILED thang, ma loi STALE_CLAIM_TIMEOUT.
    @Test
    void reapStaleClaims_runningStartedTwoHoursAgo_returnsToPendingWithStaleClaimTimeout() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING, Instant.now().minusSeconds(7200));

        newScheduler().reapStaleClaims();

        entityManager.clear();
        ScoringRun reloaded = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ScoringRunStatus.PENDING);
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getErrorMessage()).isEqualTo(ScoringRunErrorCode.STALE_CLAIM_TIMEOUT.formatted());
        assertThat(reloaded.getNextAttemptAt()).isNotNull();
    }

    // Luot vua claim (started_at = now(), duoi nguong 15 phut) - KHONG duoc dong toi.
    @Test
    void reapStaleClaims_recentlyStartedRun_isNotTouched() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING, Instant.now());

        newScheduler().reapStaleClaims();

        entityManager.clear();
        ScoringRun reloaded = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(reloaded.getAttemptCount()).isZero();
        assertThat(reloaded.getErrorMessage()).isNull();
    }

    // Luot DA cham xong (finished_at khac NULL), du started_at cu - dang cho D3 tong hop, KHONG
    // duoc reaper dong toi (dieu kien finished_at IS NULL da loai truoc o buoc doc).
    @Test
    void reapStaleClaims_finishedRunWithOldStartedAt_isNotTouched() {
        UUID jobId = createJobWithRubric();
        UUID applicationId = createApplicationFor(jobId);
        UUID runId = createRun(applicationId, ScoringRunStatus.RUNNING, Instant.now().minusSeconds(7200));
        stateService.markFinished(runId);
        Instant finishedAtBefore =
                scoringRunRepository.findById(runId).orElseThrow().getFinishedAt();

        newScheduler().reapStaleClaims();

        entityManager.clear();
        ScoringRun reloaded = scoringRunRepository.findById(runId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ScoringRunStatus.RUNNING);
        assertThat(reloaded.getFinishedAt()).isEqualTo(finishedAtBefore);
        assertThat(reloaded.getAttemptCount()).isZero();
    }
}
