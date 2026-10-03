package com.recruitment.resume;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeParsedDataRepository extends JpaRepository<ResumeParsedData, UUID> {

    Optional<ResumeParsedData> findByResumeId(UUID resumeId);

    // Chi quet CV CHINH (resumes.is_primary = true) - CV khong phai chinh khong bao gio duoc doc lai
    // o truy van similarity (chi dung CV chinh, xem Plan Mode F1 muc F), embed no la goi API thua,
    // ton tien vo ich (muc J). ORDER BY rpd.parsed_at ASC, rpd.id ASC: CV cho lau nhat duoc uu tien,
    // id la khoa cuoi duy nhat.
    @Query(
            value =
                    """
                    SELECT rpd.id FROM resume_parsed_data rpd
                    JOIN resumes r ON r.id = rpd.resume_id
                    WHERE rpd.embedding IS NULL AND r.is_primary = true
                    ORDER BY rpd.parsed_at ASC, rpd.id ASC
                    """,
            nativeQuery = true)
    List<UUID> findIdsNeedingEmbedding(Pageable pageable);

    // UPDATE thuong (khong UPSERT nhu job_embeddings): resume_parsed_data.id da chac chan ton tai
    // (lay tu findIdsNeedingEmbedding), khong co UNIQUE nao lien quan can xu ly xung dot.
    // embeddingText PHAI di qua EmbeddingTextFormat.toVectorText truoc khi truyen vao day - repository
    // nay khong tu validate dinh dang/so chieu (xem yeu cau review Dot 2).
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resume_parsed_data SET embedding = CAST(:embeddingText AS vector) WHERE id = :id",
            nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embeddingText") String embeddingText);

    // Doc truc tiep "CV nay da co embedding chua", KHONG phu thuoc is_primary - khac
    // findIdsNeedingEmbedding (loc is_primary=true trong SQL, khong dung de kiem "CV khong phai CV
    // chinh co bao gio duoc embed khong" vi query do luon loai CV do ra bat ke co embedding hay
    // khong, se chung minh sai). Them cho Dot 4 (F1), plain @Query khong @Modifying nen khong can
    // @Transactional o noi goi (mau JobEmbeddingRepository.findOpenJobIdsNeedingEmbedding, Dot 3).
    @Query(value = "SELECT (embedding IS NOT NULL) FROM resume_parsed_data WHERE id = :id", nativeQuery = true)
    boolean hasEmbedding(@Param("id") UUID id);

    // Lay embedding cua CV chinh dang chuoi text pgvector - dung cho JobRecommendationCandidateService
    // (FR-U15, nhanh CV cua truy van goi y; truoc day la JobRecommendationCacheService cua FR-U04,
    // da xoa o FR-U15 dot 4). KHONG can parse thanh float[] roi format lai: chuoi doc ra tu day dung
    // LUON lam tham so :queryVector cho JobRepository.findRankedMatchesByVector/
    // JobEmbeddingRepository.findTopMatchingJobs - vong Java chi chuyen tiep String, khong dung
    // Hibernate type nao dac biet cho vector (xem bang chung doc bytecode PgVectorStore that, F1).
    @Query(
            value =
                    "SELECT embedding::text FROM resume_parsed_data WHERE resume_id = :resumeId AND embedding IS NOT NULL",
            nativeQuery = true)
    Optional<String> findEmbeddingTextByResumeId(@Param("resumeId") UUID resumeId);

    // FR-C05 - phien ban schema (prompt_version) cua nhieu CV trong mot cau, cho API danh sach CV.
    interface PromptVersionView {
        UUID getResumeId();

        String getPromptVersion();
    }

    @Query("SELECT d.resumeId AS resumeId, d.promptVersion AS promptVersion FROM ResumeParsedData d "
            + "WHERE d.resumeId IN :resumeIds")
    List<PromptVersionView> findPromptVersionsByResumeIds(@Param("resumeIds") Collection<UUID> resumeIds);

    // FR-C05 R-R5 - phan con lai cua lan ghi trich xuat lai ma entity khong ghi duoc: parsed_at (cot
    // DB sinh, khong updatable qua entity) va embedding (khong map, xem ResumeParsedData). embedding =
    // NULL de ResumeEmbeddingScheduler tinh lai cho CV chinh (R-R6). raw_text KHONG nam trong cau nay.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE resume_parsed_data SET parsed_at = now(), embedding = NULL WHERE id = :id",
            nativeQuery = true)
    int touchAfterReparse(@Param("id") UUID id);

    // FR-C05 R-E9b - ban ghi chua tinh kinh nghiem (toan bo CV v1 co san). Dung
    // idx_parsed_data_experience_pending (V8); id la khoa cuoi.
    @Query(
            value = "SELECT id FROM resume_parsed_data WHERE experience_computed_at IS NULL "
                    + "ORDER BY parsed_at, id",
            nativeQuery = true)
    List<UUID> findIdsNeedingExperience(Pageable pageable);

    // Ghi ket qua kinh nghiem CHI KHI ban ghi van chua duoc tinh - UPDATE co dieu kien vua la buoc ghi
    // vua la claim (CLAUDE.md muc 3c): neu trich xuat lai (hoac mot vong quet khac) da ghi truoc thi
    // rowcount 0, khong de ket qua tinh tu du lieu cu de len.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resume_parsed_data SET experience_months = :months, "
                    + "experience_entries_counted = :counted, experience_entries_skipped = :skipped, "
                    + "experience_computed_at = :computedAt "
                    + "WHERE id = :id AND experience_computed_at IS NULL",
            nativeQuery = true)
    int writeExperienceIfPending(
            @Param("id") UUID id,
            @Param("months") Integer months,
            @Param("counted") int counted,
            @Param("skipped") int skipped,
            @Param("computedAt") Instant computedAt);
}
