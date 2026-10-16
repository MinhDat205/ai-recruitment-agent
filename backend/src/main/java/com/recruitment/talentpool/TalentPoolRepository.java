package com.recruitment.talentpool;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// FR-H15. Dieu kien dong y (ja.talent_pool_consent = TRUE, R-M6) CHI dat o cau DOC (dem entryCount); dung chung bieu
// thuc voi TalentPoolConsentState.of (ACTIVE <=> co TRUE, a2/a3 cua REQUIREMENT).
public interface TalentPoolRepository extends JpaRepository<TalentPool, UUID> {

    // P1 (DOC) - kho chua xoa cua cong ty, sap R-K7; entryCount dem muc hieu luc theo R-M6.
    @Query(
            value =
                    """
                    SELECT p.id AS id, p.name AS name, p.created_at AS createdAt,
                           (SELECT count(*) FROM talent_pool_entries e
                              JOIN job_applications ja ON ja.id = e.application_id
                             WHERE e.pool_id = p.id AND e.removed_at IS NULL
                               AND ja.talent_pool_consent = TRUE) AS entryCount
                    FROM talent_pools p
                    WHERE p.company_id = :companyId AND p.deleted_at IS NULL
                    ORDER BY lower(p.name), p.id
                    """,
            nativeQuery = true)
    List<TalentPoolRow> findReadableByCompanyId(@Param("companyId") UUID companyId);

    // P3/P4 (DOC) - cung bieu thuc P1 cho MOT kho; goi SAU khi service da kiem quyen tren entity.
    @Query(
            value =
                    """
                    SELECT p.id AS id, p.name AS name, p.created_at AS createdAt,
                           (SELECT count(*) FROM talent_pool_entries e
                              JOIN job_applications ja ON ja.id = e.application_id
                             WHERE e.pool_id = p.id AND e.removed_at IS NULL
                               AND ja.talent_pool_consent = TRUE) AS entryCount
                    FROM talent_pools p
                    WHERE p.id = :id AND p.deleted_at IS NULL
                    """,
            nativeQuery = true)
    Optional<TalentPoolRow> findReadableById(@Param("id") UUID id);

    // R-K3, R-K4 - kiem truoc de tra 409 som; so lower() o DB giong uq_talent_pool_name_per_company (khong so o Java).
    // excludeId = null khi tao moi, = id kho khi doi ten (doi hoa thuong ten cua chinh minh duoc phep).
    @Query(
            value =
                    """
                    SELECT EXISTS (
                        SELECT 1 FROM talent_pools
                        WHERE company_id = :companyId AND deleted_at IS NULL AND lower(name) = lower(:name)
                          AND (CAST(:excludeId AS uuid) IS NULL OR id <> CAST(:excludeId AS uuid)))
                    """,
            nativeQuery = true)
    boolean existsActiveName(
            @Param("companyId") UUID companyId, @Param("name") String name, @Param("excludeId") UUID excludeId);

    // R-C2 - khoa cac kho trong poolIds (FOR SHARE, chan xoa kho dong thoi) toi khi transaction P7 ket thuc. Tra ca kho
    // da xoa / cua cong ty khac de service phan biet 404 va 403 (R-P2 buoc 5, 6). CHI goi trong transaction GHI -
    // Postgres cam FOR SHARE trong transaction chi doc (khuon ScreeningQuestionRepository.findRevisionForShare).
    @Query(
            value = "SELECT id AS id, company_id AS companyId, deleted_at AS deletedAt FROM talent_pools"
                    + " WHERE id IN (:ids) FOR SHARE",
            nativeQuery = true)
    List<PoolLockRow> lockForShare(@Param("ids") Collection<UUID> ids);
}
