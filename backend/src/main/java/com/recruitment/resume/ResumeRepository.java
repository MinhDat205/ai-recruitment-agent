package com.recruitment.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByCandidateIdOrderByUploadedAtDesc(UUID candidateId);

    Optional<Resume> findByIdAndCandidateId(UUID id, UUID candidateId);

    Optional<Resume> findByCandidateIdAndIsPrimaryTrue(UUID candidateId);

    boolean existsByCandidateId(UUID candidateId);

    // Dung cho poller quet lo (Pageable gioi han batch-size) - tra List thay vi Page vi khong can
    // count query, chi can mot lo gioi han de xu ly moi vong quet.
    List<Resume> findByParseStatus(ParseStatus parseStatus, Pageable pageable);

    // Claim bang UPDATE co dieu kien, kiem tra rowcount ben ngoai (xem CLAUDE.md muc 3c) - KHONG
    // dung SELECT FOR UPDATE SKIP LOCKED vi no giu transaction mo trong luc cho LLM.
    // clearAutomatically = true bat buoc: bulk UPDATE qua @Query di thang xuong SQL, khong tu dong
    // bo persistence context - thieu co nay, mot entity Resume da load truoc do trong cung
    // persistence context se tiep tuc hien thi parse_status cu trong bo nho du DB da doi.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE resumes SET parse_status = 'PROCESSING' WHERE id = :id AND parse_status = 'PENDING'",
            nativeQuery = true)
    int claimForProcessing(@Param("id") UUID id);
}
