package com.recruitment.notification;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho poller gui email - xem CLAUDE.md muc 3c. Khong goi qua self-invocation tu
// NotificationMailOrchestrator, phai qua bean nay de @Transactional di qua proxy Spring.
@Service
public class NotificationMailStateService {

    private final NotificationRepository notificationRepository;

    public NotificationMailStateService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void markSent(UUID notificationId) {
        notificationRepository.markSentIfPending(notificationId);
    }

    @Transactional
    public void markFailed(UUID notificationId) {
        notificationRepository.markFailedIfPending(notificationId);
    }
}
