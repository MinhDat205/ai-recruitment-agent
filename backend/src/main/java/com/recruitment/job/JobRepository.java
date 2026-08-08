package com.recruitment.job;

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
                      AND (CAST(:titlePattern AS text) IS NULL OR j.title ILIKE CAST(:titlePattern AS text))
                      AND (CAST(:locationPattern AS text) IS NULL OR j.location ILIKE CAST(:locationPattern AS text))
                      AND (CAST(:categoryPattern AS text) IS NULL OR j.category ILIKE CAST(:categoryPattern AS text))
                    ORDER BY j.created_at DESC
                    """,
            countQuery =
                    """
                    SELECT count(*) FROM jobs j
                    WHERE j.status = 'OPEN' AND j.deleted_at IS NULL
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
            value = "SELECT * FROM jobs j WHERE j.id = :id AND j.status = 'OPEN' AND j.deleted_at IS NULL",
            nativeQuery = true)
    Optional<Job> findOpenJobById(@Param("id") UUID id);
}
