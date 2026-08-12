package com.recruitment.rubric;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, UUID> {

    List<RubricCriterion> findByRubricIdOrderByDisplayOrderAsc(UUID rubricId);

    @Query("SELECT COALESCE(SUM(c.weight), 0) FROM RubricCriterion c WHERE c.rubricId = :rubricId")
    BigDecimal sumWeightByRubricId(@Param("rubricId") UUID rubricId);
}
