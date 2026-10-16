package com.recruitment.talentpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.common.exception.ErrorResponse;
import com.recruitment.common.exception.GlobalExceptionHandler;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// FR-H15 T3 (L2) + rang buoc V17, insert thang bang JdbcTemplate (khong qua service). KHONG @Transactional: Postgres huy
// ca transaction sau cau lenh loi dau tien; moi cau tu commit, du lieu ngau nhien rieng, khong don (L2 cua FR-H10).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TalentPoolSchemaConstraintIntegrationTest {

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
    private GlobalExceptionHandler globalExceptionHandler;

    @Autowired
    private TalentPoolEntryRepository talentPoolEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID companyId;
    private UUID applicationId;

    @BeforeEach
    void createCompanyAndApplication() {
        User hr = user(Role.HR);
        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        companyId = companyRepository.save(company).getId();

        Job job = new Job();
        job.setCompanyId(companyId);
        job.setCreatedBy(hr.getId());
        job.setTitle("Nhan vien CSKH");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        UUID jobId = jobRepository.save(job).getId();

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

        applicationId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO job_applications
                    (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent, ai_consent_at,
                     talent_pool_consent, talent_pool_consent_at)
                VALUES (?, ?, ?, ?, 1, 'PENDING', TRUE, now(), TRUE, now())
                """,
                applicationId, jobId, candidate.getId(), resumeId);
    }

    private User user(Role role) {
        User user = new User();
        user.setEmail("h15s-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        user.setRole(role);
        user.setFullName("Nguoi Dung Test");
        return userRepository.save(user);
    }

    private UUID insertPool(UUID company, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO talent_pools (id, company_id, name) VALUES (?, ?, ?)", id, company, name);
        return id;
    }

    private static DataIntegrityViolationException expectViolation(Runnable action) {
        try {
            action.run();
        } catch (DataIntegrityViolationException e) {
            return e;
        }
        throw new AssertionError("Mong doi DataIntegrityViolationException");
    }

    // insertIfAbsent la @Modifying - can transaction; moi lan goi tu commit.
    private int insertIfAbsent(UUID poolId) {
        Integer result = new TransactionTemplate(transactionManager)
                .execute(status -> talentPoolEntryRepository.insertIfAbsent(poolId, applicationId));
        return result == null ? -1 : result;
    }

    private UUID otherCompany() {
        Company company = new Company();
        company.setOwnerId(user(Role.HR).getId());
        company.setName("Cong ty Khac " + UUID.randomUUID());
        return companyRepository.save(company).getId();
    }

    // ---- T3 (L2) ----

    @Test
    void t3_duplicateNameIgnoringCase_violatesUniqueIndex_handlerMapsTo409() {
        insertPool(companyId, "Backend tiềm năng");

        // (a) JDBC -> exception mang ten constraint.
        DataIntegrityViolationException thrown = expectViolation(() -> insertPool(companyId, "BACKEND TIỀM NĂNG"));
        assertThat(thrown.getMostSpecificCause().getMessage()).contains("uq_talent_pool_name_per_company");

        // (b) chinh exception do qua handler -> 409 TALENT_POOL_NAME_TAKEN.
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleDataIntegrityViolation(thrown);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(
                new ErrorResponse("TALENT_POOL_NAME_TAKEN", "Công ty đã có kho ứng viên với tên này."));
    }

    @Test
    void t3_sameNameAllowed_inOtherCompany_or_afterSoftDelete() {
        UUID first = insertPool(companyId, "Thực tập 2027");

        assertThat(insertPool(otherCompany(), "Thực tập 2027")).as("cong ty khac").isNotNull();

        jdbcTemplate.update("UPDATE talent_pools SET deleted_at = now() WHERE id = ?", first);
        assertThat(insertPool(companyId, "thực tập 2027")).as("kho cu da xoa mem").isNotNull();
    }

    @Test
    void t3_nameLengthCheck_zeroAnd101Rejected_100Accepted() {
        assertThatThrownBy(() -> insertPool(companyId, "")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(insertPool(companyId, "x".repeat(100))).isNotNull();
        // VARCHAR(100) chan truoc CHECK -> loi khac loai nhung van la DataIntegrityViolationException.
        assertThatThrownBy(() -> insertPool(companyId, "y".repeat(101)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- Rang buoc bang muc ----

    @Test
    void entry_secondActiveForSamePoolAndApplication_violatesUniqueIndex_afterRemoveAllowed() {
        UUID poolId = insertPool(companyId, "Kho " + UUID.randomUUID());
        jdbcTemplate.update(
                "INSERT INTO talent_pool_entries (pool_id, application_id) VALUES (?, ?)", poolId, applicationId);

        DataIntegrityViolationException thrown = expectViolation(() -> jdbcTemplate.update(
                "INSERT INTO talent_pool_entries (pool_id, application_id) VALUES (?, ?)", poolId, applicationId));
        assertThat(thrown.getMostSpecificCause().getMessage()).contains("uq_talent_pool_entry_active");

        jdbcTemplate.update(
                "UPDATE talent_pool_entries SET removed_at = now() WHERE pool_id = ? AND application_id = ?",
                poolId, applicationId);
        assertThat(jdbcTemplate.update(
                        "INSERT INTO talent_pool_entries (pool_id, application_id) VALUES (?, ?)", poolId, applicationId))
                .as("them lai sau khi go -> dong moi")
                .isEqualTo(1);
    }

    @Test
    void entry_insertIfAbsent_isIdempotent_onPartialUniqueIndex() {
        UUID poolId = insertPool(companyId, "Kho " + UUID.randomUUID());

        assertThat(insertIfAbsent(poolId)).isEqualTo(1);
        assertThat(insertIfAbsent(poolId)).as("ON CONFLICT DO NOTHING").isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM talent_pool_entries WHERE pool_id = ? AND application_id = ?",
                        Integer.class, poolId, applicationId))
                .isEqualTo(1);
    }

    @Test
    void entry_removedBeforeAdded_rejectedByCheck() {
        UUID poolId = insertPool(companyId, "Kho " + UUID.randomUUID());
        Instant addedAt = Instant.parse("2026-10-16T08:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO talent_pool_entries (pool_id, application_id, added_at, removed_at) VALUES (?, ?, ?, ?)",
                        poolId, applicationId, java.sql.Timestamp.from(addedAt),
                        java.sql.Timestamp.from(addedAt.minusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.update(
                        "INSERT INTO talent_pool_entries (pool_id, application_id, added_at, removed_at) VALUES (?, ?, ?, ?)",
                        poolId, applicationId, java.sql.Timestamp.from(addedAt), java.sql.Timestamp.from(addedAt)))
                .as("removed_at = added_at hop le")
                .isEqualTo(1);
    }

    // L4 - transaction go bat dau (t0) TRUOC khi luong khac them muc (added_at = t1 > t0) va commit; cau go chay sau trong
    // chinh transaction cu. removed_at = statement_timestamp() >= t1 -> thoa CHECK. Neu cau go dung now() (= t0) thi vo
    // CHECK removed_at >= added_at va test nay do.
    @Test
    void l4_removeInTransactionStartedBeforeEntryWasAdded_satisfiesCheck() throws Exception {
        UUID poolId = insertPool(companyId, "Kho " + UUID.randomUUID());
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Integer removed = new TransactionTemplate(transactionManager).execute(status -> {
                // Chot now() cua transaction nay (Postgres lay moc o cau lenh dau tien).
                jdbcTemplate.queryForObject("SELECT now()", java.sql.Timestamp.class);
                try {
                    Thread.sleep(20);
                    executor.submit(() -> jdbcTemplate.update(
                                    "INSERT INTO talent_pool_entries (pool_id, application_id) VALUES (?, ?)",
                                    poolId, applicationId))
                            .get(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return talentPoolEntryRepository.removeAllActiveByApplicationId(applicationId);
            });
            assertThat(removed).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM talent_pool_entries WHERE application_id = ? AND removed_at >= added_at",
                        Integer.class, applicationId))
                .isEqualTo(1);
    }

    @Test
    void pool_updatedAt_setByTrigger() {
        UUID poolId = insertPool(companyId, "Kho " + UUID.randomUUID());
        jdbcTemplate.update("UPDATE talent_pools SET updated_at = '2000-01-01T00:00:00Z' WHERE id = ?", poolId);
        Instant updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM talent_pools WHERE id = ?", java.sql.Timestamp.class, poolId).toInstant();
        assertThat(updatedAt).as("trigger ghi de bang now()").isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }
}
