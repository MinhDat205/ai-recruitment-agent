package com.recruitment.savedjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruitment.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// FR-U16 T17, T17b (V20, Q8, L4). SQL thang qua JdbcTemplate de kiem rang buoc o DB (UNIQUE, FK, CASCADE, index mot
// phan); mot ca ghi qua entity de kiem anh xa saved_at do DB sinh. Moi ca tu tao nguoi dung/tin rieng (UUID ngau
// nhien). Khong @Transactional: moi cau tu commit, loi rang buoc khong lam hong ca test khac (khuon CvBuilderMigrationTest).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SavedJobMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, full_name) VALUES (?, ?, 'x', ?, 'U16 V20')",
                id,
                "u16-v20-" + id + "@test.local",
                role);
        return id;
    }

    // Tin khong co dong con RESTRICT nao (khong don) - xoa cung duoc de kiem CASCADE (T17b).
    private UUID createJob() {
        UUID hrId = createUser("HR");
        UUID companyId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO companies (id, owner_id, name) VALUES (?, ?, 'Cong ty U16')", companyId, hrId);
        jdbcTemplate.update(
                "INSERT INTO jobs (id, company_id, created_by, title, description) VALUES (?, ?, ?, 'Tin U16', 'Mo ta')",
                jobId,
                companyId,
                hrId);
        return jobId;
    }

    private int insertSaved(UUID candidateId, UUID jobId) {
        return jdbcTemplate.update("INSERT INTO saved_jobs (candidate_id, job_id) VALUES (?, ?)", candidateId, jobId);
    }

    private long countSaved(String where, Object... args) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM saved_jobs WHERE " + where, Long.class, args);
    }

    @Test
    void validInsert_defaultsSavedAtAndNullDeletedAt() {
        UUID candidateId = createUser("CANDIDATE");
        UUID jobId = createJob();

        assertThat(insertSaved(candidateId, jobId)).isEqualTo(1);

        assertThat(countSaved(
                        "candidate_id = ? AND job_id = ? AND saved_at IS NOT NULL AND deleted_at IS NULL",
                        candidateId,
                        jobId))
                .isEqualTo(1L);
    }

    // T17 - moi cap (ung vien, tin) dung mot dong (R-S6), ke ca khi dong cu da bo luu.
    @Test
    void secondRowForSamePair_violatesUnique_evenWhenFirstIsSoftDeleted() {
        UUID candidateId = createUser("CANDIDATE");
        UUID jobId = createJob();
        insertSaved(candidateId, jobId);
        jdbcTemplate.update(
                "UPDATE saved_jobs SET deleted_at = now() WHERE candidate_id = ? AND job_id = ?", candidateId, jobId);

        assertThatThrownBy(() -> insertSaved(candidateId, jobId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_saved_job_per_candidate");
        assertThat(countSaved("candidate_id = ? AND job_id = ?", candidateId, jobId)).isEqualTo(1L);
    }

    // T17 - cung tin, ung vien khac: hop le (unique theo cap, khong theo tin).
    @Test
    void sameJobDifferentCandidates_allowed() {
        UUID jobId = createJob();

        assertThat(insertSaved(createUser("CANDIDATE"), jobId)).isEqualTo(1);
        assertThat(insertSaved(createUser("CANDIDATE"), jobId)).isEqualTo(1);
        assertThat(countSaved("job_id = ?", jobId)).isEqualTo(2L);
    }

    // T17 - FK.
    @Test
    void unknownCandidateOrJob_violatesForeignKey() {
        UUID jobId = createJob();
        UUID candidateId = createUser("CANDIDATE");

        assertThatThrownBy(() -> insertSaved(UUID.randomUUID(), jobId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSaved(candidateId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // T17 - index mot phan cho danh sach dang luu.
    @Test
    void activeIndex_existsWithDeletedAtPredicate() {
        String indexDef = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'saved_jobs' "
                        + "AND indexname = 'idx_saved_jobs_candidate_active'",
                String.class);

        assertThat(indexDef).contains("(candidate_id, saved_at DESC)").contains("WHERE (deleted_at IS NULL)");
    }

    // Khong cot ten cam CLAUDE.md muc 7, khong cot diem/role (REQUIREMENT muc 4.1).
    @Test
    void columns_exactlyAsSpec() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'saved_jobs' "
                        + "ORDER BY ordinal_position",
                String.class);

        assertThat(columns).containsExactly("id", "candidate_id", "job_id", "saved_at", "deleted_at");
    }

    // T17b (Q8) - xoa cung tin/nguoi dung thi dong luu mat theo; dong khong lien quan khong bi dung.
    @Test
    void hardDeleteJobOrUser_cascadesSavedRowsOnly() {
        UUID candidateA = createUser("CANDIDATE");
        UUID candidateB = createUser("CANDIDATE");
        UUID job1 = createJob();
        UUID job2 = createJob();
        insertSaved(candidateA, job1);
        insertSaved(candidateA, job2);
        insertSaved(candidateB, job1);
        insertSaved(candidateB, job2);

        jdbcTemplate.update("DELETE FROM jobs WHERE id = ?", job1);

        assertThat(countSaved("job_id = ?", job1)).isZero();
        assertThat(countSaved("candidate_id = ? AND job_id = ?", candidateA, job2)).isEqualTo(1L);
        assertThat(countSaved("candidate_id = ? AND job_id = ?", candidateB, job2)).isEqualTo(1L);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", candidateA);

        assertThat(countSaved("candidate_id = ?", candidateA)).isZero();
        assertThat(countSaved("candidate_id = ? AND job_id = ?", candidateB, job2)).isEqualTo(1L);
    }

    // L4 - INSERT qua entity: saved_at do DEFAULT now() cua DB gan va doc nguoc vao entity; deleted_at null.
    @Test
    void entityInsert_savedAtGeneratedByDatabase() {
        UUID candidateId = createUser("CANDIDATE");
        UUID jobId = createJob();

        SavedJob saved = transactionTemplate.execute(status -> {
            SavedJob row = new SavedJob();
            row.setCandidateId(candidateId);
            row.setJobId(jobId);
            entityManager.persist(row);
            entityManager.flush();
            return row;
        });

        OffsetDateTime dbSavedAt = jdbcTemplate.queryForObject(
                "SELECT saved_at FROM saved_jobs WHERE id = ?", OffsetDateTime.class, saved.getId());
        assertThat(saved.getSavedAt()).isEqualTo(dbSavedAt.toInstant());
        assertThat(saved.getDeletedAt()).isNull();
    }
}
