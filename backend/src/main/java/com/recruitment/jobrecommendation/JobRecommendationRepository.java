package com.recruitment.jobrecommendation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JobRecommendationRepository extends JpaRepository<JobRecommendation, UUID> {

    // Xoa toan bo cache cu cua MOT candidate truoc khi chen lai top-N moi (xem
    // JobRecommendationCacheService.refreshOne) - derived delete, entity map du moi cot nen
    // SELECT-roi-remove cua Spring Data hoat dong binh thuong, khong can native query.
    int deleteByCandidateId(UUID candidateId);

    // Doc cache cho endpoint (Plan Mode F1 muc F) - khoa cuoi jobId dung quy uoc ORDER BY chung cua
    // du an.
    List<JobRecommendation> findByCandidateIdOrderBySimilarityScoreDescJobIdAsc(UUID candidateId);

    // Round-robin theo cache cu nhat/chua co cache - candidate chua tung duoc lam moi (MAX tren tap
    // rong = NULL) duoc uu tien truoc (NULLS FIRST), sau do candidate co cache lau nhat. Subquery
    // long o day KHONG lien quan gi den van de HNSW index (khong co toan tu <=> nao, chi la
    // MAX(generated_at) thuong) - rang buoc "khong subquery long" cua Dot 5 chi ap dung cho truy
    // van similarity (JobEmbeddingRepository.findTopMatchingJobs), khong ap dung o day.
    @Query(
            value =
                    """
                    SELECT r.candidate_id FROM resumes r
                    JOIN resume_parsed_data rpd ON rpd.resume_id = r.id
                    WHERE r.is_primary = true AND rpd.embedding IS NOT NULL
                    ORDER BY (
                        SELECT MAX(jr.generated_at) FROM job_recommendations jr
                        WHERE jr.candidate_id = r.candidate_id
                    ) ASC NULLS FIRST, r.candidate_id ASC
                    """,
            nativeQuery = true)
    List<UUID> findCandidateIdsNeedingRefresh(Pageable pageable);
}
