package com.recruitment.screening;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationScreeningAnswerRepository extends JpaRepository<ApplicationScreeningAnswer, UUID> {

    // uq_screening_answer_order (application_id, display_order) - display_order khong trung trong mot don.
    List<ApplicationScreeningAnswer> findByApplicationIdOrderByDisplayOrderAsc(UUID applicationId);

    // L3 - doc cot bang native query, KHONG them field vao entity JobApplication (FR-U09 them khi lam duong ghi).
    // null = NOT_CAPTURED. Chi goi sau khi HrApplicationAccess da xac nhan don ton tai.
    @Query(value = "SELECT screening_captured_at FROM job_applications WHERE id = :applicationId", nativeQuery = true)
    Instant findScreeningCapturedAt(@Param("applicationId") UUID applicationId);
}
