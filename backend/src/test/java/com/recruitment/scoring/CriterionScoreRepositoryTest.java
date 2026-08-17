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

// KHONG @Transactional - cung ly do voi ScoringRunRepositoryTest/ResumeParsedDataRepositoryTest:
// test kiem tra DataIntegrityViolationException can moi saveAndFlush() tu commit doc lap, khong
// bi mot transaction bao ngoai cua test cuon vao rollback-only.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CriterionScoreRepositoryTest {

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
    private CriterionScoreRepository criterionScoreRepository;

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
        run.setStatus(ScoringRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run = scoringRunRepository.save(run);

        return run.getId();
    }

    private CriterionScore newScore(UUID scoringRunId, String criterionName, List<EvidenceEntry> evidence) {
        CriterionScore score = new CriterionScore();
        score.setScoringRunId(scoringRunId);
        score.setCriterionNameSnapshot(criterionName);
        score.setWeightSnapshot(new BigDecimal("40.00"));
        score.setMaxScoreSnapshot(5);
        score.setScore(new BigDecimal("4.00"));
        score.setReasoning("Ung vien co de cap Docker trong phan kinh nghiem lam viec");
        score.setEvidence(evidence);
        return score;
    }

    @Test
    void saveAndReload_roundTripsEvidenceJsonbExactly() {
        UUID scoringRunId = createScoringRun();
        List<EvidenceEntry> evidence = List.of(
                new EvidenceEntry("Trien khai he thong voi Docker va Docker Compose", "experience"),
                new EvidenceEntry("Docker", "skills"));

        CriterionScore score = newScore(scoringRunId, "Kinh nghiem Docker", evidence);
        criterionScoreRepository.saveAndFlush(score);
        entityManager.clear();

        CriterionScore reloaded = criterionScoreRepository.findById(score.getId()).orElseThrow();
        assertThat(reloaded.getScoringRunId()).isEqualTo(scoringRunId);
        assertThat(reloaded.getCriterionNameSnapshot()).isEqualTo("Kinh nghiem Docker");
        assertThat(reloaded.getWeightSnapshot()).isEqualByComparingTo("40.00");
        assertThat(reloaded.getMaxScoreSnapshot()).isEqualTo(5);
        assertThat(reloaded.getScore()).isEqualByComparingTo("4.00");
        assertThat(reloaded.getCreatedAt()).isNotNull();

        assertThat(reloaded.getEvidence()).hasSize(2);
        assertThat(reloaded.getEvidence().get(0).quote())
                .isEqualTo("Trien khai he thong voi Docker va Docker Compose");
        assertThat(reloaded.getEvidence().get(0).section()).isEqualTo("experience");
        assertThat(reloaded.getEvidence().get(1).section()).isEqualTo("skills");
    }

    @Test
    void saveAndReload_emptyEvidenceList_roundTripsAsEmptyListNotNull() {
        UUID scoringRunId = createScoringRun();

        CriterionScore score = newScore(scoringRunId, "Kinh nghiem Kubernetes", List.of());
        score.setScore(BigDecimal.ZERO);
        criterionScoreRepository.saveAndFlush(score);
        entityManager.clear();

        CriterionScore reloaded = criterionScoreRepository.findById(score.getId()).orElseThrow();
        assertThat(reloaded.getEvidence()).isNotNull().isEmpty();
    }

    @Test
    void save_secondRowSameScoringRunAndCriterionName_violatesUniqueConstraint() {
        UUID scoringRunId = createScoringRun();
        criterionScoreRepository.saveAndFlush(
                newScore(scoringRunId, "Kinh nghiem Docker", List.of(new EvidenceEntry("Docker", "skills"))));

        CriterionScore duplicate =
                newScore(scoringRunId, "Kinh nghiem Docker", List.of(new EvidenceEntry("Docker Compose", "skills")));

        assertThrows(DataIntegrityViolationException.class, () -> criterionScoreRepository.saveAndFlush(duplicate));
    }
}
