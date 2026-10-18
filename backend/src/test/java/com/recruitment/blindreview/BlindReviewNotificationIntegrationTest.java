package com.recruitment.blindreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.jobapplication.ApplicationService;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.messaging.MessageService;
import com.recruitment.notification.MailTestConfiguration;
import com.recruitment.notification.Notification;
import com.recruitment.notification.NotificationMailOrchestrator;
import com.recruitment.notification.NotificationRepository;
import com.recruitment.notification.NotificationType;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

// FR-H16 T12 - thong bao va email gui HR (R-O1..R-O4, L1, L2). KHONG @Transactional toan lop: listener AFTER_COMMIT chi
// chay khi transaction cua ApplicationService/MessageService THAT SU commit (khuon NotificationEventListenerIntegrationTest).
// Moi test dung Job/don rieng nen khong dung nhau du du lieu khong rollback.
@Import({TestcontainersConfiguration.class, MailTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlindReviewNotificationIntegrationTest extends BlindReviewIntegrationTestSupport {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationMailOrchestrator mailOrchestrator;

    @Autowired
    private JavaMailSender mailSender;

    @BeforeEach
    void resetMailSender() {
        Mockito.reset(mailSender);
    }

    private UUID primaryResume(Candidate candidate) {
        Resume resume = new Resume();
        resume.setCandidateId(candidate.id());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        return resumeRepository.saveAndFlush(resume).getId();
    }

    private UUID apply(UUID jobId, Candidate candidate) {
        return applicationService
                .apply(candidate.id(), new ApplicationCreateRequest(jobId, primaryResume(candidate), true, null, null, null,
                        null))
                .id();
    }

    private List<Notification> hrNotifications(Hr hr, NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(hr.userId(), PageRequest.of(0, 50)).getContent()
                .stream()
                .filter(n -> n.getType() == type)
                .toList();
    }

    private Notification single(Hr hr, NotificationType type) {
        List<Notification> list = hrNotifications(hr, type);
        assertThat(list).as("thong bao %s", type).hasSize(1);
        return list.getFirst();
    }

    private String sentMailText(Notification notification) {
        mailOrchestrator.processOne(notification.getId());
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        return captor.getValue().getText();
    }

    private static void assertNoName(String text, Candidate candidate) {
        assertNoLeak(text, candidate);
    }

    private String jobTitle(UUID jobId) {
        return jobRepository.findById(jobId).orElseThrow().getTitle();
    }

    // ---- APPLICATION_SUBMITTED / APPLICATION_WITHDRAWN ----

    @Test
    void submittedNotificationOfMaskedApplicationUsesAlias() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, true);
        Candidate huy = candidate("Quốc Huy", "0900000111");

        UUID applicationId = apply(jobId, huy);

        Notification n = single(hr, NotificationType.APPLICATION_SUBMITTED);
        assertThat(n.getBody())
                .isEqualTo(CandidateAlias.of(applicationId) + " vừa ứng tuyển vị trí \"" + jobTitle(jobId) + "\"");
        assertNoName(n.getBody() + n.getTitle(), huy);
        String mail = sentMailText(n);
        assertThat(mail).isEqualTo(n.getBody());
        assertNoName(mail, huy);
    }

    @Test
    void withdrawnNotificationOfMaskedApplicationUsesAlias() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, true);
        Candidate huy = candidate("Quốc Huy", "0900000111");
        UUID applicationId = apply(jobId, huy);

        applicationService.withdraw(huy.id(), applicationId);

        Notification n = single(hr, NotificationType.APPLICATION_WITHDRAWN);
        assertThat(n.getBody())
                .isEqualTo(CandidateAlias.of(applicationId) + " đã rút đơn ứng tuyển vị trí \"" + jobTitle(jobId) + "\"");
        assertNoName(n.getBody(), huy);
    }

    @Test
    void notificationsWhenJobDisabledAreUnchanged() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, false);
        Candidate huy = candidate("Quốc Huy", "0900000111");
        UUID applicationId = apply(jobId, huy);
        applicationService.withdraw(huy.id(), applicationId);

        assertThat(single(hr, NotificationType.APPLICATION_SUBMITTED).getBody())
                .isEqualTo("Ứng viên Quốc Huy vừa ứng tuyển vị trí \"" + jobTitle(jobId) + "\"");
        assertThat(single(hr, NotificationType.APPLICATION_WITHDRAWN).getBody())
                .isEqualTo("Ứng viên Quốc Huy đã rút đơn ứng tuyển vị trí \"" + jobTitle(jobId) + "\"");
    }

    @Test
    void notificationCreatedBeforeEnablingIsNotRewritten() throws Exception {
        // R-O3: bat cong tac khong sua thong bao da tao.
        Hr hr = hr();
        UUID jobId = job(hr, false);
        apply(jobId, candidate("Quốc Huy", "0900000111"));
        String before = single(hr, NotificationType.APPLICATION_SUBMITTED).getBody();

        setBlindReview(jobId, true);

        assertThat(before).contains("Quốc Huy");
        assertThat(single(hr, NotificationType.APPLICATION_SUBMITTED).getBody()).isEqualTo(before);
    }

    // ---- NEW_MESSAGE ----

    @Test
    void newMessageOfMaskedApplicationHasNoExcerptAndNoName() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, true);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);

        messageService.sendAsCandidate(app.candidate().id(), app.id(), "Dạ em Quốc Huy đây ạ, SĐT 0900000111.", null);

        Notification n = single(hr, NotificationType.NEW_MESSAGE);
        assertThat(n.getBody()).isEqualTo(CandidateAlias.of(app.id()) + " đã gửi tin nhắn về đơn ứng tuyển vị trí \""
                + jobTitle(jobId) + "\"");
        assertThat(n.getBody()).doesNotContain("Dạ em");
        assertNoName(n.getBody(), app.candidate());
        String mail = sentMailText(n);
        assertThat(mail).startsWith(n.getBody());
        assertNoName(mail, app.candidate());
    }

    @Test
    void attachmentOnlyMessageOfMaskedApplicationKeepsAttachmentSentence() throws Exception {
        // L2: tin chi co tep - giu cau tep dinh kem, chi doi nhan.
        Hr hr = hr();
        UUID jobId = job(hr, true);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "CV Quốc Huy.pdf", "application/pdf", "%PDF-1.4 tep".getBytes(StandardCharsets.UTF_8));

        messageService.sendAsCandidate(app.candidate().id(), app.id(), null, file);

        Notification n = single(hr, NotificationType.NEW_MESSAGE);
        assertThat(n.getBody()).isEqualTo(CandidateAlias.of(app.id())
                + " đã gửi một tệp đính kèm về đơn ứng tuyển vị trí \"" + jobTitle(jobId) + "\"");
        assertNoName(n.getBody(), app.candidate());
    }

    @Test
    void newMessageWhenJobDisabledKeepsNameAndExcerpt() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, false);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);

        messageService.sendAsCandidate(app.candidate().id(), app.id(), "Dạ em chào anh chị.", null);

        assertThat(single(hr, NotificationType.NEW_MESSAGE).getBody()).isEqualTo("Ứng viên Quốc Huy đã gửi tin nhắn về"
                + " đơn ứng tuyển vị trí \"" + jobTitle(jobId) + "\": Dạ em chào anh chị.");
    }

    @Test
    void newMessageAfterInvitationKeepsNameAndExcerpt() throws Exception {
        Hr hr = hr();
        UUID jobId = job(hr, true);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);
        invite(app);

        messageService.sendAsCandidate(app.candidate().id(), app.id(), "Dạ em xác nhận lịch.", null);

        assertThat(single(hr, NotificationType.NEW_MESSAGE).getBody()).contains("Ứng viên Quốc Huy").contains("Dạ em xác nhận");
    }

    @Test
    void messageFromHrToCandidateIsUnchanged() throws Exception {
        // R-U1, R-O: thong bao gui UNG VIEN khong an danh (README) - ten cong ty va doan trich giu nguyen.
        Hr hr = hr();
        UUID jobId = job(hr, true);
        App app = application(jobId, candidate("Quốc Huy", "0900000111"), null);

        messageService.sendAsHr(hr.userId(), app.id(), "Chào Quốc Huy, mời bạn bổ sung hồ sơ.", null);

        List<Notification> forCandidate = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(app.candidate().id(), PageRequest.of(0, 10)).getContent();
        assertThat(forCandidate).hasSize(1);
        assertThat(forCandidate.getFirst().getBody()).endsWith(": Chào Quốc Huy, mời bạn bổ sung hồ sơ.");
    }
}
