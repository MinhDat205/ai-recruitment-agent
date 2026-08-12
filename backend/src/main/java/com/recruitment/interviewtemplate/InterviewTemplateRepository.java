package com.recruitment.interviewtemplate;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewTemplateRepository extends JpaRepository<InterviewTemplate, UUID> {

    Optional<InterviewTemplate> findByJobId(UUID jobId);
}
