package com.recruitment.talentpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.exception.TalentPoolConsentRequiredException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.talentpool.dto.ApplicationTalentPoolsRequest;
import com.recruitment.talentpool.dto.ApplicationTalentPoolsResponse;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.util.List;
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

// FR-H15 T16 (R-C2, L1). KHONG @Transactional toan lop (CLAUDE.md muc 3c): hai luong phai thay du lieu da commit cua
// nhau. Goi thang service (khuon InterviewSlotConfirmConcurrencyIntegrationTest): "200" = method tra ve binh thuong,
// "409" = TalentPoolConsentRequiredException. Luong A giu khoa trong TransactionTemplate; "B dang bi chan" xac nhan bang
// pg_stat_activity (khong cho co dinh theo thoi gian). Du lieu ngau nhien rieng, khong don sau test.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ApplicationTalentPoolConcurrencyIntegrationTest {

    private static final long LOCK_WAIT_TIMEOUT_MS = 10_000;
    private static final long LOCK_POLL_INTERVAL_MS = 50;

    @Autowired
    private ApplicationTalentPoolService service;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

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

    private record Fixture(UUID ownerId, UUID candidateId, UUID applicationId, UUID poolId) {
    }

    // Don ACTIVE cua mot cong ty moi, kem mot kho chua chua don.
    private Fixture fixture() {
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

        // talent_pool_consent_at backdate tuong minh (CLAUDE.md muc 3c): T16 rut dong y bang Instant.now() cua JVM, con
        // now() la dong ho Postgres trong container - lech dong ho giua hai ben lam revoked_at < consent_at, vi pham
        // chk_talent_pool_consent_state (V14).
        UUID applicationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications
                    (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at,
                     talent_pool_consent, talent_pool_consent_at)
                VALUES (?, ?, ?, ?, 1, 'PENDING', TRUE, now(), TRUE, now() - interval '1 minute')
                """,
                applicationId, job.getId(), candidate.getId(), resume.getId());
        UUID poolId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO talent_pools (id, company_id, name) VALUES (?, ?, ?)", poolId, company.getId(), "Kho " + poolId);
        return new Fixture(hr.getId(), candidate.getId(), applicationId, poolId);
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("h15c-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private ApplicationTalentPoolsResponse replace(Fixture fixture) {
        return service.replace(
                fixture.ownerId(), fixture.applicationId(), new ApplicationTalentPoolsRequest(List.of(fixture.poolId())));
    }

    private int activeEntryCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_pool_entries WHERE pool_id = ? AND application_id = ? AND removed_at IS NULL",
                Integer.class, fixture.poolId(), fixture.applicationId());
    }

    // Cho toi khi CO phien khac (khong phai phien goi ham) dang doi khoa ma cau dang chay khop ca hai mau. Ham chay TRONG
    // transaction dang giu khoa: Postgres chup pg_stat_activity lan dau truy cap va giu toi het transaction, nen PHAI
    // pg_stat_clear_snapshot() truoc moi lan tham do (U09 L9). Het gio -> do.
    private void awaitOtherSessionBlocked(String queryPattern1, String queryPattern2, String who) {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
            Integer waiting = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database()
                      AND pid <> pg_backend_pid()
                      AND wait_event_type = 'Lock'
                      AND query ILIKE ? AND query ILIKE ?
                    """,
                    Integer.class, queryPattern1, queryPattern2);
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
        fail("Phien " + who + " khong bi khoa chan sau " + LOCK_WAIT_TIMEOUT_MS + "ms");
    }

    // ---- T16 (R-C2) ----

    // A rut dong y (UPDATE giu khoa hang don, chua commit) -> P7 cua B bi chan o cau FOR SHARE; A commit -> B doc gia tri
    // moi, 409, khong muc nao hieu luc.
    @Test
    void t16_revokeHeldByA_addOfBBlockedOnForShare_thenConsentRequired() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<ApplicationTalentPoolsResponse>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(jobApplicationRepository.revokeTalentPoolConsentIfActive(
                                fixture.applicationId(), fixture.candidateId(), Instant.now()))
                        .isEqualTo(1);
                futureB.set(executor.submit(() -> replace(fixture)));
                awaitOtherSessionBlocked("%FOR SHARE%", "%job_applications%", "P7");
                assertThat(futureB.get().isDone()).isFalse();
            });

            ExecutionException thrown =
                    assertThrows(ExecutionException.class, () -> futureB.get().get(5, TimeUnit.SECONDS));
            assertThat(thrown.getCause()).isInstanceOf(TalentPoolConsentRequiredException.class);
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeEntryCount(fixture)).isZero();
    }

    // Ca nguoc: A chi khoa hang don roi commit khong doi gi -> B duoc nhan khoa, them thanh cong.
    @Test
    void t16_lockHeldByA_commitWithoutChange_addOfBBlockedThenSucceeds() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<ApplicationTalentPoolsResponse>> futureB = new AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM job_applications WHERE id = ? FOR UPDATE", UUID.class, fixture.applicationId());
                futureB.set(executor.submit(() -> replace(fixture)));
                awaitOtherSessionBlocked("%FOR SHARE%", "%job_applications%", "P7");
                assertThat(futureB.get().isDone()).isFalse();
            });

            ApplicationTalentPoolsResponse response = futureB.get().get(5, TimeUnit.SECONDS);
            assertThat(response.pools()).singleElement().satisfies(pool -> assertThat(pool.contains()).isTrue());
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeEntryCount(fixture)).isEqualTo(1);
    }

    // ---- T16 ca them (L1): hai P7 dong thoi cung them mot (kho, don) ----
    // Chac chan, khong phu thuoc may rui: A goi P7 TRONG TransactionTemplate (service tham gia transaction cua A) - da them
    // muc nhung CHUA commit. B goi P7: khoa FOR SHARE cua A khong chan B, B thay "hien co" rong (chua thay muc cua A) va
    // INSERT ... ON CONFLICT phai CHO dong chua commit cua A tren uq_talent_pool_entry_active (pg_stat_activity). A
    // commit -> ON CONFLICT DO NOTHING -> B tra ve binh thuong. Neu cau them la INSERT thuong, B nem vi pham unique.
    @Test
    void t16_l1_twoConcurrentAddsOfSameEntry_bothSucceed_exactlyOneActiveRow() throws Exception {
        Fixture fixture = fixture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<ApplicationTalentPoolsResponse>> futureB = new AtomicReference<>();
        try {
            ApplicationTalentPoolsResponse responseA =
                    new TransactionTemplate(transactionManager).execute(status -> {
                        ApplicationTalentPoolsResponse response = replace(fixture);
                        futureB.set(executor.submit(() -> replace(fixture)));
                        awaitOtherSessionBlocked("%INSERT INTO talent_pool_entries%", "%VALUES%", "P7 thu hai");
                        assertThat(futureB.get().isDone()).isFalse();
                        return response;
                    });

            ApplicationTalentPoolsResponse responseB = futureB.get().get(5, TimeUnit.SECONDS);
            assertThat(responseA.pools()).singleElement().satisfies(pool -> assertThat(pool.contains()).isTrue());
            assertThat(responseB.pools()).singleElement().satisfies(pool -> assertThat(pool.contains()).isTrue());
        } finally {
            executor.shutdownNow();
        }
        assertThat(activeEntryCount(fixture)).isEqualTo(1);
    }
}
