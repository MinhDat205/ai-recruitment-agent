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

    // Truy van similarity chinh - hai buoc theo dung ban da duyet o Plan Mode muc D (sau review):
    // vector CV duoc GOI TU BEN NGOAI nhu mot THAM SO CO DINH (queryVector, da la text dang
    // "[0.1,0.2,...]" - xem ResumeParsedDataRepository.findEmbeddingTextByResumeId), KHONG lay bang
    // subquery long trong chinh cau nay - subquery long lam ve phai cua <=> tro thanh mot bieu thuc
    // phu thuoc tung hang thay vi hang so, khien planner Postgres khong con nhan dien duoc dang
    // "ORDER BY cot_vector <=> hang_so LIMIT n" de can nhac dung HNSW index (bang chung: da doc
    // bytecode PgVectorStore that, xem Plan Mode muc D).
    //
    // 1 - (embedding <=> queryVector): <=> la cosine DISTANCE (khop vector_cosine_ops cua
    // idx_job_emb_vec), doi sang similarity de khop y nghia cot job_recommendations.similarity_score.
    // Loc OPEN + deleted_at IS NULL + deadline chua qua giong het JobRepository.searchPublicJobs.
    // ORDER BY ket thuc bang j.id ASC - khoa cuoi duy nhat dung quy uoc chung cua du an.
    //
    // "< 'Infinity'::float8" - PHONG THU CHIEU SAU chan gia tri NaN, dung du EmbeddingService.embed
    // da chan vector suy bien tai nguon (khong con duong nao MOI tao ra NaN o day nua). Van giu lai
    // vi du lieu CU co the da nam trong job_embeddings TRUOC KHI guard do ton tai. Da kiem chung
    // thuc nghiem tren Postgres 17 that (sau yeu cau review): 'NaN'::float8 >= 0.4 tra ve TRUE (khac
    // chuan IEEE754 - Postgres coi NaN LON HON moi so khac khi so sanh thu tu), nen mot job vector-0
    // se vuot dieu kien >= :minSimilarity va lot vao ket qua. 'NaN'::float8 = 'NaN'::float8 CUNG tra
    // ve true (meo WHERE x = x quen thuoc KHONG loc duoc NaN o Postgres) va isnan() KHONG ton tai
    // cho ca float8 lan numeric trong Postgres 17 (da thu, loi "function isnan(...) does not exist").
    // 'NaN'::float8 < 'Infinity'::float8 tra ve FALSE - day la dieu kien duy nhat da kiem chung dung:
    // moi similarity that (luon nam trong [-1,1]) chac chan < Infinity, chi rieng NaN thi khong.
    @Query(
            value =
                    """
                    SELECT j.id AS jobId,
                           1 - (je.embedding <=> CAST(:queryVector AS vector)) AS similarityScore
                    FROM job_embeddings je
                    JOIN jobs j ON j.id = je.job_id
                    WHERE j.status = 'OPEN'
                      AND j.deleted_at IS NULL
                      AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                      AND (1 - (je.embedding <=> CAST(:queryVector AS vector))) < 'Infinity'::float8
                      AND (1 - (je.embedding <=> CAST(:queryVector AS vector))) >= :minSimilarity
                    ORDER BY je.embedding <=> CAST(:queryVector AS vector), j.id ASC
                    LIMIT :topN
                    """,
            nativeQuery = true)
    List<JobMatchView> findTopMatchingJobs(
            @Param("queryVector") String queryVector,
            @Param("minSimilarity") double minSimilarity,
            @Param("topN") int topN);
}
