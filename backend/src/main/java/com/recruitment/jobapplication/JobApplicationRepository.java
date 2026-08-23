package com.recruitment.jobapplication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    // Ownership check: don khong thuoc ve candidateId dang dang nhap -> Optional rong -> 404,
    // giong het pattern ResumeRepository.findByIdAndCandidateId dung trong ApplicationService.apply.
    Optional<JobApplication> findByIdAndCandidateId(UUID id, UUID candidateId);

    // GET /api/hr/jobs/{jobId}/applications (Dot 5, ApplicationOwnerService) - danh sach don cua
    // MOT job, moi nop gan day nhat truoc.
    List<JobApplication> findByJobIdOrderByAppliedAtDesc(UUID jobId);

    // Entity khong co @ManyToOne (xem ghi chu o JobApplication/Job) nen phai join tuong minh bang
    // native SQL, giong phong cach JobRepository.searchPublicJobs/findOpenJobById. Alias phai khop
    // dung ten getter cua ApplicationSummaryView.
    @Query(
            value =
                    """
                    SELECT a.id AS id, a.job_id AS jobId, j.title AS jobTitle, c.name AS companyName,
                           a.status AS status, a.applied_at AS appliedAt, a.updated_at AS updatedAt
                    FROM job_applications a
                    JOIN jobs j ON j.id = a.job_id
                    JOIN companies c ON c.id = j.company_id
                    WHERE a.candidate_id = :candidateId
                    ORDER BY a.applied_at DESC
                    """,
            nativeQuery = true)
    List<ApplicationSummaryView> findSummariesByCandidateId(@Param("candidateId") UUID candidateId);

    // Dashboard F3 (FR-H08) - phan bo don theo TRANG THAI HIEN TAI, gioi han theo cong ty (join
    // jobs), loai job da xoa mem. idx_app_status(job_id, status) khong bao trum company_id nhung
    // idx_jobs_company + idx_app_job du de Postgres join hop ly voi so luong job/don o quy mo mot
    // cong ty.
    @Query(
            value =
                    """
                    SELECT ja.status AS status, COUNT(*) AS count
                    FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                    GROUP BY ja.status
                    """,
            nativeQuery = true)
    List<StatusCountView> countByStatusForCompany(@Param("companyId") UUID companyId);

    // Dashboard F3 (FR-H08) - pheu chuyen doi. Dem theo "DA TUNG dat trang thai" doc tu
    // application_status_history.to_status (KHONG phai job_applications.status hien tai) - don
    // duoc moi phong van roi ung vien rut don (WITHDRAWN) van phai tinh la "da tung duoc moi"
    // (FR-U06, PHASES.md F3 "AI hay lam sai": loai don WITHDRAWN khoi thong ke la SAI). Dung
    // FILTER (khong phai LEFT JOIN) de khong nhan dong khi mot don co nhieu ban ghi lich su cung
    // to_status (ly thuyet, khong gay dem trung).
    @Query(
            value =
                    """
                    SELECT
                        COUNT(*) AS totalApplications,
                        COUNT(*) FILTER (WHERE EXISTS (
                            SELECT 1 FROM application_status_history ash
                            WHERE ash.application_id = ja.id AND ash.to_status = 'INTERVIEW_INVITED'
                        )) AS everInvited,
                        COUNT(*) FILTER (WHERE EXISTS (
                            SELECT 1 FROM application_status_history ash
                            WHERE ash.application_id = ja.id AND ash.to_status = 'HIRED'
                        )) AS everHired
                    FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                    """,
            nativeQuery = true)
    FunnelCountsView countFunnelForCompany(@Param("companyId") UUID companyId);

    // Dashboard F3 (FR-H08) - danh sach ung vien TOAN CONG TY, phan trang THAT o tang SQL (khac
    // ApplicationOwnerService.listApplications cua D3 tra ve List khong phan trang cho MOT job).
    // Nhanh KHONG loc tieu chi. LEFT JOIN LATERAL latest_done lay dung luot DONE MOI NHAT cua moi
    // don - cung ngu nghia voi ScoringRunRepository.findLatestDoneByApplicationIdIn (D3/D4) va
    // JobRepository.findJobPerformanceForCompany (Dot 2). ORDER BY trong LATERAL co them sr.id
    // DESC lam khoa cuoi - hai luot DONE cua CUNG mot don co the trung created_at (cung
    // transaction), thieu khoa cuoi thi LIMIT 1 chon hang nao trong nhom hoa la khong xac dinh (se
    // lam totalScore lech giua nhanh nay va searchCandidatesByCriterion cho CUNG mot don, xem bao
    // cao duyet Dot 3). CAST(:param AS type) bat buoc cho MOI tham so co the NULL - mau
    // JobRepository.searchPublicJobs, Postgres khong tu suy duoc kieu khi ve con lai la NULL.
    // ORDER BY ngoai cung ket thuc bang ja.id ASC (khoa duy nhat) - total_score/applied_at co the
    // trung nhau giua nhieu don, thieu khoa cuoi se lam phan trang lap/mat dong.
    @Query(
            value =
                    """
                    SELECT ja.id AS id, ja.job_id AS jobId, j.title AS jobTitle, ja.candidate_id AS candidateId,
                           ja.resume_id AS resumeId, ja.applied_at AS appliedAt, ja.status AS status,
                           latest_done.id AS latestScoringRunId, latest_done.total_score AS totalScore
                    FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    LEFT JOIN LATERAL (
                        SELECT sr.id, sr.total_score FROM scoring_runs sr
                        WHERE sr.application_id = ja.id AND sr.status = 'DONE'
                        ORDER BY sr.created_at DESC, sr.id DESC LIMIT 1
                    ) latest_done ON true
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                      AND (CAST(:jobId AS uuid) IS NULL OR ja.job_id = CAST(:jobId AS uuid))
                      AND (CAST(:status AS varchar) IS NULL OR ja.status = CAST(:status AS varchar))
                      AND (CAST(:minTotalScore AS numeric) IS NULL OR latest_done.total_score >= CAST(:minTotalScore AS numeric))
                      AND (CAST(:maxTotalScore AS numeric) IS NULL OR latest_done.total_score <= CAST(:maxTotalScore AS numeric))
                    ORDER BY latest_done.total_score DESC NULLS LAST, ja.applied_at ASC, ja.id ASC
                    """,
            countQuery =
                    """
                    SELECT count(*)
                    FROM job_applications ja
                    JOIN jobs j ON j.id = ja.job_id
                    LEFT JOIN LATERAL (
                        SELECT sr.id, sr.total_score FROM scoring_runs sr
                        WHERE sr.application_id = ja.id AND sr.status = 'DONE'
                        ORDER BY sr.created_at DESC, sr.id DESC LIMIT 1
                    ) latest_done ON true
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                      AND (CAST(:jobId AS uuid) IS NULL OR ja.job_id = CAST(:jobId AS uuid))
                      AND (CAST(:status AS varchar) IS NULL OR ja.status = CAST(:status AS varchar))
                      AND (CAST(:minTotalScore AS numeric) IS NULL OR latest_done.total_score >= CAST(:minTotalScore AS numeric))
                      AND (CAST(:maxTotalScore AS numeric) IS NULL OR latest_done.total_score <= CAST(:maxTotalScore AS numeric))
                    """,
            nativeQuery = true)
    Page<CandidateSearchRow> searchCandidates(
            @Param("companyId") UUID companyId,
            @Param("jobId") UUID jobId,
            @Param("status") String status,
            @Param("minTotalScore") BigDecimal minTotalScore,
            @Param("maxTotalScore") BigDecimal maxTotalScore,
            Pageable pageable);

    // Dashboard F3 (FR-H08) - nhanh CO loc tieu chi. Dan dat tu criterion_scores (loc
    // criterion_name_snapshot + score TRUOC) de Postgres chon idx_criterion_scores_filter
    // (criterion_name_snapshot, score DESC) - neu dan dat tu scoring_runs/job_applications roi loc
    // tieu chi bang EXISTS tuong quan scoring_run_id thi Postgres se uu tien idx_criterion_scores_run
    // thay vi idx_criterion_scores_filter (da loai phuong an do khi duyet plan). Subquery
    // "sr.id = (SELECT ... ORDER BY created_at DESC, id DESC LIMIT 1)" xac nhan day la luot DONE
    // MOI NHAT cua don (id DESC lam khoa cuoi - cung ly do voi searchCandidates o tren, tranh chon
    // luot khac nhau giua hai nhanh cho CUNG mot don khi trung created_at) - tranh loc trung mot
    // luot DONE cu hon. criterionName/minCriterionScore KHONG can CAST vi tang goi
    // (ApplicationSearchService) dam bao khong NULL o day (validate ca hai phai di cung nhau, xem
    // service). NULLS LAST o ORDER BY ngoai cung la vo hai nhung khong bao gio kich hoat: JOIN nay
    // la INNER JOIN voi sr.status = 'DONE', ma total_score luon NOT NULL khi status = 'DONE' (chi
    // finishAggregation moi ghi total_score, luon di cung status='DONE') - giu lai de dong nhat cu
    // phap voi searchCandidates, khong phai vi co gia tri NULL that su can xu ly.
    @Query(
            value =
                    """
                    SELECT ja.id AS id, ja.job_id AS jobId, j.title AS jobTitle, ja.candidate_id AS candidateId,
                           ja.resume_id AS resumeId, ja.applied_at AS appliedAt, ja.status AS status,
                           sr.id AS latestScoringRunId, sr.total_score AS totalScore
                    FROM criterion_scores cs
                    JOIN scoring_runs sr ON sr.id = cs.scoring_run_id AND sr.status = 'DONE'
                    JOIN job_applications ja ON ja.id = sr.application_id
                    JOIN jobs j ON j.id = ja.job_id
                    WHERE cs.criterion_name_snapshot = :criterionName
                      AND cs.score >= :minCriterionScore
                      AND j.company_id = :companyId AND j.deleted_at IS NULL
                      AND sr.id = (
                          SELECT sr2.id FROM scoring_runs sr2
                          WHERE sr2.application_id = ja.id AND sr2.status = 'DONE'
                          ORDER BY sr2.created_at DESC, sr2.id DESC LIMIT 1
                      )
                      AND (CAST(:jobId AS uuid) IS NULL OR ja.job_id = CAST(:jobId AS uuid))
                      AND (CAST(:status AS varchar) IS NULL OR ja.status = CAST(:status AS varchar))
                      AND (CAST(:minTotalScore AS numeric) IS NULL OR sr.total_score >= CAST(:minTotalScore AS numeric))
                      AND (CAST(:maxTotalScore AS numeric) IS NULL OR sr.total_score <= CAST(:maxTotalScore AS numeric))
                    ORDER BY sr.total_score DESC NULLS LAST, ja.applied_at ASC, ja.id ASC
                    """,
            countQuery =
                    """
                    SELECT count(*)
                    FROM criterion_scores cs
                    JOIN scoring_runs sr ON sr.id = cs.scoring_run_id AND sr.status = 'DONE'
                    JOIN job_applications ja ON ja.id = sr.application_id
                    JOIN jobs j ON j.id = ja.job_id
                    WHERE cs.criterion_name_snapshot = :criterionName
                      AND cs.score >= :minCriterionScore
                      AND j.company_id = :companyId AND j.deleted_at IS NULL
                      AND sr.id = (
                          SELECT sr2.id FROM scoring_runs sr2
                          WHERE sr2.application_id = ja.id AND sr2.status = 'DONE'
                          ORDER BY sr2.created_at DESC, sr2.id DESC LIMIT 1
                      )
                      AND (CAST(:jobId AS uuid) IS NULL OR ja.job_id = CAST(:jobId AS uuid))
                      AND (CAST(:status AS varchar) IS NULL OR ja.status = CAST(:status AS varchar))
                      AND (CAST(:minTotalScore AS numeric) IS NULL OR sr.total_score >= CAST(:minTotalScore AS numeric))
                      AND (CAST(:maxTotalScore AS numeric) IS NULL OR sr.total_score <= CAST(:maxTotalScore AS numeric))
                    """,
            nativeQuery = true)
    Page<CandidateSearchRow> searchCandidatesByCriterion(
            @Param("companyId") UUID companyId,
            @Param("criterionName") String criterionName,
            @Param("minCriterionScore") BigDecimal minCriterionScore,
            @Param("jobId") UUID jobId,
            @Param("status") String status,
            @Param("minTotalScore") BigDecimal minTotalScore,
            @Param("maxTotalScore") BigDecimal maxTotalScore,
            Pageable pageable);

    // Dashboard F3 (FR-H08) - danh sach ten tieu chi PHAN BIET trong pham vi cong ty, do dropdown
    // bo loc criterionName o frontend. Khong can khoa cuoi ORDER BY: DISTINCT tren 1 cot dam bao
    // moi dong da la duy nhat, khong co dong nao "dong hang" can phan biet them.
    @Query(
            value =
                    """
                    SELECT DISTINCT cs.criterion_name_snapshot
                    FROM criterion_scores cs
                    JOIN scoring_runs sr ON sr.id = cs.scoring_run_id
                    JOIN job_applications ja ON ja.id = sr.application_id
                    JOIN jobs j ON j.id = ja.job_id
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                    ORDER BY cs.criterion_name_snapshot
                    """,
            nativeQuery = true)
    List<String> findDistinctCriterionNamesForCompany(@Param("companyId") UUID companyId);
}
