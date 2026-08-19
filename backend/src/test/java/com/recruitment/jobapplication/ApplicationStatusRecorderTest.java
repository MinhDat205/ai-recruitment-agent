package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationStatusRecorderTest {

    @Mock
    private ApplicationStatusHistoryRepository statusHistoryRepository;

    @Test
    void record_savesHistoryRowWithGivenFields() {
        ApplicationStatusRecorder recorder = new ApplicationStatusRecorder(statusHistoryRepository);

        UUID applicationId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();

        recorder.record(applicationId, ApplicationStatus.PENDING, ApplicationStatus.INTERVIEW_INVITED, changedBy, null);

        ArgumentCaptor<ApplicationStatusHistory> captor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(statusHistoryRepository).save(captor.capture());
        ApplicationStatusHistory history = captor.getValue();
        assertThat(history.getApplicationId()).isEqualTo(applicationId);
        assertThat(history.getFromStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(history.getToStatus()).isEqualTo(ApplicationStatus.INTERVIEW_INVITED);
        assertThat(history.getChangedBy()).isEqualTo(changedBy);
        assertThat(history.getNote()).isNull();
    }

    @Test
    void record_nullFromStatus_savesHistoryRowWithNullFromStatus() {
        ApplicationStatusRecorder recorder = new ApplicationStatusRecorder(statusHistoryRepository);

        UUID applicationId = UUID.randomUUID();
        UUID changedBy = UUID.randomUUID();

        recorder.record(applicationId, null, ApplicationStatus.PENDING, changedBy, null);

        ArgumentCaptor<ApplicationStatusHistory> captor = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(statusHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isNull();
    }
}
