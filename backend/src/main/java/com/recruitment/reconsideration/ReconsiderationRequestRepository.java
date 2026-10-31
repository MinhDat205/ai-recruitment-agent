package com.recruitment.reconsideration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// FR-C09 - cau SQL cua REQUIREMENT muc 3.1-3.4, 4.4. Moi so sanh han dung now() cua Postgres TRONG cau (R-T2), khong
// Instant.now()/Clock o Java. Hai LATERAL (kem bieu thuc han) khai MOT lan (hang so ben duoi) de buoc kiem truoc (C1,
// khoi ung vien) va cau ghi (insertIfEligible) cung mot dinh nghia (R-T3). Moi ORDER BY ket thuc bang khoa duy nhat.
public interface ReconsiderationRequestRepository extends JpaRepository<ReconsiderationRequest, UUID> {

    // R-E1 (c) - dong lich su vao REJECTED MOI NHAT cua don ja.
    String LAST_REJECTED_LATERAL =
            """
            LEFT JOIN LATERAL (
                SELECT ash.from_status, ash.changed_at FROM application_status_history ash
                WHERE ash.application_id = ja.id AND ash.to_status = 'REJECTED'
                ORDER BY ash.changed_at DESC, ash.id DESC LIMIT 1
            ) last_rej ON TRUE
            """;

    // rejectedAt/han - dong PENDING -> REJECTED cua don ja (duy nhat: PENDING chi vao luc tao don). Don du dieu kien thi
    // day chinh la dong last_rej. R-T1: han = changed_at + '168 hours', KHONG '7 days' (lech mot gio o ngay doi gio mua
    // he) - dinh nghia DUY NHAT cua han, moi cau doc pending_rej.deadline.
    String PENDING_REJECTED_LATERAL =
            """
            LEFT JOIN LATERAL (
                SELECT ash.changed_at, ash.changed_at + INTERVAL '168 hours' AS deadline
                FROM application_status_history ash
                WHERE ash.application_id = ja.id AND ash.from_status = 'PENDING' AND ash.to_status = 'REJECTED'
                ORDER BY ash.changed_at DESC, ash.id DESC LIMIT 1
            ) pending_rej ON TRUE
            """;

    // C1 + khoi ung vien (R-C1, R-E5). Mot dong cho don cua CHINH ung vien (candidate_id la chot chan thu hai sau
    // findByIdAndCandidateId); rong khi khong phai don cua ung vien.
    @Query(
            value =
                    """
                    SELECT ja.status AS applicationStatus,
                           last_rej.from_status AS lastRejectedFromStatus,
                           pending_rej.changed_at AS rejectedAt,
                           pending_rej.deadline AS deadline,
                           (now() <= pending_rej.deadline) AS withinDeadline,
                           (j.deleted_at IS NOT NULL) AS jobRemoved,
                           r.status AS requestStatus, r.reason AS reason, r.requested_at AS requestedAt,
                           r.decided_at AS decidedAt, r.hr_response AS hrResponse
                    FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    """
                            + LAST_REJECTED_LATERAL
                            + PENDING_REJECTED_LATERAL
                            + """
                    LEFT JOIN application_reconsideration_requests r ON r.application_id = ja.id
                    WHERE ja.id = :applicationId AND ja.candidate_id = :candidateId
                    """,
            nativeQuery = true)
    Optional<ReconsiderationCandidateRow> findCandidateRow(
            @Param("applicationId") UUID applicationId, @Param("candidateId") UUID candidateId);

