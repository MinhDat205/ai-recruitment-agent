package com.recruitment.scoring;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreExplanationAttemptRepository extends JpaRepository<ScoreExplanationAttempt, UUID> {

    Optional<ScoreExplanationAttempt> findByScoringRunId(UUID scoringRunId);

    // D4 (mo rong GET /api/hr/jobs/{jobId}/applications, Dot 4) - so lan da thu cua NHIEU lot DONE
    // cung mot luc, tranh N+1 - cung mau ScoreExplanationRepository.findByScoringRunIdIn. Chi CAC
    // lot DONE chua co score_explanations moi can tra cuu gia tri nay (xem
    // ApplicationOwnerService.toRankableRow) - lot da co bao cao thi khong con quan tam so lan thu.
    List<ScoreExplanationAttempt> findByScoringRunIdIn(Collection<UUID> scoringRunIds);

    // Nguong dung cho scheduler (Dot 3, Q5 ke hoach D4 dot duyet lai) - mot lot da dat toi
    // maxAttempts khong con duoc ScoreExplanationScheduler nhat lai (dieu kien loai tru se dat trong
    // truy van cua Dot 3).
    boolean existsByScoringRunIdAndAttemptCountGreaterThanEqual(UUID scoringRunId, int attemptCount);

    // Upsert NGUYEN TU bang MOT cau lenh DB - KHONG doc-roi-ghi o tang Java (doc attempt_count, +1,
    // save lai): hai nhip poll gan nhau doc CUNG mot gia tri cu se lam mat mot lan tang (lost
    // update) - dung dung nguyen tac "chot chan phai o DB" (CLAUDE.md muc 4), ke ca khi day khong
    // phai rang buoc "duy nhat mot X dang hoat dong" ma la mot bo dem can chinh xac duoi tai dong
    // thoi. ON CONFLICT la lua chon DAU TIEN dung cu phap nay trong du an (cac cho khac dung UPDATE
    // co dieu kien + saveAndFlush bat DataIntegrityViolationException, vd
    // ScoringRunRepository.finishAggregation) - phu hop hon o day vi muc dich la TANG mot bo dem,
    // khong phai "ghi mot lan duy nhat roi khoa" nhu cac truong hop truoc do trong du an.
    @Modifying(clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO score_explanation_attempts (id, scoring_run_id, attempt_count, last_error, last_attempted_at)
                    VALUES (gen_random_uuid(), :scoringRunId, 1, :lastError, now())
                    ON CONFLICT (scoring_run_id) DO UPDATE SET
                        attempt_count = score_explanation_attempts.attempt_count + 1,
                        last_error = EXCLUDED.last_error,
                        last_attempted_at = now()
                    """,
            nativeQuery = true)
    void recordFailedAttempt(@Param("scoringRunId") UUID scoringRunId, @Param("lastError") String lastError);
}
