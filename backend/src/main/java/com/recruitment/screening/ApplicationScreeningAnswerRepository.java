package com.recruitment.screening;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationScreeningAnswerRepository extends JpaRepository<ApplicationScreeningAnswer, UUID> {

    // uq_screening_answer_order (application_id, display_order) - display_order khong trung trong mot don.
    List<ApplicationScreeningAnswer> findByApplicationIdOrderByDisplayOrderAsc(UUID applicationId);

    // L3 cua FR-H10 - doc cot bang native query cho MOT don (S6/F3). Tu FR-U09, entity JobApplication DA map
    // screening_captured_at (JobApplication.screeningCapturedAt); noi da nap san JobApplication (vd FR-H14) doc thang
    // tu entity, khong goi them cau nay. null = NOT_CAPTURED. Chi goi sau khi HrApplicationAccess da xac nhan don ton tai.
    @Query(value = "SELECT screening_captured_at FROM job_applications WHERE id = :applicationId", nativeQuery = true)
    Instant findScreeningCapturedAt(@Param("applicationId") UUID applicationId);

    // FR-H14 (REQUIREMENT muc 4.6, cau 13) - ban chup cua NHIEU don trong MOT cau (bang so sanh), tranh N+1.
    // display_order tang dan trong tung don (uq_screening_answer_order).
    List<ApplicationScreeningAnswer> findByApplicationIdInOrderByApplicationIdAscDisplayOrderAsc(
            Collection<UUID> applicationIds);
}
