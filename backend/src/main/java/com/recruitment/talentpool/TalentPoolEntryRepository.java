package com.recruitment.talentpool;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// FR-H15. Hai nhom cau:
// - DOC (P6, P8): loc dong y ja.talent_pool_consent = TRUE (R-M6) - khong bao gio hien don da rut dong y.
// - GHI (P7 "hien co", P7 go/them, P9, R-C1): KHONG loc dong y (R-M5) - de go duoc muc lech.
// Moi cau go dat removed_at = statement_timestamp() cua DB (L4), de luon thoa CHECK removed_at >= added_at (V17):
// - KHONG nhan Instant tu JVM: added_at lay dong ho DB, JVM cham hon container thi removed_at < added_at.
// - KHONG dung now(): now() la thoi diem BAT DAU transaction. Transaction go bat dau o t0, P7 khac them muc o t1 > t0
//   (added_at = t1) va commit, cau go chay sau do voi now() = t0 < t1 -> vo CHECK -> 500.
public interface TalentPoolEntryRepository extends JpaRepository<TalentPoolEntry, UUID> {

    // P8 (DOC) - sap R-M7. Pageable KHONG mang Sort: native query da tu ORDER BY.
    @Query(
            value =
                    """
                    SELECT ja.id AS applicationId, u.full_name AS candidateName, j.id AS jobId, j.title AS jobTitle,
                           (j.deleted_at IS NOT NULL) AS jobDeleted, ja.status AS applicationStatus,
                           ja.applied_at AS appliedAt, e.added_at AS addedAt
                    FROM talent_pool_entries e
                    JOIN job_applications ja ON ja.id = e.application_id
                    JOIN jobs j ON j.id = ja.job_id
                    JOIN users u ON u.id = ja.candidate_id
                    WHERE e.pool_id = :poolId AND e.removed_at IS NULL AND ja.talent_pool_consent = TRUE
                    ORDER BY e.added_at DESC, e.id DESC
                    """,
            countQuery =
                    """
                    SELECT count(*)
                    FROM talent_pool_entries e
                    JOIN job_applications ja ON ja.id = e.application_id
                    WHERE e.pool_id = :poolId AND e.removed_at IS NULL AND ja.talent_pool_consent = TRUE
                    """,
            nativeQuery = true)
    Page<TalentPoolEntryRow> findReadableByPoolId(@Param("poolId") UUID poolId, Pageable pageable);

    // P6 (DOC) - kho chua xoa dang chua don, loc dong y: don khong ACTIVE luon ra rong (contains = false).
    @Query(
            value =
                    """
                    SELECT e.pool_id
                    FROM talent_pool_entries e
                    JOIN talent_pools p ON p.id = e.pool_id AND p.deleted_at IS NULL
                    JOIN job_applications ja ON ja.id = e.application_id
                    WHERE e.application_id = :applicationId AND e.removed_at IS NULL AND ja.talent_pool_consent = TRUE
                    """,
            nativeQuery = true)
    List<UUID> findReadablePoolIds(@Param("applicationId") UUID applicationId);

    // P7 "hien co" (GHI, R-P1) - KHONG loc dong y (R-M5).
    @Query(
            value =
                    """
                    SELECT e.pool_id
                    FROM talent_pool_entries e
                    JOIN talent_pools p ON p.id = e.pool_id
                    WHERE e.application_id = :applicationId AND e.removed_at IS NULL
                      AND p.deleted_at IS NULL AND p.company_id = :companyId
                    """,
            nativeQuery = true)
    List<UUID> findActivePoolIdsForWrite(
            @Param("applicationId") UUID applicationId, @Param("companyId") UUID companyId);

    // P7 them (GHI, R-P5, L1) - ON CONFLICT tren partial index uq_talent_pool_entry_active: hai P7 dong thoi cung them
    // mot muc (khoa FOR SHARE cua R-C2 khong chan lan nhau) deu thanh cong, dung mot dong hieu luc; KHONG de vi pham
    // unique thanh 500. Tra 1 = da them, 0 = da co san.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO talent_pool_entries (pool_id, application_id)
                    VALUES (:poolId, :applicationId)
                    ON CONFLICT (pool_id, application_id) WHERE removed_at IS NULL DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(@Param("poolId") UUID poolId, @Param("applicationId") UUID applicationId);

    // P7 go (GHI) - KHONG loc dong y (R-M5). statement_timestamp(), khong now()/Instant JVM (L4, xem dau file).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE talent_pool_entries SET removed_at = statement_timestamp()
                    WHERE application_id = :applicationId AND pool_id IN (:poolIds) AND removed_at IS NULL
                    """,
            nativeQuery = true)
    int removeFromPools(@Param("applicationId") UUID applicationId, @Param("poolIds") Collection<UUID> poolIds);

    // P9 (GHI) - KHONG loc dong y (R-M5). 0 dong -> TALENT_POOL_ENTRY_NOT_FOUND o tang service.
    // statement_timestamp(), khong now()/Instant JVM (L4, xem dau file).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE talent_pool_entries SET removed_at = statement_timestamp()
                    WHERE pool_id = :poolId AND application_id = :applicationId AND removed_at IS NULL
                    """,
            nativeQuery = true)
    int removeOne(@Param("poolId") UUID poolId, @Param("applicationId") UUID applicationId);

    // R-C1 (GHI) - go muc khoi MOI kho khi ung vien rut dong y, chay trong CHINH transaction
    // ApplicationService.revokeTalentPoolConsent, SAU cau UPDATE job_applications (cung thu tu khoa don -> muc voi P7).
    // statement_timestamp(), khong now()/Instant JVM (L4, xem dau file): muc P7 vua them sau khi transaction rut bat dau
    // co added_at lon hon now() cua transaction nay.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE talent_pool_entries SET removed_at = statement_timestamp()
                    WHERE application_id = :applicationId AND removed_at IS NULL
                    """,
            nativeQuery = true)
    int removeAllActiveByApplicationId(@Param("applicationId") UUID applicationId);
}
