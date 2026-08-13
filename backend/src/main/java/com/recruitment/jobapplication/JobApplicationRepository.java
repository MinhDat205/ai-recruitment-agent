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
}
