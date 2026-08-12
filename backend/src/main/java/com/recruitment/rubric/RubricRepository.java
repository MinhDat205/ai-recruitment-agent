package com.recruitment.rubric;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {

    Optional<Rubric> findByJobId(UUID jobId);
}
