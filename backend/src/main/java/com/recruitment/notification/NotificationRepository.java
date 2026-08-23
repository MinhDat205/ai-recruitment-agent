package com.recruitment.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    // Dung cho poller gui email (NotificationMailScheduler) - quet lo, gioi han batch-size.
    List<Notification> findByEmailStatus(EmailStatus emailStatus, Pageable pageable);

    // UPDATE co dieu kien vua la buoc GHI vua la chot chan - khong claim rieng truoc khi gui (xem
    // NotificationMailOrchestrator: email_status chi co 4 gia tri, khong co trang thai trung gian
    // de claim vao). Khuon giong ScoringRunRepository.finishAggregation.
    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE notifications SET email_status = 'SENT' WHERE id = :id AND email_status = 'PENDING'",
            nativeQuery = true)
    int markSentIfPending(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE notifications SET email_status = 'FAILED' WHERE id = :id AND email_status = 'PENDING'",
            nativeQuery = true)
    int markFailedIfPending(@Param("id") UUID id);
}
