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

// KHONG @Transactional o muc class - test kiem tra DataIntegrityViolationException can moi
// saveAndFlush() tu commit doc lap, khong bi mot transaction bao ngoai cuon vao rollback-only. Cung
// mau CriterionScoreRepositoryTest/ScoringRunRepositoryTest.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScoreExplanationRepositoryTest {

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
    private ScoreExplanationRepository scoreExplanationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID createDoneScoringRun() {
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
        run.setTotalScore(new BigDecimal("72.500"));
        run = scoringRunRepository.save(run);

        return run.getId();
    }

    private ScoreExplanation newExplanation(
            UUID scoringRunId,
            List<ExplanationPoint> strengths,
            List<ExplanationPoint> weaknesses,
            List<String> metCriteria,
            List<String> missingCriteria) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(scoringRunId);
        explanation.setSummary("Ung vien co kinh nghiem Java vung, con thieu chung chi AWS");
        explanation.setStrengths(strengths);
        explanation.setWeaknesses(weaknesses);
        explanation.setMetCriteria(metCriteria);
        explanation.setMissingCriteria(missingCriteria);
        explanation.setModel("claude-sonnet-4-6");
        explanation.setPromptVersion("score-explanation-v1");
        return explanation;
    }

    @Test
    void saveAndReload_roundTripsAllFourJsonbArraysExactly() {
        UUID scoringRunId = createDoneScoringRun();
        List<ExplanationPoint> strengths = List.of(
                new ExplanationPoint("Kinh nghiem Java", "3 nam kinh nghiem, danh gia cao o tieu chi nay"),
                new ExplanationPoint("Ky nang giao tiep", "The hien ro qua qua trinh lam viec nhom"));
        List<ExplanationPoint> weaknesses =
                List.of(new ExplanationPoint("Chung chi AWS", "Khong tim thay bang chung lien quan"));
        List<String> metCriteria = List.of("Kinh nghiem Java", "Ky nang giao tiep");
        List<String> missingCriteria = List.of("Chung chi AWS");

        ScoreExplanation explanation =
                newExplanation(scoringRunId, strengths, weaknesses, metCriteria, missingCriteria);
        scoreExplanationRepository.saveAndFlush(explanation);
        entityManager.clear();

        ScoreExplanation reloaded =
                scoreExplanationRepository.findById(explanation.getId()).orElseThrow();
        assertThat(reloaded.getScoringRunId()).isEqualTo(scoringRunId);
        assertThat(reloaded.getSummary()).isEqualTo(explanation.getSummary());
        assertThat(reloaded.getModel()).isEqualTo("claude-sonnet-4-6");
        assertThat(reloaded.getPromptVersion()).isEqualTo("score-explanation-v1");
        assertThat(reloaded.getGeneratedAt()).isNotNull();

        assertThat(reloaded.getStrengths()).hasSize(2);
        assertThat(reloaded.getStrengths().get(0).criterionName()).isEqualTo("Kinh nghiem Java");
        assertThat(reloaded.getStrengths().get(0).point())
                .isEqualTo("3 nam kinh nghiem, danh gia cao o tieu chi nay");
        assertThat(reloaded.getStrengths().get(1).criterionName()).isEqualTo("Ky nang giao tiep");

        assertThat(reloaded.getWeaknesses()).hasSize(1);
        assertThat(reloaded.getWeaknesses().get(0).criterionName()).isEqualTo("Chung chi AWS");
        assertThat(reloaded.getWeaknesses().get(0).point()).isEqualTo("Khong tim thay bang chung lien quan");

        assertThat(reloaded.getMetCriteria()).containsExactly("Kinh nghiem Java", "Ky nang giao tiep");
        assertThat(reloaded.getMissingCriteria()).containsExactly("Chung chi AWS");
    }

    @Test
    void saveAndReload_emptyArrays_roundTripAsEmptyListsNotNull() {
        UUID scoringRunId = createDoneScoringRun();

        ScoreExplanation explanation = newExplanation(scoringRunId, List.of(), List.of(), List.of(), List.of());
        scoreExplanationRepository.saveAndFlush(explanation);
        entityManager.clear();

        ScoreExplanation reloaded =
                scoreExplanationRepository.findById(explanation.getId()).orElseThrow();
        assertThat(reloaded.getStrengths()).isNotNull().isEmpty();
        assertThat(reloaded.getWeaknesses()).isNotNull().isEmpty();
        assertThat(reloaded.getMetCriteria()).isNotNull().isEmpty();
        assertThat(reloaded.getMissingCriteria()).isNotNull().isEmpty();
    }

    // Chung minh CHOT CHAN LA DB (UNIQUE tren scoring_run_id, V1), khong phai code Java - Dot 3 se
    // dua vao dung constraint nay de khong claim rieng (xem comment trong ScoreExplanation).
    @Test
    void save_secondRowSameScoringRunId_violatesUniqueConstraint() {
        UUID scoringRunId = createDoneScoringRun();
        scoreExplanationRepository.saveAndFlush(
                newExplanation(scoringRunId, List.of(), List.of(), List.of(), List.of()));

        ScoreExplanation duplicate = newExplanation(scoringRunId, List.of(), List.of(), List.of(), List.of());

        assertThrows(DataIntegrityViolationException.class, () -> scoreExplanationRepository.saveAndFlush(duplicate));
    }

    @Test
    void findByScoringRunIdIn_returnsOnlyExplanationsThatExist() {
        UUID runA = createDoneScoringRun();
        UUID runB = createDoneScoringRun();
        UUID runC = createDoneScoringRun();
        scoreExplanationRepository.saveAndFlush(newExplanation(runA, List.of(), List.of(), List.of(), List.of()));
        scoreExplanationRepository.saveAndFlush(newExplanation(runB, List.of(), List.of(), List.of(), List.of()));
        // runC co y khong co bao cao - kiem tra khong bi tra thua/loi khi mot phan tu trong danh
        // sach truyen vao khong co dong tuong ung.

        List<ScoreExplanation> found = scoreExplanationRepository.findByScoringRunIdIn(List.of(runA, runB, runC));

        assertThat(found).extracting(ScoreExplanation::getScoringRunId).containsExactlyInAnyOrder(runA, runB);
    }

    @Test
    void existsByScoringRunId_noExplanationYet_returnsFalse() {
        UUID scoringRunId = createDoneScoringRun();

        assertThat(scoreExplanationRepository.existsByScoringRunId(scoringRunId)).isFalse();
    }

    @Test
    void existsByScoringRunId_explanationSaved_returnsTrue() {
        UUID scoringRunId = createDoneScoringRun();
        scoreExplanationRepository.saveAndFlush(
                newExplanation(scoringRunId, List.of(), List.of(), List.of(), List.of()));

        assertThat(scoreExplanationRepository.existsByScoringRunId(scoringRunId)).isTrue();
    }
}
