package com.recruitment.resume;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeReparseRequestRepository extends JpaRepository<ResumeReparseRequest, UUID> {

    // Kiem som "dang co yeu cau PENDING/RUNNING" de tra 409 than thien (R-R2). Chot chan that la
    // uq_resume_reparse_request_active (V8).
    boolean existsByResumeIdAndStatusIn(UUID resumeId, Collection<ResumeReparseRequestStatus> statuses);

    // Yeu cau gan nhat cua MOT CV (API danh sach CV, R-R4). id la khoa cuoi vi requested_at la now()
    // theo transaction (CLAUDE.md muc 3c).
    Optional<ResumeReparseRequest> findFirstByResumeIdOrderByRequestedAtDescIdDesc(UUID resumeId);

    // Yeu cau gan nhat cua NHIEU CV trong mot cau (danh sach CV cua ung vien) - DISTINCT ON cua
    // Postgres, cung thu tu requested_at DESC, id DESC nhu method tren.
    @Query(
            value = "SELECT DISTINCT ON (resume_id) * FROM resume_reparse_requests "
                    + "WHERE resume_id IN (:resumeIds) ORDER BY resume_id, requested_at DESC, id DESC",
            nativeQuery = true)
    List<ResumeReparseRequest> findLatestByResumeIds(@Param("resumeIds") Collection<UUID> resumeIds);

    // Poller: bo qua ban ghi con trong backoff, khoa cuoi id (mau ResumeRepository.findReadyForProcessing).
    @Query(
            value = "SELECT * FROM resume_reparse_requests WHERE status = 'PENDING' "
                    + "AND (next_attempt_at IS NULL OR next_attempt_at <= now()) "
                    + "ORDER BY requested_at, id LIMIT :batchSize",
            nativeQuery = true)
    List<ResumeReparseRequest> findReadyForProcessing(@Param("batchSize") int batchSize);

    // Claim bang UPDATE co dieu kien, kiem rowcount ben ngoai (CLAUDE.md muc 3c).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resume_reparse_requests SET status = 'RUNNING', claimed_at = now() "
                    + "WHERE id = :id AND status = 'PENDING' "
                    + "AND (next_attempt_at IS NULL OR next_attempt_at <= now())",
            nativeQuery = true)
    int claimForProcessing(@Param("id") UUID id);

    // Loi LLM tam thoi / stale claim - mot UPDATE vua ghi vua chong race, attempt_count lam khoa lac
    // quan (mau ResumeRepository.markTemporaryFailure). Rowcount 0 = luong khac da doi ban ghi truoc.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resume_reparse_requests SET status = :nextStatus, attempt_count = :nextAttempt, "
                    + "error_message = :errorMessage, next_attempt_at = :nextAttemptAt, claimed_at = NULL, "
                    + "finished_at = :finishedAt "
                    + "WHERE id = :id AND status = 'RUNNING' AND attempt_count = :expectedCount",
            nativeQuery = true)
    int markTemporaryFailure(
            @Param("id") UUID id,
            @Param("expectedCount") int expectedCount,
            @Param("nextStatus") String nextStatus,
            @Param("nextAttempt") int nextAttempt,
            @Param("errorMessage") String errorMessage,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("finishedAt") Instant finishedAt);

    // Stale-claim reaper (mau ResumeRepository.findIdsByParseStatusAndClaimedAtBefore): tra id, khong
    // tra entity.
    @Query("SELECT r.id FROM ResumeReparseRequest r WHERE r.status = :status AND r.claimedAt < :threshold")
    List<UUID> findIdsByStatusAndClaimedAtBefore(
            @Param("status") ResumeReparseRequestStatus status, @Param("threshold") Instant threshold);
}
