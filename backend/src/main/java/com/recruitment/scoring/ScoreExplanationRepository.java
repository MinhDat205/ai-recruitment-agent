package com.recruitment.scoring;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreExplanationRepository extends JpaRepository<ScoreExplanation, UUID> {

    // D4 (mo rong GET /api/hr/jobs/{jobId}/applications, Dot 4) - bao cao giai thich cua NHIEU lot
    // DONE cung mot luc, tranh N+1 - cung mau CriterionScoreRepository.findByScoringRunIdIn.
    List<ScoreExplanation> findByScoringRunIdIn(Collection<UUID> scoringRunIds);

    // Dot 3 (ScoreExplanationOrchestrator) - kiem lot da co bao cao chua truoc khi goi LLM. KHONG
    // phai chot chan duy nhat de chong ghi trung - UNIQUE tren scoring_run_id (V1) moi la chot chan
    // that, xem comment trong ScoreExplanation.
    boolean existsByScoringRunId(UUID scoringRunId);
}
