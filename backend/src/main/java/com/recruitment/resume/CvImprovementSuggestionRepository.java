package com.recruitment.resume;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CvImprovementSuggestionRepository extends JpaRepository<CvImprovementSuggestion, UUID> {

    // KHONG dung findByResumeId (so it) - V1 co y cho phep NHIEU ban ghi cung resume_id (xem
    // comment trong CvImprovementSuggestion.java), mot derived query "findBy" so it se nem
    // IncorrectResultSizeDataAccessException ngay khi co hang thu hai. Lay DUNG ban MOI NHAT:
    // generated_at DESC, id lam khoa cuoi vi generated_at la DEFAULT now() cua Postgres -
    // transaction-scoped (CLAUDE.md muc 3c), nhieu ban ghi tao cung mot transaction (vd test seed)
    // co the trung generated_at tuyet doi.
    Optional<CvImprovementSuggestion> findFirstByResumeIdOrderByGeneratedAtDescIdDesc(UUID resumeId);
}
