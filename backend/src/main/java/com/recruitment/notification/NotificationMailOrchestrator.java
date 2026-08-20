package com.recruitment.notification;

import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// KHONG method nao o day @Transactional (xem CLAUDE.md muc 3c va PHASES.md muc E2 "AI hay lam
// sai") - gui email la I/O ngoai, giu transaction mo qua no se cach ly ket noi DB suot thoi gian
// cho SMTP va can pool khi nhieu thong bao cho gui cung luc, dung loi da duoc canh bao truoc.
//
// KHONG claim truoc khi gui, khac D1/D2/D3: notifications.email_status chi co dung 4 gia tri
// (PENDING/SENT/FAILED/SKIPPED - xem CHECK constraint V1__init_schema.sql), khong co trang thai
// trung gian kieu 'SENDING'/'RUNNING' de claim vao, va khong duoc them migration moi de them mot.
// Chot chan la UPDATE co dieu kien SAU KHI gui (markSent/markFailed), giong D3
// (ScoringRunRepository.finishAggregation) - khong phai vi phep tinh la ham thuan nhu D3, ma vi
// GIA DINH du an hien chi chay DUNG MOT instance (docker-compose.yml khong khai bao replicas/load
// balancer nao): @Scheduled(fixedDelayString=...) chay tuan tu trong 1 instance, nhip sau chi bat
// dau sau khi nhip truoc pollPendingEmails() return, nen khong co 2 nhip cung nhat 1 dong PENDING
// trong CUNG mot instance. Neu sau nay trien khai da instance, rui ro ly thuyet la GUI TRUNG mot
// email cho cung 1 thong bao (khong mat/sai du lieu nghiep vu - nghiep vu chinh da commit tu
// truoc, day chi la thong bao phu) - ghi nhan no o docs/ROADMAP.md muc chore/hardening thay vi tu
// them co che khoa (SELECT FOR UPDATE SKIP LOCKED bi cam dung ly do tren - giu transaction mo qua
// I/O ngoai).
@Component
public class NotificationMailOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailOrchestrator.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMailStateService stateService;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public NotificationMailOrchestrator(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            NotificationMailStateService stateService,
            JavaMailSender mailSender,
            @Value("${app.notification.mail-from:noreply@ai-recruitment-agent.local}") String mailFrom) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.stateService = stateService;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    public void processOne(UUID notificationId) {
        try {
            doProcess(notificationId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi cung, mau AggregationOrchestrator.processOne - loi khong luong
            // truoc (vd Notification/User bi xoa giua chung).
            log.debug("Loi khong luong truoc trong luc gui email thong bao: notificationId={}", notificationId, e);
            stateService.markFailed(notificationId);
        }
    }

    private void doProcess(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElseThrow();
        User recipient = userRepository.findById(notification.getUserId()).orElseThrow();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(recipient.getEmail());
        message.setSubject(notification.getTitle());
        message.setText(notification.getBody());

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // Bat rieng MailException quanh dung lenh gui - tranh loi SMTP roi xuong luoi an toan
            // ngoai cung o processOne() va bi log 2 lan cho cung mot nguyen nhan.
            log.debug("Gui email thong bao that bai: notificationId={}", notificationId, e);
            stateService.markFailed(notificationId);
            return;
        }

        stateService.markSent(notificationId);
    }
}
