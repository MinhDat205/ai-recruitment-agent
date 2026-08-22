package com.recruitment.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobEmbeddingRepository extends JpaRepository<JobEmbedding, UUID> {

    // Scope theo job_id (UNIQUE o DB - Optional la dung, khong bao gio co 2 hang) - dung cho test va
    // cho bat ky noi nao can kiem "job nay da co embedding chua" ma khong quet toan bang qua findAll().
    Optional<JobEmbedding> findByJobId(UUID jobId);

    // NOT EXISTS thay vi bang hang doi rieng - job_embeddings.job_id la UNIQUE, ban than mot hang
    // ton tai da la trang thai "xong" (xem Plan Mode F1, muc A). ORDER BY j.created_at ASC, j.id ASC:
    // job OPEN lau nhat chua co embedding duoc uu tien truoc (FIFO), j.id la khoa cuoi duy nhat dung
    // quy uoc chung cua du an.
    @Query(
            value =
                    """
                    SELECT j.id FROM jobs j
                    WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
                      AND NOT EXISTS (SELECT 1 FROM job_embeddings je WHERE je.job_id = j.id)
                    ORDER BY j.created_at ASC, j.id ASC
                    """,
            nativeQuery = true)
    List<UUID> findOpenJobIdsNeedingEmbedding(Pageable pageable);

    // UPSERT (khong INSERT thuong + bat DataIntegrityViolationException): hai luot poll trung nhau
    // (ly thuyet, xem Plan Mode F1 muc A - fixedDelay khong chay chong trong mot instance) chi ghi de
    // bang mot vector tuong duong, khong loi. Cau nay cung la co che "sinh lai" (muc B): JobOwnerService
    // goi deleteByJobId khi title/description doi, sau do upsertEmbedding ghi lai binh thuong.
    // embeddingText PHAI di qua EmbeddingTextFormat.toVectorText truoc khi truyen vao day - repository
    // nay khong tu validate dinh dang/so chieu (xem yeu cau review Dot 2).
    @Modifying(clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO job_embeddings (job_id, embedding, model)
                    VALUES (:jobId, CAST(:embeddingText AS vector), :model)
                    ON CONFLICT (job_id) DO UPDATE
                    SET embedding = EXCLUDED.embedding, model = EXCLUDED.model, created_at = now()
                    """,
            nativeQuery = true)
    void upsertEmbedding(
            @Param("jobId") UUID jobId, @Param("embeddingText") String embeddingText, @Param("model") String model);

    // Derived delete (khong phai native @Query): entity khong map cot embedding nen SELECT-roi-remove
    // cua Spring Data van doc duoc hang binh thuong (embedding NOT NULL chi rang buoc luc INSERT,
    // khong anh huong SELECT/DELETE). Dung cho muc B (sinh lai embedding khi HR sua title/description).
    int deleteByJobId(UUID jobId);
}
