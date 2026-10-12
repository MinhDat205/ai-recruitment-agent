package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.exception.ScreeningQuestionsChangedException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationService;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.screening.dto.ScreeningAnswerInput;
import com.recruitment.screening.dto.ScreeningQuestionRequest;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// FR-U09 T8 (B3, R-R3, R-R5) - HR sua cau hoi trong luc ung vien nop don: hoac 409 SCREENING_QUESTIONS_CHANGED, hoac ban
// chup khop DUNG bo cau hoi da kiem; khong bao gio ghep lech.
// KHONG @Transactional toan lop (CLAUDE.md §3c): hai luong phai thay du lieu da commit cua nhau. Mot ben chay trong
// TransactionTemplate cua test va GIU transaction (giu khoa hang jobs); ben kia chay o luong rieng. "Ben kia dang bi khoa
// chan" xac nhan bang pg_stat_activity (wait_event_type = 'Lock'), KHONG cho co dinh theo thoi gian (L5); het 10 s ma
// chua thay thi test do. Du lieu ngau nhien rieng, chi khang dinh tren tin cua test, khong don sau test (L2 cua FR-H10).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ScreeningCaptureConcurrencyIntegrationTest {

    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;
    private static final long LOCK_POLL_INTERVAL_MS = 50;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ScreeningQuestionOwnerService questionOwnerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private record Fixture(UUID ownerId, UUID jobId, UUID q1, UUID q2, UUID candidateId, UUID resumeId) {
    }

    private Fixture createFixture() {
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
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        UUID jobId = jobRepository.save(job).getId();

        UUID q1 = questionOwnerService.add(hr.getId(), jobId, shortText("Cau 1")).id();
        UUID q2 = questionOwnerService.add(hr.getId(), jobId, shortText("Cau 2")).id();

        User candidate = user(Role.CANDIDATE);
        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        UUID resumeId = resumeRepository.save(resume).getId();

        return new Fixture(hr.getId(), jobId, q1, q2, candidate.getId(), resumeId);
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("t8-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private static ScreeningQuestionRequest shortText(String text) {
        return new ScreeningQuestionRequest(ScreeningQuestionType.SHORT_TEXT, text, null, false);
    }

    private static ApplicationCreateRequest request(Fixture fixture, int revision, String answerToQ1) {
        return new ApplicationCreateRequest(
                fixture.jobId(),
                fixture.resumeId(),
                true,
                null,
                revision,
                List.of(new ScreeningAnswerInput(fixture.q1(), answerToQ1, null)),
                null);
    }

    private int revision(UUID jobId) {
        return jdbcTemplate.queryForObject("SELECT screening_revision FROM jobs WHERE id = ?", Integer.class, jobId);
    }

    // L5 - cho toi khi MOT phien KHAC (pid <> pg_backend_pid()) cua DB test dang doi khoa tren cau co screening_revision:
    // ca (a) la SELECT ... FOR SHARE cua apply, ca (b) la UPDATE jobs SET screening_revision cua HR. Het gio -> do.
    // L9 - ham nay chay TRONG transaction dang giu khoa; Postgres chup pg_stat_activity lan dau truy cap va giu nguyen toi
    // het transaction, nen PHAI pg_stat_clear_snapshot() truoc moi lan tham do (khong thi luon thay 0 phien).
    private void awaitOtherSessionBlockedOnLock(String who) {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
            Integer waiting = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE '%screening_revision%'
                    """,
                    Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            try {
                Thread.sleep(LOCK_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Bi ngat khi cho phien " + who + " bi khoa chan");
            }
        }
        fail("Phien " + who + " khong bi khoa chan sau " + LOCK_WAIT_TIMEOUT_MS + " ms - khoa hang jobs khong hoat dong");
    }

    // (a) HR truoc: transaction HR giu khoa hang jobs (UPDATE ... screening_revision). Nop don voi revision cu phai CHO o
    // FOR SHARE; HR commit xong thi doc revision da commit -> 409, khong tao don.
    @Test
    void t8_hrHoldsLock_applyWaitsThenConflicts() throws Exception {
        Fixture fixture = createFixture();
        int oldRevision = revision(fixture.jobId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<ApplicationResponse>> applyFuture = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                questionOwnerService.update(
                        fixture.ownerId(),
                        fixture.jobId(),
                        fixture.q1(),
                        new ScreeningQuestionRequest(ScreeningQuestionType.YES_NO, "Cau 1 da doi loai", null, false));
                applyFuture.set(executor.submit(
                        () -> applicationService.apply(fixture.candidateId(), request(fixture, oldRevision, "Tra loi"))));
                awaitOtherSessionBlockedOnLock("nop don");
            });

            ExecutionException thrown =
                    assertThrows(ExecutionException.class, () -> applyFuture.get().get(30, TimeUnit.SECONDS));
            assertThat(thrown.getCause()).isInstanceOf(ScreeningQuestionsChangedException.class);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM job_applications WHERE job_id = ?", Integer.class, fixture.jobId()))
                .isZero();
        assertThat(revision(fixture.jobId())).isEqualTo(oldRevision + 1);
    }

    // (b) Ung vien truoc: transaction nop don giu FOR SHARE. Cau UPDATE cua HR phai CHO; nop don commit voi ban chup
    // dung bo cau hoi TRUOC khi sua, roi thay doi cua HR moi ap dung.
    @Test
    void t8_applyHoldsLock_hrWaits_snapshotMatchesQuestionsBeforeEdit() throws Exception {
        Fixture fixture = createFixture();
        int revisionBefore = revision(fixture.jobId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> hrFuture = new AtomicReference<>();
        AtomicReference<UUID> applicationId = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                applicationId.set(applicationService
                        .apply(fixture.candidateId(), request(fixture, revisionBefore, "Tra loi cau 1"))
                        .id());
                hrFuture.set(executor.submit(() -> questionOwnerService.update(
                        fixture.ownerId(),
                        fixture.jobId(),
                        fixture.q1(),
                        new ScreeningQuestionRequest(ScreeningQuestionType.YES_NO, "Cau 1 da doi loai", null, false))));
                awaitOtherSessionBlockedOnLock("HR sua cau hoi");
            });

            hrFuture.get().get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT question_id, question_type_snapshot, question_text_snapshot, answer_text
                FROM application_screening_answers WHERE application_id = ? ORDER BY display_order
                """,
                applicationId.get());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("question_id")).isEqualTo(fixture.q1());
        assertThat(rows.get(0).get("question_type_snapshot")).as("ban chup truoc khi HR doi loai").isEqualTo("SHORT_TEXT");
        assertThat(rows.get(0).get("question_text_snapshot")).isEqualTo("Cau 1");
        assertThat(rows.get(0).get("answer_text")).isEqualTo("Tra loi cau 1");
        assertThat(rows.get(1).get("question_id")).isEqualTo(fixture.q2());

        assertThat(revision(fixture.jobId())).as("HR hoan tat sau khi nop don commit").isEqualTo(revisionBefore + 1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT question_type FROM job_screening_questions WHERE id = ?", String.class, fixture.q1()))
                .isEqualTo("YES_NO");
    }
}
