package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.exception.InvitationResendNotAllowedException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest.SlotRequest;
import com.recruitment.interviewtemplate.InterviewTemplate;
import com.recruitment.interviewtemplate.InterviewTemplateRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationService;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// FR-H12 T10 (R-G3, R-G4) - tuan tu hoa gui lai voi gui lai / Trung tuyen / Rut don bang khoa hang don
// (lockIfInterviewInvited). KHONG @Transactional toan lop (CLAUDE.md §3c, muc 12 L8): hai luong phai thay du lieu da
// commit cua nhau. Goi thang service (khuon ScreeningCaptureConcurrencyIntegrationTest): "201/200" = method tra ve binh
// thuong, "409" = InvitationResendNotAllowedException, "khong 500" = khong exception nao khac. Du lieu ngau nhien rieng,
// chi khang dinh tren don cua test, khong don sau test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class InterviewInvitationResendConcurrencyIntegrationTest {

    private static final int ROUNDS = 10;

    @Autowired
    private InterviewInvitationService invitationService;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private InterviewTemplateRepository interviewTemplateRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private record Fixture(UUID ownerId, UUID candidateId, UUID applicationId) {
    }

    // Don da INTERVIEW_INVITED qua I2 that (1 khung, da chot).
    private Fixture createInvitedApplication() {
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
        job = jobRepository.save(job);

        InterviewTemplate template = new InterviewTemplate();
        template.setJobId(job.getId());
        template.setCompanyName(company.getName());
        template.setSubject("Thu moi");
        template.setBody("Noi dung");
        template.setSenderName("Phong Nhan Su");
        interviewTemplateRepository.save(template);

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
        application.setJobId(job.getId());
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        invitationService.sendInvitation(hr.getId(), application.getId(), request(2));
        return new Fixture(hr.getId(), candidate.getId(), application.getId());
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("t10-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private static InterviewInvitationSendRequest request(int daysAhead) {
        Instant at = Instant.now().plus(daysAhead, ChronoUnit.DAYS);
        return new InterviewInvitationSendRequest(
                List.of(new SlotRequest(at, "Tang 3"), new SlotRequest(at.plus(1, ChronoUnit.HOURS), "Tang 4")),
                "Thu moi gui lai",
                "Noi dung gui lai");
    }

    private int invitationCount(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ?", Integer.class, applicationId);
    }

    private int activeInvitationCount(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ? AND superseded_at IS NULL",
                Integer.class,
                applicationId);
    }

    private String statusOf(UUID applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM job_applications WHERE id = ?", String.class, applicationId);
    }

    private int resentNotificationCount(UUID candidateId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'INTERVIEW_INVITATION_RESENT'",
                Integer.class,
                candidateId);
    }

    // Ket qua mot luong: null = thanh cong; khac null = exception (da go ExecutionException).
    private static Throwable outcome(Future<?> future) throws InterruptedException, TimeoutException {
        try {
            future.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        }
    }

    // Chay hai viec cung luc (cung qua mot cong CountDownLatch) va tra hai ket qua.
    private static Throwable[] runConcurrently(ExecutorService executor, Callable<?> first, Callable<?> second)
            throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        Future<?> f1 = executor.submit(() -> {
            gate.await();
            return first.call();
        });
        Future<?> f2 = executor.submit(() -> {
            gate.await();
            return second.call();
        });
        gate.countDown();
        return new Throwable[] {outcome(f1), outcome(f2)};
    }

    // (a) Hai lan gui lai song song tren cung don -> ca hai thanh cong; 3 giay moi, dung mot hieu luc.
    @Test
    void t10a_twoParallelResends_bothSucceed_exactlyOneActive() throws Exception {
        Fixture fixture = createInvitedApplication();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Throwable[] results = runConcurrently(
                    executor,
                    () -> invitationService.resendInvitation(fixture.ownerId(), fixture.applicationId(), request(3)),
                    () -> invitationService.resendInvitation(fixture.ownerId(), fixture.applicationId(), request(4)));

            assertThat(results[0]).isNull();
            assertThat(results[1]).isNull();
        } finally {
            executor.shutdownNow();
        }

        assertThat(invitationCount(fixture.applicationId())).isEqualTo(3);
        assertThat(activeInvitationCount(fixture.applicationId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ? AND superseded_at IS NOT NULL",
                        Integer.class,
                        fixture.applicationId()))
                .isEqualTo(2);
        assertThat(resentNotificationCount(fixture.candidateId())).isEqualTo(2);
        assertThat(statusOf(fixture.applicationId())).isEqualTo("INTERVIEW_INVITED");
    }

    // (b) Gui lai song song voi Trung tuyen, 10 lan tren 10 don: {gui lai OK, HIRED OK} hoac {HIRED OK, gui lai 409}.
    @Test
    void t10b_resendParallelWithHired_tenRounds_onlyAllowedOutcomes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Fixture fixture = createInvitedApplication();

                Throwable[] results = runConcurrently(
                        executor,
                        () -> invitationService.resendInvitation(fixture.ownerId(), fixture.applicationId(), request(3)),
                        () -> applicationStatusService.changeStatus(
                                fixture.ownerId(), fixture.applicationId(), ApplicationStatus.HIRED));

                Throwable resend = results[0];
                assertThat(results[1]).as("HIRED vong %d", round).isNull();
                if (resend != null) {
                    assertThat(resend).as("gui lai vong %d", round).isInstanceOf(InvitationResendNotAllowedException.class);
                    assertThat(invitationCount(fixture.applicationId())).isEqualTo(1);
                } else {
                    assertThat(invitationCount(fixture.applicationId())).isEqualTo(2);
                }
                assertThat(statusOf(fixture.applicationId())).isEqualTo("HIRED");
                assertThat(activeInvitationCount(fixture.applicationId())).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // (c) Nhu (b) voi rut don phia ung vien: {gui lai OK, rut OK} hoac {rut OK, gui lai 409}.
    @Test
    void t10c_resendParallelWithWithdraw_tenRounds_onlyAllowedOutcomes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Fixture fixture = createInvitedApplication();

                Throwable[] results = runConcurrently(
                        executor,
                        () -> invitationService.resendInvitation(fixture.ownerId(), fixture.applicationId(), request(3)),
                        () -> applicationService.withdraw(fixture.candidateId(), fixture.applicationId()));

                Throwable resend = results[0];
                assertThat(results[1]).as("rut don vong %d", round).isNull();
                if (resend != null) {
                    assertThat(resend).as("gui lai vong %d", round).isInstanceOf(InvitationResendNotAllowedException.class);
                    assertThat(invitationCount(fixture.applicationId())).isEqualTo(1);
                } else {
                    assertThat(invitationCount(fixture.applicationId())).isEqualTo(2);
                }
                assertThat(statusOf(fixture.applicationId())).isEqualTo("WITHDRAWN");
                assertThat(activeInvitationCount(fixture.applicationId())).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // (d) Don luong, chac chan (S3): A giu khoa hang don (lockIfInterviewInvited, chua commit) -> updateStatusIfCurrent cua B
    // bi chan qua 500 ms; A commit -> B xong trong 5 s, tra 1, don HIRED. Phan biet duoc cach "doc trang thai roi ghi,
    // khong khoa" ma (b) khong phan biet duoc.
    @Test
    void t10d_lockHeldByA_blocksHiredUpdateOfB_untilACommits() throws Exception {
        Fixture fixture = createInvitedApplication();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<Integer>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(jobApplicationRepository.lockIfInterviewInvited(fixture.applicationId())).isEqualTo(1);
                futureB.set(executor.submit(() -> new TransactionTemplate(transactionManager)
                        .execute(s -> jobApplicationRepository.updateStatusIfCurrent(
                                fixture.applicationId(), ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.HIRED))));
                assertThrows(TimeoutException.class, () -> futureB.get().get(500, TimeUnit.MILLISECONDS));
            });

            assertThat(futureB.get().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(statusOf(fixture.applicationId())).isEqualTo("HIRED");
    }

    // (d) ca nguoc: B (HIRED) commit truoc -> A lockIfInterviewInvited tra 0.
    @Test
    void t10d_hiredCommittedFirst_lockReturnsZero() {
        Fixture fixture = createInvitedApplication();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Integer hired = tx.execute(s -> jobApplicationRepository.updateStatusIfCurrent(
                fixture.applicationId(), ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.HIRED));
        Integer locked = tx.execute(s -> jobApplicationRepository.lockIfInterviewInvited(fixture.applicationId()));

        assertThat(hired).isEqualTo(1);
        assertThat(locked).isZero();
        assertThat(statusOf(fixture.applicationId())).isEqualTo("HIRED");
    }
}
