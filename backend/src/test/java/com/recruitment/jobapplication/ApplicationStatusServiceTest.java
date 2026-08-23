package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

        when(jobApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(companyRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(company));
        when(jobApplicationRepository.save(application)).thenReturn(application);

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

        when(jobApplicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(companyRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(company));

        // PENDING -> HIRED khong nam trong ALLOWED_TRANSITIONS, nem loi truoc buoc publish.
        assertThatThrownBy(() -> service.changeStatus(ownerId, applicationId, ApplicationStatus.HIRED))
                .isInstanceOf(InvalidApplicationStatusTransitionException.class);

        verifyNoInteractions(eventPublisher);
    }
}
