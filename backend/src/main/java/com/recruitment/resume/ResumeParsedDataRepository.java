package com.recruitment.resume;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeParsedDataRepository extends JpaRepository<ResumeParsedData, UUID> {

    Optional<ResumeParsedData> findByResumeId(UUID resumeId);
}
