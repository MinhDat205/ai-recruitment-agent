package com.recruitment.reconsideration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// FR-C09 T23 (V21, REQUIREMENT muc 4.1). SQL thang qua JdbcTemplate de kiem rang buoc o DB (UNIQUE, CHECK, FK RESTRICT);
// mot ca doc qua entity; mot ca chay thu moi cau native cua ReconsiderationRequestRepository tren Postgres that (cau native
// khong duoc kiem luc khoi dong). Moi ca tu tao du lieu rieng (UUID ngau nhien). Khong @Transactional: moi cau tu commit,
// loi rang buoc khong lam hong ca test khac (khuon SavedJobMigrationTest).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ReconsiderationMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ReconsiderationRequestRepository repository;

    private record Fixture(UUID hrId, UUID candidateId, UUID jobId, UUID applicationId) {}

    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, full_name) VALUES (?, ?, 'x', ?, 'C09 V21')",
                id,
                "c09-v21-" + id + "@test.local",
                role);
        return id;
    }

    // Don REJECTED o vong ho so: lich su NULL -> PENDING, PENDING -> REJECTED (changed_at = now() cua cau INSERT).
    private Fixture createRejectedApplication() {
        UUID hrId = createUser("HR");
        UUID candidateId = createUser("CANDIDATE");
        UUID companyId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, owner_id, name) VALUES (?, ?, 'Cong ty C09')", companyId, hrId);
        jdbcTemplate.update(
                "INSERT INTO jobs (id, company_id, created_by, title, description) VALUES (?, ?, ?, 'Tin C09', 'Mo ta')",
                jobId,
                companyId,
                hrId);
        jdbcTemplate.update(
                "INSERT INTO resumes (id, candidate_id, file_url, file_name, file_type) VALUES (?, ?, 'x', 'cv.pdf', 'PDF')",
                resumeId,
                candidateId);
        jdbcTemplate.update(
                """
                INSERT INTO job_applications (id, job_id, candidate_id, resume_id, recruitment_cycle, status, ai_consent,
                                              ai_consent_at)
                VALUES (?, ?, ?, ?, 1, 'REJECTED', TRUE, now())
                """,
                applicationId,
                jobId,
                candidateId,
                resumeId);
        jdbcTemplate.update(
                "INSERT INTO application_status_history (application_id, from_status, to_status) VALUES (?, NULL, 'PENDING')",
                applicationId);
        jdbcTemplate.update(
                "INSERT INTO application_status_history (application_id, from_status, to_status, changed_by) "
                        + "VALUES (?, 'PENDING', 'REJECTED', ?)",
                applicationId,
                hrId);
        return new Fixture(hrId, candidateId, jobId, applicationId);
    }

    // Cau @Modifying can transaction; tra int de assertThat khong nhap nhang kieu.
    private int tx(IntSupplier work) {
        Integer rows = transactionTemplate.execute(status -> work.getAsInt());
        return rows == null ? -1 : rows;
    }

    private int insertRequest(UUID applicationId) {
        return jdbcTemplate.update(
                "INSERT INTO application_reconsideration_requests (application_id) VALUES (?)", applicationId);
    }

    private long countRequests(UUID applicationId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM application_reconsideration_requests WHERE application_id = ?",
                Long.class,
                applicationId);
    }

    @Test
    void validInsert_defaultsOpenAndUndecided() {
        Fixture f = createRejectedApplication();

        assertThat(insertRequest(f.applicationId())).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM application_reconsideration_requests
                        WHERE application_id = ? AND status = 'OPEN' AND requested_at IS NOT NULL
                          AND reason IS NULL AND hr_response IS NULL AND decided_at IS NULL AND decided_by IS NULL
                        """,
                        Long.class,
                        f.applicationId()))
                .isEqualTo(1L);
    }

    // T23 - mot yeu cau cho moi don (R-E2), ke ca khi yeu cau dau da quyet.
    @Test
    void secondRequestForSameApplication_violatesUnique() {
        Fixture f = createRejectedApplication();
        insertRequest(f.applicationId());
        jdbcTemplate.update(
                """
                UPDATE application_reconsideration_requests SET status = 'DECLINED', decided_at = now(), decided_by = ?
                WHERE application_id = ?
                """,
                f.hrId(),
                f.applicationId());

        assertThatThrownBy(() -> insertRequest(f.applicationId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_reconsideration_per_application");
        assertThat(countRequests(f.applicationId())).isEqualTo(1L);
    }

    // T23 - OPEN thi chua co thoi diem/nguoi quyet.
    @Test
    void openWithDecidedAt_violatesDecisionCheck() {
        Fixture f = createRejectedApplication();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO application_reconsideration_requests (application_id, decided_at) VALUES (?, now())",
                        f.applicationId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_decision");
    }

    // T23 - da quyet thi phai co ca decided_at lan decided_by.
    @Test
    void declinedWithoutDecidedBy_violatesDecisionCheck() {
        Fixture f = createRejectedApplication();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO application_reconsideration_requests (application_id, status, decided_at)
                        VALUES (?, 'DECLINED', now())
                        """,
                        f.applicationId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_decision");
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO application_reconsideration_requests (application_id, status, decided_by)
                        VALUES (?, 'ACCEPTED', ?)
                        """,
                        f.applicationId(),
                        f.hrId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_decision");
    }

    // T23 - phan hoi chi di cung DECLINED.
    @Test
    void acceptedWithHrResponse_violatesResponseCheck() {
        Fixture f = createRejectedApplication();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        INSERT INTO application_reconsideration_requests
                            (application_id, status, decided_at, decided_by, hr_response)
                        VALUES (?, 'ACCEPTED', now(), ?, 'Phan hoi')
                        """,
                        f.applicationId(),
                        f.hrId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_response_declined");
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO application_reconsideration_requests (application_id, hr_response) VALUES (?, 'x')",
                        f.applicationId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_response_declined");
    }

    @Test
    void unknownStatus_violatesStatusCheck() {
        Fixture f = createRejectedApplication();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO application_reconsideration_requests (application_id, status) VALUES (?, 'PENDING')",
                        f.applicationId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countRequests(f.applicationId())).isZero();
    }

    // T23 - bien do dai ly do (R-V2): 999, 1000 hop le; 1001 vi pham.
    @Test
    void reasonLength_boundaryAt1000() {
        Fixture f999 = createRejectedApplication();
        Fixture f1000 = createRejectedApplication();
        Fixture f1001 = createRejectedApplication();
        String sql = "INSERT INTO application_reconsideration_requests (application_id, reason) VALUES (?, ?)";

        assertThat(jdbcTemplate.update(sql, f999.applicationId(), "a".repeat(999))).isEqualTo(1);
        assertThat(jdbcTemplate.update(sql, f1000.applicationId(), "a".repeat(1000))).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(sql, f1001.applicationId(), "a".repeat(1001)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_reason_length");
    }

    // char_length dem ky tu, khong dem byte: 1000 ky tu tieng Viet co dau (nhieu byte UTF-8) van hop le.
    @Test
    void reasonLength_countsCharactersNotBytes() {
        Fixture f = createRejectedApplication();

        assertThat(jdbcTemplate.update(
                        "INSERT INTO application_reconsideration_requests (application_id, reason) VALUES (?, ?)",
                        f.applicationId(),
                        "ệ".repeat(1000)))
                .isEqualTo(1);
    }

    // T23 - bien do dai phan hoi: 1000 hop le, 1001 vi pham.
    @Test
    void hrResponseLength_boundaryAt1000() {
        Fixture ok = createRejectedApplication();
        Fixture tooLong = createRejectedApplication();
        String sql =
                """
                INSERT INTO application_reconsideration_requests
                    (application_id, status, decided_at, decided_by, hr_response)
                VALUES (?, 'DECLINED', now(), ?, ?)
                """;

        assertThat(jdbcTemplate.update(sql, ok.applicationId(), ok.hrId(), "b".repeat(1000))).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(sql, tooLong.applicationId(), tooLong.hrId(), "b".repeat(1001)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_reconsideration_response_length");
    }

    // ON DELETE RESTRICT (muc 4.1, 9.1): don con yeu cau thi khong xoa cung duoc; nguoi quyet cung vay. Nguoi quyet la
    // tai khoan rieng khong co lien ket nao khac, de loi chi co the den tu decided_by.
    @Test
    void hardDeleteApplicationOrDecider_restricted() {
        Fixture f = createRejectedApplication();
        UUID deciderId = createUser("HR");
        jdbcTemplate.update(
                """
                INSERT INTO application_reconsideration_requests (application_id, status, decided_at, decided_by)
                VALUES (?, 'ACCEPTED', now(), ?)
                """,
                f.applicationId(),
                deciderId);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM job_applications WHERE id = ?", f.applicationId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("application_reconsideration_requests");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", deciderId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("application_reconsideration_requests");
        assertThat(countRequests(f.applicationId())).isEqualTo(1L);

        // Ca duong: don khong co yeu cau thi xoa duoc (RESTRICT chi chan khi con dong con).
        Fixture g = createRejectedApplication();
        assertThat(jdbcTemplate.update("DELETE FROM job_applications WHERE id = ?", g.applicationId())).isEqualTo(1);
    }

    // Khong cot job_id (Q4), khong cot ten cam CLAUDE.md muc 7.
    @Test
    void columns_exactlyAsSpec() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = 'application_reconsideration_requests' ORDER BY ordinal_position",
                String.class);

        assertThat(columns)
                .containsExactly(
                        "id",
                        "application_id",
                        "reason",
                        "status",
                        "hr_response",
                        "requested_at",
                        "decided_at",
                        "decided_by");
    }

    // Doc qua entity: requested_at do DB gan, status anh xa enum.
    @Test
    void entityRead_mapsColumns() {
        Fixture f = createRejectedApplication();
        jdbcTemplate.update(
                """
                INSERT INTO application_reconsideration_requests
                    (application_id, reason, status, decided_at, decided_by, hr_response)
                VALUES (?, 'Ly do', 'DECLINED', now(), ?, 'Phan hoi')
                """,
                f.applicationId(),
                f.hrId());

        ReconsiderationRequest row = repository.findByApplicationId(f.applicationId()).orElseThrow();

        OffsetDateTime dbRequestedAt = jdbcTemplate.queryForObject(
                "SELECT requested_at FROM application_reconsideration_requests WHERE application_id = ?",
                OffsetDateTime.class,
                f.applicationId());
        assertThat(row.getStatus()).isEqualTo(ReconsiderationStatus.DECLINED);
        assertThat(row.getReason()).isEqualTo("Ly do");
        assertThat(row.getHrResponse()).isEqualTo("Phan hoi");
        assertThat(row.getDecidedBy()).isEqualTo(f.hrId());
        assertThat(row.getDecidedAt()).isNotNull();
        assertThat(row.getRequestedAt()).isEqualTo(dbRequestedAt.toInstant());
    }

    // Chay thu moi cau native cua repository tren Postgres that (cu phap, alias projection, kieu tham so null). Hanh vi
    // nghiep vu day du (bien han, thu tu R-E5, tranh chap) kiem o dot 3-4 (T1-T22).
    @Test
    void repositoryNativeQueries_executeOnRealPostgres() {
        Fixture f = createRejectedApplication();

        ReconsiderationCandidateRow before =
                repository.findCandidateRow(f.applicationId(), f.candidateId()).orElseThrow();
        assertThat(before.getApplicationStatus()).isEqualTo("REJECTED");
        assertThat(before.getLastRejectedFromStatus()).isEqualTo("PENDING");
        assertThat(before.getRejectedAt()).isNotNull();
        assertThat(before.getDeadline()).isEqualTo(before.getRejectedAt().plusSeconds(168L * 3600));
        assertThat(before.getWithinDeadline()).isTrue();
        assertThat(before.getJobRemoved()).isFalse();
        assertThat(before.getRequestStatus()).isNull();
        assertThat(repository.findCandidateRow(f.applicationId(), f.hrId())).isEmpty();

        // Ung vien khac -> 0 dong; dung ung vien, reason null -> 1 dong, reason NULL.
        assertThat(tx(() ->repository.insertIfEligible(f.applicationId(), f.hrId(), "x")))
                .isZero();
        assertThat(tx(() ->repository.insertIfEligible(f.applicationId(), f.candidateId(), null)))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT reason FROM application_reconsideration_requests WHERE application_id = ?",
                        String.class,
                        f.applicationId()))
                .isNull();

        ReconsiderationCandidateRow open = repository.findCandidateRow(f.applicationId(), f.candidateId()).orElseThrow();
        assertThat(open.getRequestStatus()).isEqualTo("OPEN");
        assertThat(open.getRequestedAt()).isNotNull();

        ReconsiderationHrRow hrRow = repository.findHrRow(f.applicationId()).orElseThrow();
        assertThat(hrRow.getCandidateId()).isEqualTo(f.candidateId());
        assertThat(hrRow.getCandidateFullName()).isEqualTo("C09 V21");
        assertThat(hrRow.getRejectedAt()).isEqualTo(before.getRejectedAt());
        assertThat(hrRow.getStatus()).isEqualTo("OPEN");
        assertThat(repository.findHrRowsByJob(f.jobId(), "OPEN")).hasSize(1);
        assertThat(repository.findHrRowsByJob(f.jobId(), null)).hasSize(1);
        assertThat(repository.countOpenByJob(f.jobId())).isEqualTo(1L);

        // Quyet: decline voi response null; lan hai (va accept sau do) -> 0 dong.
        assertThat(tx(() ->repository.declineIfOpen(f.applicationId(), f.hrId(), null)))
                .isEqualTo(1);
        assertThat(tx(() ->repository.declineIfOpen(f.applicationId(), f.hrId(), "y")))
                .isZero();
        assertThat(tx(() ->repository.acceptIfOpen(f.applicationId(), f.hrId())))
                .isZero();
        assertThat(repository.findHrRowsByJob(f.jobId(), "OPEN")).isEmpty();
        assertThat(repository.findHrRowsByJob(f.jobId(), null)).hasSize(1);
        assertThat(repository.countOpenByJob(f.jobId())).isZero();

        // accept tren yeu cau OPEN khac.
        Fixture g = createRejectedApplication();
        insertRequest(g.applicationId());
        assertThat(tx(() ->repository.acceptIfOpen(g.applicationId(), g.hrId())))
                .isEqualTo(1);
        assertThat(repository.findByApplicationId(g.applicationId()).orElseThrow().getStatus())
                .isEqualTo(ReconsiderationStatus.ACCEPTED);
    }
}