    // R-T3 - ghi yeu cau trong MOT cau: dieu kien R-E1 b, c, e + han. 0 dong -> service doc lai findCandidateRow de tra
    // dung ma theo R-E5. Request thu hai dong thoi (R-E2) cho o uq_reconsideration_per_application roi vang
    // unique_violation -> DataIntegrityViolationException -> GlobalExceptionHandler -> 409.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO application_reconsideration_requests (application_id, reason)
                    SELECT ja.id, CAST(:reason AS text)
                    FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    """
                            + LAST_REJECTED_LATERAL
                            + PENDING_REJECTED_LATERAL
                            + """
                    WHERE ja.id = :applicationId AND ja.candidate_id = :candidateId
                      AND ja.status = 'REJECTED'
                      AND last_rej.from_status = 'PENDING'
                      AND j.deleted_at IS NULL
                      AND now() <= pending_rej.deadline
                    """,
            nativeQuery = true)
    int insertIfEligible(
            @Param("applicationId") UUID applicationId,
            @Param("candidateId") UUID candidateId,
            @Param("reason") String reason);

    // R-S3 (0) + C1 kiem truoc - yeu cau cua don (toi da mot, uq_reconsideration_per_application).
    Optional<ReconsiderationRequest> findByApplicationId(UUID applicationId);

    // R-S3 (1) - chap nhan khi HR gui giay moi. Cung hang voi declineIfOpen: luot sau cho khoa hang roi danh gia lai WHERE
    // tren ban da commit -> 0 dong -> 409 RECONSIDERATION_NOT_OPEN (R-D3). Khong SELECT ... FOR UPDATE.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE application_reconsideration_requests
                    SET status = 'ACCEPTED', decided_at = now(), decided_by = :ownerId
                    WHERE application_id = :applicationId AND status = 'OPEN'
                    """,
            nativeQuery = true)
    int acceptIfOpen(@Param("applicationId") UUID applicationId, @Param("ownerId") UUID ownerId);

    // R-D2 - giu quyet dinh tu choi. response da chuan hoa o service (R-V1); null = HR khong nhap.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE application_reconsideration_requests
                    SET status = 'DECLINED', hr_response = CAST(:response AS text), decided_at = now(), decided_by = :ownerId
                    WHERE application_id = :applicationId AND status = 'OPEN'
                    """,
            nativeQuery = true)
    int declineIfOpen(
            @Param("applicationId") UUID applicationId,
            @Param("ownerId") UUID ownerId,
            @Param("response") String response);

    // Khoi yeu cau o E1 cua FR-H09 va response H2 - du lieu tho, ReconsiderationReader che truoc khi dung DTO (R-A2).
    @Query(
            value =
                    """
                    SELECT r.application_id AS applicationId, ja.candidate_id AS candidateId,
                           u.full_name AS candidateFullName, ja.applied_at AS appliedAt,
                           pending_rej.changed_at AS rejectedAt, r.requested_at AS requestedAt, r.reason AS reason,
                           r.status AS status, r.decided_at AS decidedAt, r.hr_response AS hrResponse
                    FROM application_reconsideration_requests r
                    JOIN job_applications ja ON ja.id = r.application_id
                    JOIN users u ON u.id = ja.candidate_id
                    """
                            + PENDING_REJECTED_LATERAL
                            + """
                    WHERE r.application_id = :applicationId
                    """,
            nativeQuery = true)
    Optional<ReconsiderationHrRow> findHrRow(@Param("applicationId") UUID applicationId);

    // H1 (muc 4.4) - yeu cau cua MOI chu ky cua tin; status null = tat ca, 'OPEN' = dang cho. Cho lau nhat o tren, khoa
    // cuoi application_id (duy nhat trong bang). Khong phan trang.
    @Query(
            value =
                    """
                    SELECT r.application_id AS applicationId, ja.candidate_id AS candidateId,
                           u.full_name AS candidateFullName, ja.applied_at AS appliedAt,
                           pending_rej.changed_at AS rejectedAt, r.requested_at AS requestedAt, r.reason AS reason,
                           r.status AS status, r.decided_at AS decidedAt, r.hr_response AS hrResponse
                    FROM application_reconsideration_requests r
                    JOIN job_applications ja ON ja.id = r.application_id
                    JOIN users u ON u.id = ja.candidate_id
                    """
                            + PENDING_REJECTED_LATERAL
                            + """
                    WHERE ja.job_id = :jobId
                      AND (CAST(:status AS varchar) IS NULL OR r.status = CAST(:status AS varchar))
                    ORDER BY r.requested_at ASC, r.application_id ASC
                    """,
            nativeQuery = true)
    List<ReconsiderationHrRow> findHrRowsByJob(@Param("jobId") UUID jobId, @Param("status") String status);

    // H1 openCount - khong phu thuoc bo loc.
    @Query(
            value =
                    """
                    SELECT count(*) FROM application_reconsideration_requests r
                    JOIN job_applications ja ON ja.id = r.application_id
                    WHERE ja.job_id = :jobId AND r.status = 'OPEN'
                    """,
            nativeQuery = true)
    long countOpenByJob(@Param("jobId") UUID jobId);
}
