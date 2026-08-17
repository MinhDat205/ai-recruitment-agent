package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.ai.criterion.CriterionScorePayload;
import com.recruitment.ai.criterion.CriterionScoringResult;
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
import org.springframework.test.context.ActiveProfiles;

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
}
