package com.recruitment.cvbuilder;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvBuilderDocumentRepository extends JpaRepository<CvBuilderDocument, UUID> {

    Optional<CvBuilderDocument> findByResumeId(UUID resumeId);
}
