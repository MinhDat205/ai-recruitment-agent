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
import org.springframework.transaction.annotation.Transactional;

// @Modifying can mot transaction dang mo de thuc thi (chua co state service o Dot 1, se them o Dot
// 3) - cung ly do voi hai test claim_* trong ScoringRunRepositoryTest: @Transactional o MUC METHOD
// cho tung test goi recordFailedAttempt.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScoreExplanationAttemptRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private RubricRepository rubricRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ScoringRunRepository scoringRunRepository;

    @Autowired
    private ScoreExplanationAttemptRepository scoreExplanationAttemptRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createScoringRun() {
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
        application.setJobId(job.getId());
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(job.getRecruitmentCycle());
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        ScoringRun run = new ScoringRun();
        run.setApplicationId(application.getId());
        run.setStatus(ScoringRunStatus.DONE);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalScore(new BigDecimal("50.000"));
        run = scoringRunRepository.save(run);

        return run.getId();
    }

    @Test
    @Transactional
    void recordFailedAttempt_firstCall_insertsRowWithAttemptCountOne() {
        UUID scoringRunId = createScoringRun();

        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_INVALID_JSON: loi dinh dang");

        entityManager.clear();
        ScoreExplanationAttempt attempt =
                scoreExplanationAttemptRepository.findByScoringRunId(scoringRunId).orElseThrow();
        assertThat(attempt.getAttemptCount()).isEqualTo(1);
        assertThat(attempt.getLastError()).isEqualTo("LLM_INVALID_JSON: loi dinh dang");
        assertThat(attempt.getLastAttemptedAt()).isNotNull();
    }

    // Chung minh upsert TANG dong hien co thay vi tao dong moi (khong vi pham UNIQUE tren
    // scoring_run_id) - xem ly do chon ON CONFLICT DO UPDATE thay vi doc-roi-ghi trong
    // ScoreExplanationAttemptRepository.recordFailedAttempt.
    @Test
    @Transactional
    void recordFailedAttempt_secondCall_incrementsAttemptCountAndOverwritesLastError() {
        UUID scoringRunId = createScoringRun();
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_INVALID_JSON: loi lan 1");

        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 2");

        entityManager.clear();
        ScoreExplanationAttempt attempt =
                scoreExplanationAttemptRepository.findByScoringRunId(scoringRunId).orElseThrow();
        assertThat(attempt.getAttemptCount()).isEqualTo(2);
        assertThat(attempt.getLastError()).isEqualTo("LLM_ERROR: loi lan 2");
    }

    @Test
    @Transactional
    void recordFailedAttempt_thirdCall_attemptCountThree() {
        UUID scoringRunId = createScoringRun();
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 1");
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 2");

        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 3");

        entityManager.clear();
        ScoreExplanationAttempt attempt =
                scoreExplanationAttemptRepository.findByScoringRunId(scoringRunId).orElseThrow();
        assertThat(attempt.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @Transactional
    void existsByScoringRunIdAndAttemptCountGreaterThanEqual_belowThreshold_returnsFalse() {
        UUID scoringRunId = createScoringRun();
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 1");
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 2");

        boolean atThreshold = scoreExplanationAttemptRepository.existsByScoringRunIdAndAttemptCountGreaterThanEqual(
                scoringRunId, 3);

        assertThat(atThreshold).isFalse();
    }

    @Test
    @Transactional
    void existsByScoringRunIdAndAttemptCountGreaterThanEqual_atThreshold_returnsTrue() {
        UUID scoringRunId = createScoringRun();
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 1");
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 2");
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, "LLM_ERROR: loi lan 3");

        boolean atThreshold = scoreExplanationAttemptRepository.existsByScoringRunIdAndAttemptCountGreaterThanEqual(
                scoringRunId, 3);

        assertThat(atThreshold).isTrue();
    }

    @Test
    @Transactional
    void existsByScoringRunIdAndAttemptCountGreaterThanEqual_noAttemptYet_returnsFalse() {
        UUID scoringRunId = createScoringRun();

        boolean atThreshold = scoreExplanationAttemptRepository.existsByScoringRunIdAndAttemptCountGreaterThanEqual(
                scoringRunId, 3);

        assertThat(atThreshold).isFalse();
    }
}
