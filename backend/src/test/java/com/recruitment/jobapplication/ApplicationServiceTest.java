package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ApplicationNotWithdrawableException;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.notification.ApplicationSubmittedEvent;
import com.recruitment.notification.ApplicationWithdrawnEvent;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private JobApplicationRepository applicationRepository;

    @Mock
    private ApplicationStatusHistoryRepository statusHistoryRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private ApplicationStatusRecorder applicationStatusRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApplicationService newService() {
        return new ApplicationService(
                applicationRepository,
                statusHistoryRepository,
                jobRepository,
                resumeRepository,
                applicationStatusRecorder,
                eventPublisher);
    }

    @Test
    void apply_happyPath_recordsInitialPendingHistoryRow() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();

        Job job = new Job();
        job.setId(jobId);
        job.setRecruitmentCycle(1);

        Resume resume = new Resume();
        resume.setId(resumeId);

        when(jobRepository.findOpenJobById(jobId)).thenReturn(Optional.of(job));
        when(resumeRepository.findByIdAndCandidateId(resumeId, candidateId)).thenReturn(Optional.of(resume));
        when(applicationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            application.setId(UUID.randomUUID());
            return application;
        });

        ApplicationResponse response =
                service.apply(candidateId, new ApplicationCreateRequest(jobId, resumeId, true, "Cover letter"));

        verify(applicationStatusRecorder)
                .record(response.id(), null, ApplicationStatus.PENDING, candidateId, null);
    }

    @Test
    void apply_happyPath_publishesApplicationSubmittedEvent() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID resumeId = UUID.randomUUID();

        Job job = new Job();
        job.setId(jobId);
        job.setRecruitmentCycle(1);

        Resume resume = new Resume();
        resume.setId(resumeId);

        when(jobRepository.findOpenJobById(jobId)).thenReturn(Optional.of(job));
        when(resumeRepository.findByIdAndCandidateId(resumeId, candidateId)).thenReturn(Optional.of(resume));
        when(applicationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            JobApplication application = invocation.getArgument(0);
            application.setId(UUID.randomUUID());
            return application;
        });

        ApplicationResponse response =
                service.apply(candidateId, new ApplicationCreateRequest(jobId, resumeId, true, "Cover letter"));

        verify(eventPublisher)
                .publishEvent(argThat((ApplicationSubmittedEvent event) -> event.applicationId().equals(response.id())
                        && event.jobId().equals(jobId)
                        && event.candidateId().equals(candidateId)));
    }

    @Test
    void getMyApplicationHistory_applicationOfAnotherCandidate_throwsNotFound() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findByIdAndCandidateId(applicationId, candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyApplicationHistory(candidateId, applicationId))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void withdraw_fromPending_recordsHistoryAndChangesStatus() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setStatus(ApplicationStatus.PENDING);

        when(applicationRepository.findByIdAndCandidateId(applicationId, candidateId)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        var response = service.withdraw(candidateId, applicationId);

        assertThat(response.status()).isEqualTo(ApplicationStatus.WITHDRAWN);

        verify(applicationStatusRecorder)
                .record(applicationId, ApplicationStatus.PENDING, ApplicationStatus.WITHDRAWN, candidateId, null);
    }

    @Test
    void withdraw_fromPending_publishesApplicationWithdrawnEvent() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setJobId(jobId);
        application.setStatus(ApplicationStatus.PENDING);

        when(applicationRepository.findByIdAndCandidateId(applicationId, candidateId)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        service.withdraw(candidateId, applicationId);

        verify(eventPublisher)
                .publishEvent(argThat((ApplicationWithdrawnEvent event) -> event.applicationId().equals(applicationId)
                        && event.jobId().equals(jobId)
                        && event.candidateId().equals(candidateId)));
    }

    @Test
    void withdraw_alreadyHired_throwsApplicationNotWithdrawableException() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        JobApplication application = new JobApplication();
        application.setId(applicationId);
        application.setStatus(ApplicationStatus.HIRED);

        when(applicationRepository.findByIdAndCandidateId(applicationId, candidateId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.withdraw(candidateId, applicationId))
                .isInstanceOf(ApplicationNotWithdrawableException.class);

        verify(applicationRepository, never()).save(any());
    }

    @Test
    void withdraw_applicationOfAnotherCandidate_throwsApplicationNotFoundException() {
        ApplicationService service = newService();

        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        when(applicationRepository.findByIdAndCandidateId(applicationId, candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw(candidateId, applicationId))
                .isInstanceOf(ApplicationNotFoundException.class);
    }
}
