package com.recruitment.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.TestcontainersConfiguration;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.job.JobStatus;
import com.recruitment.jobapplication.ApplicationService;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.ApplicationStatusService;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeFileType;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.Role;
import com.recruitment.user.User;
import com.recruitment.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

// FR-C03: kiem tra dau-cuoi 3 su kien AFTER_COMMIT (apply/withdraw/changeStatus) that su tao dung
// dong notifications. KHONG @Transactional cap class - AFTER_COMMIT chi chay khi transaction cua
// ApplicationService/ApplicationStatusService THAT SU commit; boc @Transactional bao ngoai se
// rollback het truoc khi Spring kip goi listener, giong ly do ScoringRunStateServiceTest/
// ResumeParsingStateServiceTest khong dung @Transactional bao ngoai (xem comment dau hai file do).
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class NotificationEventListenerIntegrationTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationStatusService applicationStatusService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private record Fixture(UUID hrOwnerId, UUID jobId, UUID candidateId, UUID resumeId) {
    }

    private Fixture createOpenJobWithCandidateResume() {
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

        return new Fixture(hr.getId(), job.getId(), candidate.getId(), resume.getId());
    }

    @Test
    void apply_happyPath_createsNotificationForHrOwner() {
        Fixture fixture = createOpenJobWithCandidateResume();

        applicationService.apply(
                fixture.candidateId(), new ApplicationCreateRequest(fixture.jobId(), fixture.resumeId(), true, null));

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(fixture.hrOwnerId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.APPLICATION_SUBMITTED);
        assertThat(notifications.get(0).getEmailStatus()).isEqualTo(EmailStatus.PENDING);
    }

    @Test
    void withdraw_happyPath_createsNotificationForHrOwner() {
        Fixture fixture = createOpenJobWithCandidateResume();
        ApplicationResponse application = applicationService.apply(
                fixture.candidateId(), new ApplicationCreateRequest(fixture.jobId(), fixture.resumeId(), true, null));

        applicationService.withdraw(fixture.candidateId(), application.id());

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(fixture.hrOwnerId(), PageRequest.of(0, 10))
                .getContent();
        // 1 thong bao don moi (apply) + 1 thong bao rut don (withdraw), ca hai deu ve cung 1 HR.
        assertThat(notifications).hasSize(2);
        assertThat(notifications)
                .extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.APPLICATION_SUBMITTED, NotificationType.APPLICATION_WITHDRAWN);
    }

    // Ranh buoc CLAUDE.md muc 8: thong bao cho ung vien tuyet doi khong duoc lo diem/nhan xet
    // noi bo cua HR - chi duoc thay trang thai don da doi.
    @Test
    void changeStatus_happyPath_createsNotificationForCandidateWithoutLeakingScoreInfo() {
        Fixture fixture = createOpenJobWithCandidateResume();
        ApplicationResponse application = applicationService.apply(
                fixture.candidateId(), new ApplicationCreateRequest(fixture.jobId(), fixture.resumeId(), true, null));

        applicationStatusService.changeStatus(fixture.hrOwnerId(), application.id(), ApplicationStatus.REJECTED);

        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(fixture.candidateId(), PageRequest.of(0, 10))
                .getContent();
        assertThat(notifications).hasSize(1);
        Notification notification = notifications.get(0);
        assertThat(notification.getType()).isEqualTo(NotificationType.APPLICATION_STATUS_CHANGED);
        assertThat(notification.getBody()).doesNotContainIgnoringCase("score");
        assertThat(notification.getBody()).doesNotContainIgnoringCase("điểm");
        assertThat(notification.getBody()).doesNotContainIgnoringCase("rubric");
    }
}
