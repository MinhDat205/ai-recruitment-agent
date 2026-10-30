package com.recruitment.savedjob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// FR-U16 - cau SQL cua REQUIREMENT muc 4.4 (Q1-Q8 da duyet o dot 3). Moi cau loc candidate_id lay tu JWT: khong co
// duong doc/sua dong cua ung vien khac. Moi ORDER BY ket thuc bang s.id (R-V1).
public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {

    /**
     * Q1 (R-L3 buoc 2, L5) - khoa hang users cua chinh ung vien, giu toi het transaction, de hai request luu cua cung
     * ung vien chay noi tiep (chot tran 100 va ca hai PUT cung tin). FOR NO KEY UPDATE tu xung dot voi chinh no nhung
     * KHONG xung dot voi FOR KEY SHARE ma moi INSERT co FK toi users tu lay (vd ung vien dang nop don); FOR UPDATE thi
     * co. Chi goi trong transaction GHI (Postgres cam FOR ... trong transaction chi doc).
     */
    @Query(value = "SELECT id FROM users WHERE id = :candidateId FOR NO KEY UPDATE", nativeQuery = true)
    Optional<UUID> lockCandidateForSave(@Param("candidateId") UUID candidateId);

    /**
     * Q2 (R-L3 buoc 3) - dong cua cap (ung vien, tin), ke ca dong da bo luu. PHAI chay SAU Q1 va khong duoc nap
     * SavedJob nao truoc Q1 trong cung persistence context: o READ COMMITTED cau sau khi cho khoa moi thay dong request
     * truoc vua commit; doc truoc khoa (hoac lay tu cache cap 1) se bo lot va chen dong thu hai.
     */
    Optional<SavedJob> findByCandidateIdAndJobId(UUID candidateId, UUID jobId);

    // Q3 (R-L3 buoc 4) - dem dong dang luu, goi sau Q1.
    @Query(
            value = "SELECT count(*) FROM saved_jobs WHERE candidate_id = :candidateId AND deleted_at IS NULL",
            nativeQuery = true)
    long countActiveByCandidateId(@Param("candidateId") UUID candidateId);

    // Q4 (R-S6, R-S8) - luu lai dong da bo: xoa deleted_at, saved_at = now() cua DB. Duoi khoa Q1 luon ra 1 dong.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE saved_jobs SET deleted_at = NULL, saved_at = now()
                    WHERE candidate_id = :candidateId AND job_id = :jobId AND deleted_at IS NOT NULL
                    """,
            nativeQuery = true)
    int reactivate(@Param("candidateId") UUID candidateId, @Param("jobId") UUID jobId);

    // Q5 (luu moi) dung saveAndFlush(SavedJob) co san cua JpaRepository - saved_at do DEFAULT now() cua DB gan.

    // Q6 (R-S5) - bo luu = dat deleted_at, chi dong dang luu. 0 dong (chua luu/da bo/jobId la) van la thanh cong.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE saved_jobs SET deleted_at = now()
                    WHERE candidate_id = :candidateId AND job_id = :jobId AND deleted_at IS NULL
                    """,
            nativeQuery = true)
    int softDelete(@Param("candidateId") UUID candidateId, @Param("jobId") UUID jobId);

    // Q7 (E3, R-V1, R-V3) - dong dang luu + don o chu ky HIEN TAI cua tin (toi da mot don, uq_application_per_cycle).
    // Alias phai khop dung ten getter cua SavedJobListRow.
    @Query(
            value =
                    """
                    SELECT s.id AS savedJobId, s.job_id AS jobId, s.saved_at AS savedAt,
                           ja.id AS applicationId, ja.status AS applicationStatus
                    FROM saved_jobs s
                    JOIN jobs j ON j.id = s.job_id
                    LEFT JOIN job_applications ja
                           ON ja.job_id = s.job_id
                          AND ja.candidate_id = s.candidate_id
                          AND ja.recruitment_cycle = j.recruitment_cycle
                    WHERE s.candidate_id = :candidateId AND s.deleted_at IS NULL
                    ORDER BY s.saved_at DESC, s.id DESC
                    """,
            nativeQuery = true)
    List<SavedJobListRow> findActiveList(@Param("candidateId") UUID candidateId);

    // Q8 (E4) - id cac tin dang luu, cung thu tu Q7.
    @Query(
            value =
                    """
                    SELECT s.job_id FROM saved_jobs s
                    WHERE s.candidate_id = :candidateId AND s.deleted_at IS NULL
                    ORDER BY s.saved_at DESC, s.id DESC
                    """,
            nativeQuery = true)
    List<UUID> findActiveJobIds(@Param("candidateId") UUID candidateId);
}
