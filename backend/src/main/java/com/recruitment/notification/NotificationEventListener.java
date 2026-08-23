package com.recruitment.notification;

import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.notification.NotificationContentBuilder.Content;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 3 method dau dung @TransactionalEventListener(AFTER_COMMIT): ApplicationStatusService.changeStatus/
// ApplicationService.apply/withdraw publish TRONG transaction chinh, Spring hoan xu ly toi day chi
// khi transaction do commit thanh cong. Method cuoi (onAggregationFinished) dung @EventListener
// thuong: AggregationOrchestrator.doProcess publish NGOAI moi transaction (CLAUDE.md muc 3c),
// khong co gi de hoan.
//
// Loi tao thong bao (vd Job/User bi xoa giua chung, du hau nhu khong xay ra vi khong co hard
// delete - CLAUDE.md muc 2) KHONG duoc vang ra ngoai: nghiep vu chinh da commit va tra response cho
// nguoi dung roi, mot loi o day chi la mat mot dong thong bao, khong phai loi nghiep vu.
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public NotificationEventListener(
            JobRepository jobRepository,
            CompanyRepository companyRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    // AFTER_COMMIT chay SAU KHI transaction goc (vd ApplicationService.apply) da commit xong -
    // khong con transaction nao de "tham gia" (REQUIRED se khong hop le, Spring chan thang o
    // startup - da gap loi nay khi chay that). Phai REQUIRES_NEW de tu mo transaction rieng cho
    // dong INSERT notifications.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationStatusChanged(ApplicationStatusChangedEvent event) {
        try {
            Job job = jobRepository.findById(event.jobId()).orElseThrow();
            Content content = NotificationContentBuilder.forStatusChanged(job, event.toStatus());
            save(event.candidateId(), NotificationType.APPLICATION_STATUS_CHANGED, content, event.applicationId());
        } catch (RuntimeException e) {
            log.error("Khong tao duoc thong bao doi trang thai don: applicationId={}", event.applicationId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        try {
            Job job = jobRepository.findById(event.jobId()).orElseThrow();
            Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
            User candidate = userRepository.findById(event.candidateId()).orElseThrow();
            Content content = NotificationContentBuilder.forApplicationSubmitted(job, candidate.getFullName());
            save(company.getOwnerId(), NotificationType.APPLICATION_SUBMITTED, content, event.applicationId());
        } catch (RuntimeException e) {
            log.error("Khong tao duoc thong bao don moi: applicationId={}", event.applicationId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationWithdrawn(ApplicationWithdrawnEvent event) {
        try {
            Job job = jobRepository.findById(event.jobId()).orElseThrow();
            Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
            User candidate = userRepository.findById(event.candidateId()).orElseThrow();
            Content content = NotificationContentBuilder.forApplicationWithdrawn(job, candidate.getFullName());
            save(company.getOwnerId(), NotificationType.APPLICATION_WITHDRAWN, content, event.applicationId());
        } catch (RuntimeException e) {
            log.error("Khong tao duoc thong bao rut don: applicationId={}", event.applicationId(), e);
        }
    }

    @EventListener
    @Transactional
    public void onAggregationFinished(AggregationFinishedEvent event) {
        try {
            Job job = jobRepository.findById(event.jobId()).orElseThrow();
            Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
            Content content = NotificationContentBuilder.forAggregationFinished(job);
            save(company.getOwnerId(), NotificationType.SCORING_FINISHED, content, event.applicationId());
        } catch (RuntimeException e) {
            log.error("Khong tao duoc thong bao cham diem xong: scoringRunId={}", event.scoringRunId(), e);
        }
    }

    private void save(UUID userId, NotificationType type, Content content, UUID applicationId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(content.title());
        n.setBody(content.body());
        n.setLink(content.link());
        n.setEntityType("APPLICATION");
        n.setEntityId(applicationId);
        n.setRead(false);
        n.setEmailStatus(EmailStatus.PENDING);
        notificationRepository.save(n);
    }
}
