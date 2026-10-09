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

// onApplicationStatusChanged/onApplicationSubmitted/onApplicationWithdrawn/onMessageSent dung
// @TransactionalEventListener(AFTER_COMMIT): ApplicationStatusService.changeStatus/ApplicationService.apply/
// withdraw/MessageService.send publish TRONG transaction chinh, Spring hoan xu ly toi day chi khi transaction
// do commit thanh cong. onAggregationFinished dung @EventListener thuong: AggregationOrchestrator.doProcess
// publish NGOAI moi transaction (CLAUDE.md muc 3c), khong co gi de hoan. onConversationRead (FR-C06 R-R3) cung
// @EventListener thuong nhung CO CHU DICH chay trong transaction cua M3 (xem comment tai method).
//
// Loi tao thong bao (tru onConversationRead) (vd Job/User bi xoa giua chung, du hau nhu khong xay ra vi khong co hard
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
            Content content = NotificationContentBuilder.forStatusChanged(job, event.toStatus(), event.applicationId());
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
            Content content = NotificationContentBuilder.forApplicationSubmitted(
                    job, candidate.getFullName(), event.applicationId());
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
            Content content = NotificationContentBuilder.forApplicationWithdrawn(
                    job, candidate.getFullName(), event.applicationId());
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
            Content content = NotificationContentBuilder.forAggregationFinished(job, event.applicationId());
            save(company.getOwnerId(), NotificationType.SCORING_FINISHED, content, event.applicationId());
        } catch (RuntimeException e) {
            log.error("Khong tao duoc thong bao cham diem xong: scoringRunId={}", event.scoringRunId(), e);
        }
    }

    // FR-C06 R-N1 - mau onApplicationSubmitted: AFTER_COMMIT + REQUIRES_NEW, loi KHONG vang ra ngoai (tin da
    // commit, mat mot thong bao khong lam hong viec gui tin). Nguoi nhan luon la BEN KIA - nguoi gui khong nhan
    // thong bao ve tin cua chinh minh.
    // R-N4 - gop thong bao: ben nhan con mot NEW_MESSAGE CHUA DOC cua chinh don nay thi khong tao them (va
    // khong co email them). Kiem o tang service, KHONG unique index: hai tin gan nhu dong thoi co the tao hai
    // thong bao - vo hai, chap nhan (ngoai le co chu dich voi CLAUDE.md muc 4, REQUIREMENT R-N4).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMessageSent(MessageSentEvent event) {
        try {
            Job job = jobRepository.findById(event.jobId()).orElseThrow();
            Company company = companyRepository.findById(job.getCompanyId()).orElseThrow();
            UUID recipientId;
            Content content;
            if (event.sentByHr()) {
                recipientId = event.candidateId();
                content = NotificationContentBuilder.forNewMessage(
                        job, company.getName(), true, event.excerpt(), event.applicationId());
            } else {
                User candidate = userRepository.findById(event.candidateId()).orElseThrow();
                recipientId = company.getOwnerId();
                content = NotificationContentBuilder.forNewMessage(
                        job, candidate.getFullName(), false, event.excerpt(), event.applicationId());
            }
            if (notificationRepository.existsByUserIdAndTypeAndEntityIdAndReadFalse(
                    recipientId, NotificationType.NEW_MESSAGE, event.applicationId())) {
                return;
            }
            save(recipientId, NotificationType.NEW_MESSAGE, content, event.applicationId());
        } catch (RuntimeException e) {
            log.error("Khong tao duoc thong bao tin nhan moi: applicationId={}", event.applicationId(), e);
        }
    }

    // FR-C06 R-R3 (muc 12 L2) - @EventListener THUONG: chay dong bo trong CUNG transaction cua M3 (khong
    // REQUIRES_NEW, khong AFTER_COMMIT), KHONG nuot loi - loi o day lam M3 that bai, khong de tin "da doc" ma
    // thong bao van "chua doc" (R-N4 se chan moi thong bao sau cua don).
    @EventListener
    public void onConversationRead(ConversationReadEvent event) {
        notificationRepository.markReadByUserAndTypeAndEntity(
                event.userId(), NotificationType.NEW_MESSAGE.name(), event.applicationId());
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
