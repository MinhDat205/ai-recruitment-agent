package com.recruitment.screening;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreeningQuestionRepository extends JpaRepository<ScreeningQuestion, UUID> {

    // Khoa cuoi id de thu tu on dinh khi display_order trung (vd sau xoa mem roi them).
    List<ScreeningQuestion> findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(UUID jobId);

    Optional<ScreeningQuestion> findByIdAndJobIdAndDeletedAtIsNull(UUID id, UUID jobId);

    long countByJobIdAndDeletedAtIsNull(UUID jobId);

    @Query(
            value = "SELECT COALESCE(MAX(display_order), 0) FROM job_screening_questions "
                    + "WHERE job_id = :jobId AND deleted_at IS NULL",
            nativeQuery = true)
    int findMaxActiveDisplayOrder(@Param("jobId") UUID jobId);

    // R-Q10, R-Q11 - CHINH cau nay vua tang revision vua khoa hang jobs toi khi transaction ket thuc: transaction
    // thu hai cung Job dung o day cho toi khi transaction thu nhat commit/rollback. Phai chay SAU kiem quyen/kiem dau
    // vao va TRUOC moi buoc dem/tim/ghi cau hoi. clearAutomatically: xoa persistence context de khong ai dung lai
    // entity Job da nap truoc cau UPDATE (R-Q10a). Cot screening_revision khong map vao entity Job (muc 4.2).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE jobs SET screening_revision = screening_revision + 1 WHERE id = :jobId",
            nativeQuery = true)
    int incrementRevision(@Param("jobId") UUID jobId);

    @Query(value = "SELECT screening_revision FROM jobs WHERE id = :jobId", nativeQuery = true)
    int findRevision(@Param("jobId") UUID jobId);

    // FR-U09 R-R3 - khoa hang jobs (FOR SHARE) toi khi transaction nop don ket thuc: cau UPDATE ... screening_revision
    // cua HR (incrementRevision) phai cho; HR dang giu khoa thi cau nay cho HR commit roi doc gia tri DA commit. Hai lan
    // nop don song song khong chan nhau. CHI goi trong transaction GHI - Postgres bao loi "cannot execute SELECT FOR SHARE
    // in a read-only transaction" (L2); F1 (readOnly) dung findRevision. SELECT nen KHONG @Modifying; khong @Lock.
    @Query(value = "SELECT screening_revision FROM jobs WHERE id = :jobId FOR SHARE", nativeQuery = true)
    int findRevisionForShare(@Param("jobId") UUID jobId);
}
