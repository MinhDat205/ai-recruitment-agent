package com.recruitment.scoring;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CriterionScoreRepository extends JpaRepository<CriterionScore, UUID> {

    // GET .../scoring-runs (Dot 5) - dem so tieu chi DA cham (criteriaScored) cho NHIEU luot cham
    // cung mot luc bang MOT query GROUP BY, tranh N+1 (dem tung luot rieng trong vong lap se la N+1
    // khi danh sach dai). scoringRunIds rong khong duoc goi (xem
    // ScoringRunService.countScoredCriteriaByRun) - JPQL "IN ()" rong la loi cu phap.
    @Query(
            "SELECT c.scoringRunId AS scoringRunId, COUNT(c) AS count FROM CriterionScore c "
                    + "WHERE c.scoringRunId IN :scoringRunIds GROUP BY c.scoringRunId")
    List<ScoringRunCriteriaCountView> countByScoringRunIdIn(@Param("scoringRunIds") Collection<UUID> scoringRunIds);
}
