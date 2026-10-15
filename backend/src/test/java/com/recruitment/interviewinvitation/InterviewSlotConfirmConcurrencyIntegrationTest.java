package com.recruitment.interviewinvitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.exception.InterviewInvitationConflictException;
import com.recruitment.common.exception.InterviewSlotAlreadyConfirmedException;
import com.recruitment.common.exception.SlotSelectionNotAllowedException;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

// FR-U10 T6-T8 (R-R) - chon khung tuan tu hoa voi chon khung / gui lai / Trung tuyen / Tu choi / Rut don bang khoa hang don
// (lockIfInterviewInvited). KHONG @Transactional toan lop (CLAUDE.md muc 3c): hai luong phai thay du lieu da commit cua
// nhau. Goi thang service (khuon InterviewInvitationResendConcurrencyIntegrationTest): "200" = method tra ve binh thuong,
// "409" = dung loai exception mong doi, "khong 500" = khong exception nao khac. Ca don luong xac nhan "B dang bi khoa
// chan" bang pg_stat_activity (khuon ScreeningCaptureConcurrencyIntegrationTest, REQUIREMENT muc 13 L4), khong cho co
// dinh theo thoi gian. Du lieu ngau nhien rieng, chi khang dinh tren don cua test, khong don sau test.
// So thong bao INTERVIEW_SLOT_CONFIRMED (REQUIREMENT muc 13 L18) dem NGAY sau khi cac luong xong, khong vong tham: listener
// AFTER_COMMIT chay dong bo tren luong commit truoc khi method service tra ve (L3).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class InterviewSlotConfirmConcurrencyIntegrationTest {

    private static final int ROUNDS = 10;
    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;
    private static final long LOCK_POLL_INTERVAL_MS = 50;

    @Autowired
    private InterviewInvitationService invitationService;

    @Autowired
    private InterviewInvitationRepository invitationRepository;

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

    // slots = id khung cua giay moi hieu luc theo display_order.
    private record Fixture(UUID ownerId, UUID candidateId, UUID applicationId, List<UUID> slots) {
    }

    // Don da INTERVIEW_INVITED qua I2 that voi 3 khung tuong lai, CHUA chot.
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

        invitationService.sendInvitation(hr.getId(), application.getId(), request(3, 3));
        return new Fixture(hr.getId(), candidate.getId(), application.getId(), activeSlotIds(application.getId()));
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("u10c-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    // count khung tuong lai, cach nhau mot gio, bat dau sau daysAhead ngay.
    private static InterviewInvitationSendRequest request(int count, int daysAhead) {
        Instant first = Instant.now().plus(daysAhead, ChronoUnit.DAYS);
        List<SlotRequest> slots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(new SlotRequest(first.plus(i, ChronoUnit.HOURS), "Phong " + (i + 1)));
        }
        return new InterviewInvitationSendRequest(slots, "Thu moi", "Noi dung");
    }

    private List<UUID> activeSlotIds(UUID applicationId) {
        return jdbcTemplate.queryForList(
                """
                SELECT s.id FROM interview_invitation_slots s
                JOIN interview_invitations i ON i.id = s.invitation_id
                WHERE i.application_id = ? AND i.superseded_at IS NULL ORDER BY s.display_order
                """,
                UUID.class,
                applicationId);
    }

    private UUID activeConfirmedSlot(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT confirmed_slot_id FROM interview_invitations WHERE application_id = ? AND superseded_at IS NULL",
                UUID.class,
                applicationId);
    }

    // Khung chot cua giay moi chua slotId (giay moi do co the da bi thay).
    private UUID confirmedSlotOfInvitationContaining(UUID slotId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT i.confirmed_slot_id FROM interview_invitations i
                JOIN interview_invitation_slots s ON s.invitation_id = i.id
                WHERE s.id = ?
                """,
                UUID.class,
                slotId);
    }

    private int activeInvitationCount(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_invitations WHERE application_id = ? AND superseded_at IS NULL",
                Integer.class,
                applicationId);
    }

    // Thong bao "ung vien da chon lich" cua don, gui cho chu cong ty (R-N2).
    private int slotConfirmedNotificationCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM notifications
                WHERE type = 'INTERVIEW_SLOT_CONFIRMED' AND entity_id = ? AND user_id = ?
                """,
                Integer.class,
                fixture.applicationId(),
                fixture.ownerId());
    }

    private int notificationCountOfApplication(Fixture fixture, String type) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE type = ? AND entity_id = ?",
                Integer.class,
                type,
                fixture.applicationId());
    }

    private String statusOf(UUID applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM job_applications WHERE id = ?", String.class, applicationId);
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

    // L4 - cho toi khi MOT phien KHAC (pid <> pg_backend_pid()) dang doi khoa tren cau lockIfInterviewInvited
    // ("SET status = status") cua confirmSlot. Ham chay TRONG transaction dang giu khoa: Postgres chup pg_stat_activity lan
    // dau truy cap va giu toi het transaction, nen PHAI pg_stat_clear_snapshot() truoc moi lan tham do (U09 L9). Het gio -> do.
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
                      AND query ILIKE '%SET status = status%'
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
        fail("Phien " + who + " khong bi khoa chan sau " + LOCK_WAIT_TIMEOUT_MS + " ms - khoa hang don khong hoat dong");
    }

    // ---- T6 - hai luot chon song song cung don ----

    @Test
    void t6a_twoParallelConfirmsSameSlot_tenRounds_exactlyOneWins() throws Exception {
        assertExactlyOneConfirmWins(false);
    }

    @Test
    void t6b_twoParallelConfirmsDifferentSlots_tenRounds_exactlyOneWins() throws Exception {
        assertExactlyOneConfirmWins(true);
    }

    private void assertExactlyOneConfirmWins(boolean differentSlots) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Fixture fixture = createInvitedApplication();
                UUID first = fixture.slots().get(0);
                UUID second = differentSlots ? fixture.slots().get(1) : first;

                Throwable[] results = runConcurrently(
                        executor,
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), first),
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), second));

                assertThat(results[0] == null ^ results[1] == null)
                        .as("vong %d: dung mot luot thanh cong - %s / %s", round, results[0], results[1])
                        .isTrue();
                Throwable loser = results[0] != null ? results[0] : results[1];
                assertThat(loser).as("vong %d", round).isInstanceOf(InterviewSlotAlreadyConfirmedException.class);
                UUID winnerSlot = results[0] == null ? first : second;
                assertThat(activeConfirmedSlot(fixture.applicationId())).as("vong %d", round).isEqualTo(winnerSlot);
                assertThat(statusOf(fixture.applicationId())).isEqualTo("INTERVIEW_INVITED");
                // T6 (L18): dung MOT thong bao cho chu cong ty - luot 409 khong tao them; tong cua don cung la 1.
                assertThat(slotConfirmedNotificationCount(fixture)).as("thong bao vong %d", round).isEqualTo(1);
                assertThat(notificationCountOfApplication(fixture, "INTERVIEW_SLOT_CONFIRMED"))
                        .as("thong bao vong %d", round)
                        .isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- T7 - chon khung song song voi gui lai ----

    // {chon OK, gui lai OK: giay moi cu giu khung chot} hoac {gui lai OK, chon 409 INTERVIEW_INVITATION_CONFLICT}.
    @Test
    void t7_confirmParallelWithResend_tenRounds_onlyAllowedOutcomes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Fixture fixture = createInvitedApplication();
                UUID chosen = fixture.slots().get(1);

                Throwable[] results = runConcurrently(
                        executor,
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), chosen),
                        () -> invitationService.resendInvitation(fixture.ownerId(), fixture.applicationId(), request(2, 10)));

                Throwable confirm = results[0];
                assertThat(results[1]).as("gui lai vong %d", round).isNull();
                if (confirm == null) {
                    // Chon truoc: giay moi cu (da bi thay) giu khung chot (H12 R-G5).
                    assertThat(confirmedSlotOfInvitationContaining(chosen)).as("vong %d", round).isEqualTo(chosen);
                } else {
                    assertThat(confirm).as("chon vong %d", round).isInstanceOf(InterviewInvitationConflictException.class);
                    assertThat(confirmedSlotOfInvitationContaining(chosen)).as("vong %d", round).isNull();
                }
                assertThat(activeConfirmedSlot(fixture.applicationId())).as("giay moi moi chua chot").isNull();
                assertThat(activeInvitationCount(fixture.applicationId())).isEqualTo(1);
                assertThat(statusOf(fixture.applicationId())).isEqualTo("INTERVIEW_INVITED");
                // T7 (L18, R-R): HR nhan thong bao "da chon" chi khi luot chon thanh cong; ung vien luon nhan dung mot
                // thong bao gui lai.
                assertThat(slotConfirmedNotificationCount(fixture))
                        .as("thong bao chon vong %d", round)
                        .isEqualTo(confirm == null ? 1 : 0);
                assertThat(notificationCountOfApplication(fixture, "INTERVIEW_SLOT_CONFIRMED"))
                        .as("thong bao chon vong %d", round)
                        .isEqualTo(confirm == null ? 1 : 0);
                assertThat(notificationCountOfApplication(fixture, "INTERVIEW_INVITATION_RESENT"))
                        .as("thong bao gui lai vong %d", round)
                        .isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // Don luong, chac chan (L4): A giu khoa hang don (chua commit) -> confirmSlot cua B bi chan (pg_stat_activity); A danh
    // dau giay moi hien tai la cu + chen giay moi moi 2 khung CHUA chot roi commit -> B tra INTERVIEW_INVITATION_CONFLICT.
    @Test
    void t7_lockHeldByA_resendCommitted_confirmOfBBlockedThenConflict() throws Exception {
        Fixture fixture = createInvitedApplication();
        UUID chosen = fixture.slots().get(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(jobApplicationRepository.lockIfInterviewInvited(fixture.applicationId())).isEqualTo(1);
                futureB.set(executor.submit(
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), chosen)));
                awaitOtherSessionBlockedOnLock("chon khung");
                assertThat(futureB.get().isDone()).isFalse();

                Instant now = Instant.now();
                invitationRepository.markActiveSuperseded(fixture.applicationId(), now);
                insertUnconfirmedInvitation(fixture, now);
            });

            ExecutionException thrown =
                    assertThrows(ExecutionException.class, () -> futureB.get().get(5, TimeUnit.SECONDS));
            assertThat(thrown.getCause()).isInstanceOf(InterviewInvitationConflictException.class);
        } finally {
            executor.shutdownNow();
        }
        assertThat(confirmedSlotOfInvitationContaining(chosen)).isNull();
        assertThat(activeConfirmedSlot(fixture.applicationId())).isNull();
        assertThat(activeInvitationCount(fixture.applicationId())).isEqualTo(1);
        assertThat(notificationCountOfApplication(fixture, "INTERVIEW_SLOT_CONFIRMED")).isZero();
    }

    // Ca nguoc: A chi khoa roi commit khong doi gi -> B duoc nhan khoa, chon thanh cong.
    @Test
    void t7_lockHeldByA_commitWithoutChange_confirmOfBBlockedThenSucceeds() throws Exception {
        Fixture fixture = createInvitedApplication();
        UUID chosen = fixture.slots().get(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(jobApplicationRepository.lockIfInterviewInvited(fixture.applicationId())).isEqualTo(1);
                futureB.set(executor.submit(
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), chosen)));
                awaitOtherSessionBlockedOnLock("chon khung");
                assertThat(futureB.get().isDone()).isFalse();
            });

            futureB.get().get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeConfirmedSlot(fixture.applicationId())).isEqualTo(chosen);
        assertThat(slotConfirmedNotificationCount(fixture)).isEqualTo(1);
    }

    // Giay moi moi 2 khung CHUA chot (helper SQL, REQUIREMENT T7) - giay moi 1 khung tu chot se lam B nhan
    // INTERVIEW_SLOT_ALREADY_CONFIRMED (R-W5 dong 2) thay vi INTERVIEW_INVITATION_CONFLICT.
    private void insertUnconfirmedInvitation(Fixture fixture, Instant sentAt) {
        UUID invitationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO interview_invitations (id, application_id, subject, rendered_content, sent_at, sent_by)
                VALUES (?, ?, 'Thu moi gui lai', 'Noi dung gui lai', ?, ?)
                """,
                invitationId,
                fixture.applicationId(),
                Timestamp.from(sentAt),
                fixture.ownerId());
        Instant first = Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        for (int i = 0; i < 2; i++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO interview_invitation_slots (id, invitation_id, display_order, scheduled_at, location)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    invitationId,
                    i + 1,
                    Timestamp.from(first.plus(i, ChronoUnit.HOURS)),
                    "Phong moi " + (i + 1));
        }
    }

    // ---- T8 - chon khung song song voi Trung tuyen / Tu choi / Rut don ----

    @Test
    void t8a_confirmParallelWithHired_tenRounds_onlyAllowedOutcomes() throws Exception {
        assertConfirmParallelWithFinalStatus(ApplicationStatus.HIRED);
    }

    @Test
    void t8b_confirmParallelWithRejected_tenRounds_onlyAllowedOutcomes() throws Exception {
        assertConfirmParallelWithFinalStatus(ApplicationStatus.REJECTED);
    }

    // {chon OK, doi trang thai OK} hoac {doi trang thai OK, chon 409 SLOT_SELECTION_NOT_ALLOWED}.
    private void assertConfirmParallelWithFinalStatus(ApplicationStatus finalStatus) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Fixture fixture = createInvitedApplication();
                UUID chosen = fixture.slots().get(0);

                Throwable[] results = runConcurrently(
                        executor,
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), chosen),
                        () -> applicationStatusService.changeStatus(fixture.ownerId(), fixture.applicationId(), finalStatus));

                Throwable confirm = results[0];
                assertThat(results[1]).as("doi trang thai vong %d", round).isNull();
                if (confirm == null) {
                    assertThat(activeConfirmedSlot(fixture.applicationId())).isEqualTo(chosen);
                } else {
                    assertThat(confirm).as("chon vong %d", round).isInstanceOf(SlotSelectionNotAllowedException.class);
                    assertThat(activeConfirmedSlot(fixture.applicationId())).isNull();
                }
                assertThat(statusOf(fixture.applicationId())).isEqualTo(finalStatus.name());
                assertThat(slotConfirmedNotificationCount(fixture))
                        .as("thong bao chon vong %d", round)
                        .isEqualTo(confirm == null ? 1 : 0);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // {chon OK, rut OK} hoac {rut OK, chon 409 SLOT_SELECTION_NOT_ALLOWED}.
    @Test
    void t8c_confirmParallelWithWithdraw_tenRounds_onlyAllowedOutcomes() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Fixture fixture = createInvitedApplication();
                UUID chosen = fixture.slots().get(0);

                Throwable[] results = runConcurrently(
                        executor,
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), chosen),
                        () -> applicationService.withdraw(fixture.candidateId(), fixture.applicationId()));

                Throwable confirm = results[0];
                assertThat(results[1]).as("rut don vong %d", round).isNull();
                if (confirm == null) {
                    assertThat(activeConfirmedSlot(fixture.applicationId())).isEqualTo(chosen);
                } else {
                    assertThat(confirm).as("chon vong %d", round).isInstanceOf(SlotSelectionNotAllowedException.class);
                    assertThat(activeConfirmedSlot(fixture.applicationId())).isNull();
                }
                assertThat(statusOf(fixture.applicationId())).isEqualTo("WITHDRAWN");
                assertThat(slotConfirmedNotificationCount(fixture))
                        .as("thong bao chon vong %d", round)
                        .isEqualTo(confirm == null ? 1 : 0);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    // Don luong chieu THUAN (L5): A khoa + chuyen HIRED trong cung transaction (chua commit) -> confirmSlot cua B bi chan;
    // A commit -> B danh gia lai WHERE, thay HIRED -> 409 SLOT_SELECTION_NOT_ALLOWED.
    @Test
    void t8_lockAndHiredByA_confirmOfBBlockedThenNotAllowed() throws Exception {
        Fixture fixture = createInvitedApplication();
        UUID chosen = fixture.slots().get(0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(jobApplicationRepository.lockIfInterviewInvited(fixture.applicationId())).isEqualTo(1);
                assertThat(jobApplicationRepository.updateStatusIfCurrent(
                                fixture.applicationId(), ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.HIRED))
                        .isEqualTo(1);
                futureB.set(executor.submit(
                        () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), chosen)));
                awaitOtherSessionBlockedOnLock("chon khung");
                assertThat(futureB.get().isDone()).isFalse();
            });

            ExecutionException thrown =
                    assertThrows(ExecutionException.class, () -> futureB.get().get(5, TimeUnit.SECONDS));
            assertThat(thrown.getCause()).isInstanceOf(SlotSelectionNotAllowedException.class);
        } finally {
            executor.shutdownNow();
        }
        assertThat(statusOf(fixture.applicationId())).isEqualTo("HIRED");
        assertThat(activeConfirmedSlot(fixture.applicationId())).isNull();
        assertThat(notificationCountOfApplication(fixture, "INTERVIEW_SLOT_CONFIRMED")).isZero();
    }

    // Ca NGUOC: HIRED commit truoc -> confirmSlot 409 SLOT_SELECTION_NOT_ALLOWED.
    @Test
    void t8_hiredCommittedFirst_confirmReturnsNotAllowed() {
        Fixture fixture = createInvitedApplication();
        Integer hired = new TransactionTemplate(transactionManager)
                .execute(s -> jobApplicationRepository.updateStatusIfCurrent(
                        fixture.applicationId(), ApplicationStatus.INTERVIEW_INVITED, ApplicationStatus.HIRED));

        assertThat(hired).isEqualTo(1);
        assertThrows(
                SlotSelectionNotAllowedException.class,
                () -> invitationService.confirmSlot(fixture.candidateId(), fixture.applicationId(), fixture.slots().get(0)));
        assertThat(activeConfirmedSlot(fixture.applicationId())).isNull();
        assertThat(notificationCountOfApplication(fixture, "INTERVIEW_SLOT_CONFIRMED")).isZero();
    }
}
