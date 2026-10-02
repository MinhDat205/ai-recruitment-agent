package com.recruitment.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    Optional<CandidateProfile> findByUserId(UUID userId);

    // R-E4 - chi quet ho so CHUA co embedding VA van ban dai dien (R-E1) khac rong, loc ngay trong
    // SQL (khong quet roi bo qua trong Java). ORDER BY updated_at ASC, id ASC - ho so cho lau nhat
    // uu tien truoc, id la khoa cuoi duy nhat (quy uoc chung cua du an).
    @Query(
            value =
                    """
                    SELECT id FROM candidate_profiles
                    WHERE embedding IS NULL
                      AND ((headline IS NOT NULL AND headline <> '')
                           OR cardinality(skills) > 0
                           OR (bio IS NOT NULL AND bio <> ''))
                    ORDER BY updated_at ASC, id ASC
                    """,
            nativeQuery = true)
    List<UUID> findIdsNeedingEmbedding(Pageable pageable);

    // R-E3 - dat embedding/model ve NULL khi van ban dai dien doi, goi tu CandidateProfileService.
    // update() trong CUNG transaction voi save(profile). flushAutomatically = true: flush thay doi
    // entity (headline/skills/bio...) truoc khi chay cau nay - clearAutomatically xoa persistence
    // context NGAY SAU KHI chay, neu thieu flushAutomatically thi thay doi entity chua flush se bi
    // mat (dung khuon touchAfterReparse, FR-C05 R-R5, ResumeParsedDataRepository.java).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE candidate_profiles SET embedding = NULL, embedding_model = NULL WHERE id = :id",
            nativeQuery = true)
    void clearEmbedding(@Param("id") UUID id);

    // R-E5 - ghi CO DIEU KIEN theo updated_at da doc TRUOC khi goi EmbeddingService (ngoai
    // transaction) - khac CO CHU DICH voi UPDATE khong dieu kien cua ResumeParsedDataRepository.
    // updateEmbedding (tien le CV) - sua dung rang buoc race da ghi no o ROADMAP. Rowcount 0 nghia
    // la ho so da bi sua lai giua luc doc va luc ghi xong -> bo qua, khong ghi gi, lan poll ke tiep
    // tu doc lai gia tri moi nhat va tinh lai.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE candidate_profiles SET embedding = CAST(:embeddingText AS vector), "
                    + "embedding_model = :model WHERE id = :id AND updated_at = :expectedUpdatedAt",
            nativeQuery = true)
    int updateEmbeddingIfUnchanged(
            @Param("id") UUID id,
            @Param("embeddingText") String embeddingText,
            @Param("model") String model,
            @Param("expectedUpdatedAt") Instant expectedUpdatedAt);
}
