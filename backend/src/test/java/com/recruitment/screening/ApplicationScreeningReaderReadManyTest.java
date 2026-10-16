package com.recruitment.screening;

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
import com.recruitment.screening.ApplicationScreeningReader.ScreeningBundle;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// FR-H14 dot 2 - ApplicationScreeningReader.readMany (ban chup sang loc cua nhieu don trong mot query). Goi thang
// reader. Dong ban chup + cau hoi goc ghi bang JdbcTemplate (entity ApplicationScreeningAnswer @Immutable, khong
// setter - mau ApplicationScreeningHrIntegrationTest). capturedAt truyen thang vao readMany (noi goi that doc tu
// JobApplication da nap) nen test khong can ghi cot screening_captured_at. @Transactional: moi @Test rollback rieng;
// JdbcTemplate dung chung connection cua transaction test. Khong khang dinh thu tu thoi gian giua cac ban ghi.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApplicationScreeningReaderReadManyTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-10-05T17:35:00Z");

    @Autowired
    private ApplicationScreeningReader reader;

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

    private Job createJob() {
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
        // saveAndFlush: cau hoi goc chen bang JdbcTemplate ngay sau do can thay Job (FK job_id).
        return jobRepository.saveAndFlush(job);
    }

    private UUID createApplication(Job job) {
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
        return jobApplicationRepository.saveAndFlush(application).getId();
    }

    // Cau hoi goc that (question_id cua ban chup co FK toi job_screening_questions).
    private UUID insertQuestion(Job job, String type, String text, int displayOrder) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO job_screening_questions (job_id, question_type, question_text, display_order)
                VALUES (?, ?, ?, ?) RETURNING id
                """,
                UUID.class,
                job.getId(), type, text, displayOrder);
    }

    private void insertAnswer(
            UUID applicationId,
            UUID questionId,
            int displayOrder,
            String type,
            String questionText,
            String optionsJson,
            String answerText,
            Boolean answerYesNo) {
        jdbcTemplate.update(
                """
                INSERT INTO application_screening_answers
                    (application_id, question_id, display_order, question_type_snapshot, question_text_snapshot,
                     options_snapshot, required_snapshot, answer_text, answer_yes_no)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
                applicationId, questionId, displayOrder, type, questionText, optionsJson, false, answerText,
                answerYesNo);
    }

    // Ba trang thai trong MOT lan goi: capturedAt null -> NOT_CAPTURED (ke ca khi co dong ban chup - cot quyet dinh
    // truoc, R-A3 cua FR-H10); co capturedAt, 0 dong -> NO_QUESTIONS; co dong -> CAPTURED.
    @Test
    void readMany_threeApplications_returnsEachCaptureState() {
        Job job = createJob();
        UUID notCaptured = createApplication(job);
        UUID noQuestions = createApplication(job);
        UUID captured = createApplication(job);
        insertAnswer(notCaptured, null, 1, "YES_NO", "Cau hoi", null, null, true);
        insertAnswer(captured, null, 1, "YES_NO", "San sang lam ca toi?", null, null, false);

        Map<UUID, Instant> capturedAtById = new HashMap<>();
        capturedAtById.put(notCaptured, null);
        capturedAtById.put(noQuestions, CAPTURED_AT);
        capturedAtById.put(captured, CAPTURED_AT);

        Map<UUID, ScreeningBundle> result = reader.readMany(capturedAtById);

        assertThat(result).containsOnlyKeys(notCaptured, noQuestions, captured);
        assertThat(result.get(notCaptured).state()).isEqualTo(ScreeningCaptureState.NOT_CAPTURED);
        assertThat(result.get(notCaptured).capturedAt()).isNull();
        assertThat(result.get(notCaptured).answers()).isEmpty();
        assertThat(result.get(noQuestions).state()).isEqualTo(ScreeningCaptureState.NO_QUESTIONS);
        assertThat(result.get(noQuestions).capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(result.get(noQuestions).answers()).isEmpty();
        assertThat(result.get(captured).state()).isEqualTo(ScreeningCaptureState.CAPTURED);
        assertThat(result.get(captured).capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(result.get(captured).answers()).hasSize(1);
    }

    // Ban chup co questionId (ca khi null), dung cot *_snapshot va cau tra loi; thu tu theo display_order trong TUNG
    // don (chen nguoc thu tu de khong trung hop voi thu tu chen); dong cua don nay khong lan sang don kia.
    @Test
    void readMany_capturedApplications_snapshotsCarryQuestionIdAndFollowDisplayOrderPerApplication() {
        Job job = createJob();
        UUID questionYesNo = insertQuestion(job, "YES_NO", "Bat dau trong 2 tuan?", 1);
        UUID questionShort = insertQuestion(job, "SHORT_TEXT", "Muc luong mong muon?", 2);
        UUID first = createApplication(job);
        UUID second = createApplication(job);

        insertAnswer(first, null, 3, "SINGLE_CHOICE", "Ca lam viec?", "[\"Sang\", \"Chieu\"]", "Chieu", null);
        insertAnswer(first, questionShort, 2, "SHORT_TEXT", "Muc luong mong muon?", null, "25 trieu", null);
        insertAnswer(first, questionYesNo, 1, "YES_NO", "Bat dau trong 2 tuan?", null, null, true);
        insertAnswer(second, questionShort, 2, "SHORT_TEXT", "Muc luong mong muon?", null, null, null);
        insertAnswer(second, questionYesNo, 1, "YES_NO", "Bat dau trong 2 tuan?", null, null, false);

        Map<UUID, ScreeningBundle> result = reader.readMany(Map.of(first, CAPTURED_AT, second, CAPTURED_AT));

        List<ScreeningAnswerSnapshot> firstAnswers = result.get(first).answers();
        assertThat(firstAnswers).extracting(ScreeningAnswerSnapshot::displayOrder).containsExactly(1, 2, 3);
        assertThat(firstAnswers)
                .extracting(ScreeningAnswerSnapshot::questionId)
                .containsExactly(questionYesNo, questionShort, null);
        assertThat(firstAnswers.get(0).questionType()).isEqualTo(ScreeningQuestionType.YES_NO);
        assertThat(firstAnswers.get(0).answerYesNo()).isTrue();
        assertThat(firstAnswers.get(1).answerText()).isEqualTo("25 trieu");
        assertThat(firstAnswers.get(2).questionType()).isEqualTo(ScreeningQuestionType.SINGLE_CHOICE);
        assertThat(firstAnswers.get(2).options()).containsExactly("Sang", "Chieu");
        assertThat(firstAnswers.get(2).answerText()).isEqualTo("Chieu");

        List<ScreeningAnswerSnapshot> secondAnswers = result.get(second).answers();
        assertThat(secondAnswers).extracting(ScreeningAnswerSnapshot::displayOrder).containsExactly(1, 2);
        assertThat(secondAnswers.get(0).answerYesNo()).isFalse();
        // Khong tra loi -> answerText va answerYesNo deu null (khong bi doi thanh chuoi rong).
        assertThat(secondAnswers.get(1).answerText()).isNull();
        assertThat(secondAnswers.get(1).answerYesNo()).isNull();
    }

    // Bien: map rong -> map rong, khong chay query voi IN ().
    @Test
    void readMany_emptyInput_returnsEmptyMap() {
        assertThat(reader.readMany(Map.of())).isEmpty();
    }

    // read(...) cu khong doi: cung du lieu, state cua read va readMany trung nhau cho ca ba truong hop co the dung
    // read (read tu doc cot screening_captured_at nen chi so sanh duoc khi cot = gia tri truyen vao readMany).
    @Test
    void readMany_capturedApplication_stateMatchesSingleRead() {
        Job job = createJob();
        UUID captured = createApplication(job);
        jdbcTemplate.update(
                "UPDATE job_applications SET screening_captured_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(CAPTURED_AT, ZoneOffset.UTC),
                captured);
        insertAnswer(captured, null, 1, "YES_NO", "Cau hoi", null, null, true);

        ScreeningBundle bundle = reader.readMany(Map.of(captured, CAPTURED_AT)).get(captured);

        assertThat(bundle.state()).isEqualTo(reader.read(captured).state());
        assertThat(bundle.answers()).hasSize(reader.read(captured).items().size());
    }
}
