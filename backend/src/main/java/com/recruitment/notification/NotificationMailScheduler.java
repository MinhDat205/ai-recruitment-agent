package com.recruitment.notification;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Tat trong test qua app.notification.enabled=false (application-test.yml) - mau
// AggregationScheduler/ResumeParsingScheduler, tranh scheduler tu tick gay nhieu cac
// @SpringBootTest khac dung chung context cache. Test goi thang orchestrator.processOne(id).
@Component
@ConditionalOnProperty(name = "app.notification.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationMailScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailScheduler.class);

    private final NotificationRepository notificationRepository;
    private final NotificationMailOrchestrator orchestrator;
    private final int batchSize;

    public NotificationMailScheduler(
            NotificationRepository notificationRepository,
            NotificationMailOrchestrator orchestrator,
            @Value("${app.notification.batch-size:10}") int batchSize) {
        this.notificationRepository = notificationRepository;
        this.orchestrator = orchestrator;
        this.batchSize = batchSize;
    }

    // KHONG @Transactional - xem CLAUDE.md muc 3c. orchestrator.processOne() tu xu ly loi cua
    // chinh no, nhung van bat them o day de mot email loi khong lam gian doan viec quet cac dong
    // con lai trong cung dot.
    @Scheduled(fixedDelayString = "${app.notification.poll-interval-ms:5000}")
    public void pollPendingEmails() {
        List<Notification> pending =
                notificationRepository.findByEmailStatus(EmailStatus.PENDING, PageRequest.of(0, batchSize));
        for (Notification notification : pending) {
            try {
                orchestrator.processOne(notification.getId());
            } catch (RuntimeException e) {
                log.debug("Loi khong bat duoc trong vong quet gui email thong bao: notificationId={}", notification.getId(), e);
            }
        }
    }
}
