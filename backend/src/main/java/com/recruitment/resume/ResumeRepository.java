package com.recruitment.resume;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    Optional<Resume> findByIdAndCandidateId(UUID id, UUID candidateId);

    Optional<Resume> findByCandidateIdAndIsPrimaryTrue(UUID candidateId);

    boolean existsByCandidateId(UUID candidateId);

    // Dot 4f (chore/hardening) - thay the findByParseStatus(PENDING, pageable) cu: them dieu kien
    // next_attempt_at (bo qua ban ghi con dang cho backoff) VA khoa cuoi ", id" - thieu khoa cuoi,
    // nhieu resume upload trong CUNG mot transaction co uploaded_at bang nhau (CLAUDE.md muc 3c) se
    // ra thu tu khong on dinh giua hai lan poll, mot ban ghi co the bi doi vo han khi so PENDING
    // lon hon batchSize (dung loi ma Dot 2b da sua o ScoringRunRepository).
    @Query(
            value = "SELECT * FROM resumes WHERE parse_status = 'PENDING' "
                    + "AND (next_attempt_at IS NULL OR next_attempt_at <= now()) "
                    + "ORDER BY uploaded_at, id LIMIT :batchSize",
            nativeQuery = true)
    List<Resume> findReadyForProcessing(@Param("batchSize") int batchSize);

    // Claim bang UPDATE co dieu kien, kiem tra rowcount ben ngoai (xem CLAUDE.md muc 3c) - KHONG
    // dung SELECT FOR UPDATE SKIP LOCKED vi no giu transaction mo trong luc cho LLM.
    // clearAutomatically = true bat buoc: bulk UPDATE qua @Query di thang xuong SQL, khong tu dong
    // bo persistence context - thieu co nay, mot entity Resume da load truoc do trong cung
    // persistence context se tiep tuc hien thi parse_status cu trong bo nho du DB da doi.
    // Dot 4f: them dieu kien next_attempt_at - dong bo voi findReadyForProcessing, tranh mot
    // resume dang trong backoff bi claim som neu co duong goi truc tiep khac (khong qua findReadyForProcessing).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resumes SET parse_status = 'PROCESSING', claimed_at = now() "
                    + "WHERE id = :id AND parse_status = 'PENDING' "
                    + "AND (next_attempt_at IS NULL OR next_attempt_at <= now())",
            nativeQuery = true)
    int claimForProcessing(@Param("id") UUID id);

    // Dot 4e (chore/hardening) - ghi ket qua loi LLM TAM THOI bang MOT UPDATE co dieu kien vua la
    // buoc ghi vua la chot chan chong race, dung khuon voi ScoringRunRepository.markTemporaryFailure.
    // attempt_count dong vai tro cot version quang hoc (optimistic lock): dieu kien
    // "attempt_count = :expectedCount" bat duoc truong hop mot luong khac (worker goc hoan thanh,
    // hoac stale-claim reaper o Dot 4h) da thay doi ban ghi nay giua luc doc va luc ghi - rowcount=0
    // la NO-OP AN TOAN (xem ResumeParsingStateService.markTemporaryFailure), KHONG phai loi.
    // claimed_at reset ve NULL khi quay lai PENDING - cho lan claim ke tiep ghi lai gia tri moi.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resumes SET parse_status = :nextStatus, attempt_count = :nextAttempt, "
                    + "parse_error = :errorMessage, next_attempt_at = :nextAttemptAt, claimed_at = NULL "
                    + "WHERE id = :id AND parse_status = 'PROCESSING' AND attempt_count = :expectedCount",
            nativeQuery = true)
    int markTemporaryFailure(
            @Param("id") UUID id,
            @Param("expectedCount") int expectedCount,
            @Param("nextStatus") String nextStatus,
            @Param("nextAttempt") int nextAttempt,
            @Param("errorMessage") String errorMessage,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    // Dot 4h (chore/hardening) - danh sach ID cho stale-claim reaper: mot ban ghi PROCESSING claim
    // qua lau (claimed_at truoc nguong stale-timeout-ms) nghia la JVM co the da restart giua chung,
    // ban ghi "ket" vinh vien vi scheduler chinh chi quet PENDING. Tra List<UUID> (KHONG tra
    // List<Resume>) - dung trong @Transactional(readOnly = true), khong duoc de entity thoat ra
    // ngoai pham vi transaction doc do (xem ResumeParsingStateService.findStaleClaimIds).
    @Query("SELECT r.id FROM Resume r WHERE r.parseStatus = :status AND r.claimedAt < :threshold")
    List<UUID> findIdsByParseStatusAndClaimedAtBefore(
            @Param("status") ParseStatus status, @Param("threshold") Instant threshold);

    // Muc 3b con sot cua ke hoach Dot 3/4 (chore/hardening) - duong thu lai THU CONG cho candidate
    // khi CV da FAILED han (het so lan thu tu dong o Dot 4, hoac loi moi truong khong phai loi SDK
    // vi du thieu ANTHROPIC_API_KEY luc chay). Dieu kien nguon "parse_status = 'FAILED'" dong vai
    // tro chot chan optimistic - FAILED la trang thai cuoi cung, chi co dung MOT duong di vao no nen
    // khong can so khop them attempt_count nhu markTemporaryFailure; rowcount 0 nghia la mot luong
    // khac da doi ban ghi nay khoi FAILED giua luc ResumeService kiem tra va luc goi ham nay (race
    // hiem), xem ResumeParsingStateService.retry. Reset CA BA cot: attempt_count/next_attempt_at ve
    // 0/NULL (thao tac thu cong la BAT DAU LAI TU DAU, khong tinh vao so lan thu tu dong da dung
    // het - khong reset se FAILED ngay o loi tam thoi ke tiep vi da o san nguong exhausted);
    // claimed_at ve NULL vi ban ghi khong con o PROCESSING.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resumes SET parse_status = 'PENDING', parse_error = NULL, attempt_count = 0, "
                    + "next_attempt_at = NULL, claimed_at = NULL WHERE id = :id AND parse_status = 'FAILED'",
            nativeQuery = true)
    int retryFailedResume(@Param("id") UUID id);
}
