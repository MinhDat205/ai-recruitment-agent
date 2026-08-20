package com.recruitment.jobapplication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
}
