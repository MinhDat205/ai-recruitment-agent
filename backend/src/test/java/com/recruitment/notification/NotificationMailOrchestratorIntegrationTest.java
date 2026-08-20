package com.recruitment.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

// KHONG @Transactional cap class - processOne() tu mo/dong transaction ngan rieng cua no
// (NotificationMailStateService), boc @Transactional bao ngoai se che mat viec markSent/markFailed
// co that su commit hay khong, giong ly do ScoringRunStateServiceTest khong dung @Transactional
// bao ngoai.
@Import({TestcontainersConfiguration.class, MailTestConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class NotificationMailOrchestratorIntegrationTest {

    @Autowired
    private NotificationMailOrchestrator orchestrator;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private JavaMailSender mailSender;

    @BeforeEach
    void resetMailSenderMock() {
        Mockito.reset(mailSender);
    }

    // Dung lam "ban ghi nghiep vu lien quan" de chung minh loi gui email KHONG lam sai nghiep vu
    // chinh - JobApplication ma notification tro toi qua entityId.
    private record Fixture(UUID recipientId, UUID applicationId) {
    }

    private Fixture createFixture() {
        User hr = new User();
        hr.setEmail("hr-" + UUID.randomUUID() + "@example.com");
        hr.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        hr.setRole(Role.HR);
        hr.setFullName("Nha Tuyen Dung Test");
        hr = userRepository.save(hr);

        Company company = new Company();
        company.setOwnerId(hr.getId());
        company.setName("Cong ty Test " + UUID.randomUUID());
        company = companyRepository.save(company);

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setCreatedBy(hr.getId());
        job.setTitle("Backend Developer");
        job.setDescription("Mo ta cong viec");
        job.setStatus(JobStatus.OPEN);
        job.setRecruitmentCycle(1);
        job = jobRepository.save(job);

        User candidate = new User();
        candidate.setEmail("cand-" + UUID.randomUUID() + "@example.com");
        candidate.setPasswordHash("$2a$10$fakehashfaketestfaketestfaketestfaketestfaketest");
        candidate.setRole(Role.CANDIDATE);
        candidate.setFullName("Nguyen Van Ung Vien");
        candidate = userRepository.save(candidate);

        Resume resume = new Resume();
        resume.setCandidateId(candidate.getId());
        resume.setFileUrl("resumes/" + UUID.randomUUID() + ".pdf");
        resume.setFileName("cv.pdf");
        resume.setFileType(ResumeFileType.PDF);
        resume.setFileSize(1024L);
        resume.setPrimary(true);
        resume.setParseStatus(ParseStatus.DONE);
        resume = resumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setJobId(job.getId());
        application.setCandidateId(candidate.getId());
        application.setResumeId(resume.getId());
        application.setRecruitmentCycle(1);
        application.setStatus(ApplicationStatus.PENDING);
        application.setAiConsent(true);
        application.setAiConsentAt(Instant.now());
        application = jobApplicationRepository.save(application);

        return new Fixture(candidate.getId(), application.getId());
    }

    private Notification seedPendingNotification(UUID recipientId, UUID applicationId) {
        Notification n = new Notification();
        n.setUserId(recipientId);
        n.setType(NotificationType.APPLICATION_STATUS_CHANGED);
        n.setTitle("Cập nhật đơn ứng tuyển");
        n.setBody("Đơn ứng tuyển của bạn đã chuyển sang trạng thái: Bị từ chối");
        n.setLink("/candidate/applications");
        n.setEntityType("APPLICATION");
        n.setEntityId(applicationId);
        n.setRead(false);
        n.setEmailStatus(EmailStatus.PENDING);
        return notificationRepository.save(n);
    }

    @Test
    void processOne_sendSucceeds_marksSent() {
        Fixture fixture = createFixture();
        Notification notification = seedPendingNotification(fixture.recipientId(), fixture.applicationId());

        orchestrator.processOne(notification.getId());

        Notification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloaded.getEmailStatus()).isEqualTo(EmailStatus.SENT);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void processOne_sendThrows_marksFailedAndLeavesJobApplicationUntouched() {
        Fixture fixture = createFixture();
        Notification notification = seedPendingNotification(fixture.recipientId(), fixture.applicationId());
        doThrow(new MailSendException("Gia lap loi SMTP"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        orchestrator.processOne(notification.getId());

        Notification reloadedNotification = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(reloadedNotification.getEmailStatus()).isEqualTo(EmailStatus.FAILED);

        JobApplication reloadedApplication =
                jobApplicationRepository.findById(fixture.applicationId()).orElseThrow();
        assertThat(reloadedApplication.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }
}
