package com.recruitment.jobdraft;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAiGenerationRepository extends JpaRepository<JobAiGeneration, UUID> {

    List<JobAiGeneration> findByJobIdOrderByPartAsc(UUID jobId);
}
