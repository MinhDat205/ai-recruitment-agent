package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.jobapplication.dto.ApplicationCreateRequest;
import com.recruitment.jobapplication.dto.ApplicationResponse;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void apply_happyPath_recordsInitialPendingHistoryRow() {
        ApplicationService service =
                new ApplicationService(applicationRepository, statusHistoryRepository, jobRepository, resumeRepository);

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

        ArgumentCaptor<ApplicationStatusHistory> historyCaptor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(statusHistoryRepository).save(historyCaptor.capture());
        ApplicationStatusHistory history = historyCaptor.getValue();
        assertThat(history.getApplicationId()).isEqualTo(response.id());
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(history.getChangedBy()).isEqualTo(candidateId);
    }

    @Test
    void getMyApplicationHistory_applicationOfAnotherCandidate_throwsNotFound() {
        ApplicationService service =
                new ApplicationService(applicationRepository, statusHistoryRepository, jobRepository, resumeRepository);

        UUID candidateId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findByIdAndCandidateId(applicationId, candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyApplicationHistory(candidateId, applicationId))
                .isInstanceOf(ApplicationNotFoundException.class);
    }
}
