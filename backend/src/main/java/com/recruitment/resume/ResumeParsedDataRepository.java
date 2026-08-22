package com.recruitment.resume;

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
}
