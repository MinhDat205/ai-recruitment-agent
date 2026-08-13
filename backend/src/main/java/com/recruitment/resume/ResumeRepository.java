package com.recruitment.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    Optional<Resume> findByIdAndCandidateId(UUID id, UUID candidateId);

    Optional<Resume> findByCandidateIdAndIsPrimaryTrue(UUID candidateId);

    boolean existsByCandidateId(UUID candidateId);
}
