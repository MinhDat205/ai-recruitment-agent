package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.recruitment.common.exception.ApplicationStatusConflictException;
import com.recruitment.common.exception.InvalidApplicationStatusTransitionException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.notification.ApplicationStatusChangedEvent;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ApplicationStatusServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ApplicationStatusRecorder applicationStatusRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApplicationStatusService newService() {
        return new ApplicationStatusService(
                jobApplicationRepository, jobRepository, companyRepository, applicationStatusRecorder, eventPublisher);
    }

    @Test
    void changeStatus_pendingToRejected_publishesApplicationStatusChangedEvent() {
        ApplicationStatusService service = newService();

        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setCandidateId(candidateId);
        application.setStatus(ApplicationStatus.PENDING);

        Job job = new Job();
        job.setId(jobId);
        job.setCompanyId(companyId);

        Company company = new Company();
        company.setId(companyId);
        company.setOwnerId(ownerId);

        JobApplication refreshed = new JobApplication();
        refreshed.setId(applicationId);
        refreshed.setJobId(jobId);
        refreshed.setCandidateId(candidateId);
        refreshed.setStatus(ApplicationStatus.REJECTED);

        when(companyRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(company));
        when(jobApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application), Optional.of(refreshed));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(jobApplicationRepository.updateStatusIfCurrent(applicationId, ApplicationStatus.PENDING, ApplicationStatus.REJECTED))
                .thenReturn(1);

        service.changeStatus(ownerId, applicationId, ApplicationStatus.REJECTED);

        verify(eventPublisher)
                .publishEvent(argThat((ApplicationStatusChangedEvent event) -> event.applicationId()
                                .equals(applicationId)
                        && event.jobId().equals(jobId)
                        && event.candidateId().equals(candidateId)
                        && event.fromStatus() == ApplicationStatus.PENDING
                        && event.toStatus() == ApplicationStatus.REJECTED));
    }

    @Test
    void changeStatus_invalidTransition_doesNotPublishEvent() {
        ApplicationStatusService service = newService();

        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setCandidateId(UUID.randomUUID());
        application.setStatus(ApplicationStatus.PENDING);

        Job job = new Job();
        job.setId(jobId);
        job.setCompanyId(companyId);

        Company company = new Company();
        company.setId(companyId);
        company.setOwnerId(ownerId);

        when(companyRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(company));
        when(jobApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        // PENDING -> HIRED khong nam trong ALLOWED_TRANSITIONS, nem loi truoc buoc publish.
        assertThatThrownBy(() -> service.changeStatus(ownerId, applicationId, ApplicationStatus.HIRED))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class);

        verifyNoInteractions(eventPublisher);
    }

    // Dot 2 (chore/hardening) - chot chan lost-update: updateStatusIfCurrent tra rowcount=0 nghia la
    // mot request khac da doi trang thai truoc do (double-click, hai tab HR cung doc duoc oldStatus
    // PENDING). Mo phong bang mock thay vi hai luong that (unit test, khong can that su dong thoi) -
    // day chinh la mau da chot trong ke hoach Dot 2: "gia lap mot luong khac da ghi truoc" bang
    // rowcount=0 tra ve tu repository.
    @Test
    void changeStatus_concurrentModification_throwsConflictExceptionAndDoesNotRecordOrPublish() {
        ApplicationStatusService service = newService();

        UUID ownerId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setCandidateId(UUID.randomUUID());
        application.setStatus(ApplicationStatus.PENDING);

        Job job = new Job();
        job.setId(jobId);
        job.setCompanyId(companyId);

        Company company = new Company();
        company.setId(companyId);
        company.setOwnerId(ownerId);

        when(companyRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(company));
        when(jobApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        // Mot request khac da doi PENDING -> INTERVIEW_INVITED (hoac REJECTED) truoc do trong DB
        // that - request nay van doc duoc oldStatus=PENDING (chua kip thay doi trong object Java),
        // nhung UPDATE co dieu kien khong con khop nua.
        when(jobApplicationRepository.updateStatusIfCurrent(applicationId, ApplicationStatus.PENDING, ApplicationStatus.REJECTED))
                .thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(ownerId, applicationId, ApplicationStatus.REJECTED))
                .isInstanceOf(ApplicationStatusConflictException.class);

        verifyNoInteractions(eventPublisher);
        verify(applicationStatusRecorder, never()).record(any(), any(), any(), any(), any());
    }
}
