package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// FR-H10 T15 (R-S6, muc 4.1) - rang buoc CHECK cua V13, insert thang bang JdbcTemplate (khong qua service).
// KHONG @Transactional: Postgres huy ca transaction sau cau lenh loi dau tien, cac ca sau se sai vi ly do khac. Moi
// cau lenh tu commit; du lieu ngau nhien rieng, khong don sau test (L2).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScreeningSchemaConstraintIntegrationTest {

    private static final String INSERT_ANSWER =
            """
            INSERT INTO application_screening_answers
                (application_id, display_order, question_type_snapshot, question_text_snapshot, options_snapshot,
                 required_snapshot, answer_text, answer_yes_no)
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
            """;

    private static final String INSERT_QUESTION =
            """
            INSERT INTO job_screening_questions (job_id, question_type, question_text, options, display_order)
            VALUES (?, ?, ?, CAST(? AS jsonb), ?)
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private UUID jobId;
    private UUID applicationId;
    private int nextOrder;

    @BeforeEach
    void createJobAndApplication() {
        User hr = user(Role.HR);
        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle("Nhan vien CSKH");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.DRAFT);
        job.setRecruitmentCycle(1);
        jobId = jobRepository.save(job).getId();

        User candidate = user(Role.CANDIDATE);
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
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        applicationId = jobApplicationRepository.save(application).getId();
        nextOrder = 1;
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("t15-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private int insertAnswer(
            String type, String options, boolean required, String answerText, Boolean answerYesNo) {
        return jdbcTemplate.update(
                INSERT_ANSWER, applicationId, nextOrder++, type, "Cau hoi", options, required, answerText, answerYesNo);
    }

    @Test
    void t15_answerTextLength_499And500Accepted_501Rejected() {
        assertThat(insertAnswer("SHORT_TEXT", null, false, "a".repeat(499), null)).isEqualTo(1);
        assertThat(insertAnswer("SHORT_TEXT", null, false, "b".repeat(500), null)).isEqualTo(1);
        assertThatThrownBy(() -> insertAnswer("SHORT_TEXT", null, false, "c".repeat(501), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void t15_answerShapeAndRequired_rejected() {
        assertThatThrownBy(() -> insertAnswer("YES_NO", null, false, "Co", null))
                .as("YES_NO co answer_text")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAnswer("SHORT_TEXT", null, true, null, null))
                .as("cau bat buoc khong co tra loi")
                .isInstanceOf(DataIntegrityViolationException.class);
        // Doi chung duong: cung hinh dang nhung hop le thi insert duoc.
        assertThat(insertAnswer("YES_NO", null, true, null, true)).isEqualTo(1);
        assertThat(insertAnswer("SHORT_TEXT", null, false, null, null)).isEqualTo(1);
    }

    @Test
    void t15_optionsSnapshotShape_rejected() {
        assertThatThrownBy(() -> insertAnswer("SINGLE_CHOICE", null, false, null, null))
                .as("SINGLE_CHOICE options_snapshot NULL")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAnswer("SINGLE_CHOICE", "[\"A\"]", false, null, null))
                .as("SINGLE_CHOICE options_snapshot 1 phan tu")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAnswer("SHORT_TEXT", "[\"A\",\"B\"]", false, null, null))
                .as("SHORT_TEXT co options_snapshot")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertAnswer("YES_NO", "[\"A\",\"B\"]", false, null, null))
                .as("YES_NO co options_snapshot")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insertAnswer("SINGLE_CHOICE", "[\"A\",\"B\"]", false, "A", null)).isEqualTo(1);
    }

    @Test
    void t15_questionOptionsShape_rejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_QUESTION, jobId, "SINGLE_CHOICE", "Cau hoi", "[\"A\"]", 1))
                .as("job_screening_questions SINGLE_CHOICE 1 phuong an")
                .isInstanceOf(DataIntegrityViolationException.class);
        // L7 - truoc khi them IS NOT NULL, CHECK ra NULL voi options NULL va Postgres cho qua.
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_QUESTION, jobId, "SINGLE_CHOICE", "Cau hoi", null, 1))
                .as("job_screening_questions SINGLE_CHOICE options NULL")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(INSERT_QUESTION, jobId, "SHORT_TEXT", "Cau hoi", "[\"A\",\"B\"]", 1))
                .as("job_screening_questions SHORT_TEXT co options")
                .isInstanceOf(DataIntegrityViolationException.class);
        // Doi chung duong.
        assertThat(jdbcTemplate.update(INSERT_QUESTION, jobId, "SINGLE_CHOICE", "Cau hoi", "[\"A\",\"B\"]", 2))
                .isEqualTo(1);
        assertThat(jdbcTemplate.update(INSERT_QUESTION, jobId, "SHORT_TEXT", "Cau hoi", null, 3)).isEqualTo(1);
    }
}
