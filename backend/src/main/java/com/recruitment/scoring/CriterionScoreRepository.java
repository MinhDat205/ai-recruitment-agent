package com.recruitment.scoring;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriterionScoreRepository extends JpaRepository<CriterionScore, UUID> {
}
