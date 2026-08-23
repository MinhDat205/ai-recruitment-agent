package com.recruitment.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CvImprovementRequestRepository extends JpaRepository<CvImprovementRequest, UUID> {

    // Dot 5 (CvImprovementSuggestionService) - kiem da co request PENDING/RUNNING cho resume nay
    // chua truoc khi tao moi (phan hoi som, than thien - chot chan THAT van la
    // uq_cv_improvement_request_active o DB, xem V6). id lam khoa cuoi vi requestedAt la
    // transaction-scoped now() (CLAUDE.md muc 3c), nhieu request tao cung transaction (vd test) co
    // the trung requestedAt tuyet doi.
    Optional<CvImprovementRequest> findFirstByResumeIdAndStatusInOrderByRequestedAtDescIdDesc(
            UUID resumeId, List<CvImprovementRequestStatus> statuses);

    // Dot 5 - GET doc trang thai yeu cau GAN NHAT bat ke status (ke ca FAILED) khi chua co
    // cv_improvement_suggestions nao. Cung ly do id lam khoa cuoi nhu tren.
    Optional<CvImprovementRequest> findFirstByResumeIdOrderByRequestedAtDescIdDesc(UUID resumeId);

    // Dot 4 (CvImprovementScheduler) - quet lo PENDING theo dot, mau
    // ResumeRepository.findByParseStatus. OrderBy requestedAt Asc, id Asc BAT BUOC (khac
    // ResumeRepository.findByParseStatus khong can): requested_at la DEFAULT now() cua Postgres -
    // transaction-scoped (CLAUDE.md muc 3c), nhieu hang tao cung mot transaction (vd nhieu candidate
    // bam xin goi y gan nhau, hoac test seed) co the trung requested_at tuyet doi. Thieu khoa cuoi
    // IdAsc thi thu tu khong xac dinh, ket hop voi LIMIT cua Pageable se gay starvation - mot request
    // PENDING co the bi bo qua vo thoi han khi hang doi dai hon batchSize.
    List<CvImprovementRequest> findByStatusOrderByRequestedAtAscIdAsc(CvImprovementRequestStatus status, Pageable pageable);

    // Claim bang UPDATE co dieu kien, kiem rowcount ben ngoai (CLAUDE.md muc 3c) - mau
    // ResumeRepository.claimForProcessing. KHONG dung SELECT FOR UPDATE SKIP LOCKED vi giu
    // transaction mo trong luc cho LLM.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE cv_improvement_requests SET status = 'RUNNING' WHERE id = :id AND status = 'PENDING'",
            nativeQuery = true)
    int claimForProcessing(@Param("id") UUID id);
}
