package com.recruitment.job;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID> {

    // CAST(:param AS text) bat buoc o ca hai ve: Postgres khong tu suy duoc kieu tham so
    // khi ve con lai la NULL (ERROR: could not determine data type of parameter).
    @Query(
            value =
                    """
                    SELECT * FROM jobs j
                    WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
                      AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                      AND (CAST(:titlePattern AS text) IS NULL OR j.title ILIKE CAST(:titlePattern AS text))
                      AND (CAST(:locationPattern AS text) IS NULL OR j.location ILIKE CAST(:locationPattern AS text))
                      AND (CAST(:categoryPattern AS text) IS NULL OR j.category ILIKE CAST(:categoryPattern AS text))
                    ORDER BY j.created_at DESC
                    """,
            countQuery =
                    """
                    SELECT count(*) FROM jobs j
                    WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
                      AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                      AND (CAST(:titlePattern AS text) IS NULL OR j.title ILIKE CAST(:titlePattern AS text))
                      AND (CAST(:locationPattern AS text) IS NULL OR j.location ILIKE CAST(:locationPattern AS text))
                      AND (CAST(:categoryPattern AS text) IS NULL OR j.category ILIKE CAST(:categoryPattern AS text))
                    """,
            nativeQuery = true)
    Page<Job> searchPublicJobs(
            @Param("titlePattern") String titlePattern,
            @Param("locationPattern") String locationPattern,
            @Param("categoryPattern") String categoryPattern,
            Pageable pageable);

    @Query(
            value =
                    """
                    SELECT * FROM jobs j
                    WHERE j.id = :id AND j.status = 'OPEN' AND j.deleted_at IS NULL
                      AND (j.deadline IS NULL OR j.deadline >= CURRENT_DATE)
                    """,
            nativeQuery = true)
    Optional<Job> findOpenJobById(@Param("id") UUID id);

    Page<Job> findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    Page<Job> findByCompanyIdAndDeletedAtIsNullAndStatusOrderByCreatedAtDesc(
            UUID companyId, JobStatus status, Pageable pageable);

    // Dashboard F3 (FR-H08) - hieu suat tung chien dich (job) cua cong ty. LEFT JOIN job_applications
    // de job CHUA co don nao van xuat hien (totalApplications=0). LATERAL latest_done lay dung
    // luot DONE MOI NHAT cua moi don - cung ngu nghia voi
    // ScoringRunRepository.findLatestDoneByApplicationIdIn (D3/D4), khong phai luot moi nhat bat
    // ke trang thai. LATERAL ever_invited/ever_hired dem theo "DA TUNG dat trang thai" qua
    // application_status_history - CUNG nguyen tac voi
    // JobApplicationRepository.countFunnelForCompany, ap dung nhat quan cho ca hai cot trong bang
    // nay (Dot 2, quyet dinh #6 trong plan). GROUP BY liet ke ca j.created_at vi no chi dung o
    // ORDER BY, khong phai SELECT list.
    //
    // COUNT(ja.id)/COUNT(latest_done.id) (khong phai COUNT(*)) va COUNT(*) FILTER(...) (khong
    // phai COUNT(*) tran) - ca hai deu can thiet de job 0 don tra ve 0 chu khong phai 1 (da kiem
    // thuc nghiem tren Postgres 17 that, xem bao cao duyet Dot 2).
    //
    // ORDER BY j.created_at DESC, j.id ASC: created_at la transaction-scoped (now()), nhieu job
    // tao trong CUNG mot transaction (seed, test) se trung created_at tuyet doi - j.id lam khoa
    // cuoi DUY NHAT de thu tu bang on dinh qua nhieu lan goi (quy uoc chung cho moi ORDER BY trong
    // F3, xem CLAUDE.md muc 3c ve now() transaction-scoped).
    @Query(
            value =
                    """
                    SELECT j.id AS jobId, j.title AS title, j.status AS status,
                           j.recruitment_cycle AS recruitmentCycle,
                           COUNT(ja.id) AS totalApplications,
                           COUNT(latest_done.id) AS scoredApplications,
                           -- AVG tren numeric mo rong scale (da kiem thuc nghiem: AVG cua
                           -- NUMERIC(6,3) tra ve numeric voi 16 chu so thap phan), ROUND ve 3
                           -- cho khop scale that cua scoring_runs.total_score NUMERIC(6,3).
                           -- ROUND(NULL, 3) van la NULL nen khong doi hanh vi khi chua co luot
                           -- DONE nao - DUNG xoa ROUND nay o cac dot sau, no khong thua.
                           ROUND(AVG(latest_done.total_score), 3) AS averageScore,
                           COUNT(*) FILTER (WHERE ever_invited.hit IS NOT NULL) AS everInvitedCount,
                           COUNT(*) FILTER (WHERE ever_hired.hit IS NOT NULL) AS everHiredCount
                    FROM jobs j
                    LEFT JOIN job_applications ja ON ja.job_id = j.id
                    LEFT JOIN LATERAL (
                        SELECT sr.id, sr.total_score FROM scoring_runs sr
                        WHERE sr.application_id = ja.id AND sr.status = 'DONE'
                        ORDER BY sr.created_at DESC LIMIT 1
                    ) latest_done ON true
                    LEFT JOIN LATERAL (
                        SELECT 1 AS hit FROM application_status_history ash
                        WHERE ash.application_id = ja.id AND ash.to_status = 'INTERVIEW_INVITED' LIMIT 1
                    ) ever_invited ON true
                    LEFT JOIN LATERAL (
                        SELECT 1 AS hit FROM application_status_history ash
                        WHERE ash.application_id = ja.id AND ash.to_status = 'HIRED' LIMIT 1
                    ) ever_hired ON true
                    WHERE j.company_id = :companyId AND j.deleted_at IS NULL
                    GROUP BY j.id, j.title, j.status, j.recruitment_cycle, j.created_at
                    ORDER BY j.created_at DESC, j.id ASC
                    """,
            nativeQuery = true)
    List<JobPerformanceView> findJobPerformanceForCompany(@Param("companyId") UUID companyId);
}
