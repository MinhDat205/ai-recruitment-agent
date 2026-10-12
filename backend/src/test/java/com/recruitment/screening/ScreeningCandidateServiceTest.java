package com.recruitment.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.job.Job;
import com.recruitment.job.JobRepository;
import com.recruitment.screening.dto.ScreeningFormResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// FR-U09 T2 (R-F3) - thu tu doc cua F1 la quy tac cung: findOpenJobById -> findRevision -> danh sach cau hoi. Dao thu tu
// (doc cau hoi truoc revision) thi client co the nhan revision moi + cau hoi cu va apply chap nhan cau tra loi lech.
@ExtendWith(MockitoExtension.class)
class ScreeningCandidateServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ScreeningQuestionRepository questionRepository;

    @Test
    void getForm_readsRevisionBeforeQuestions_andNeverLocks() {
        ScreeningCandidateService service = new ScreeningCandidateService(jobRepository, questionRepository);
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findOpenJobById(jobId)).thenReturn(Optional.of(new Job()));
        when(questionRepository.findRevision(jobId)).thenReturn(4);
        when(questionRepository.findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(jobId)).thenReturn(List.of());

        ScreeningFormResponse response = service.getForm(jobId);

        assertThat(response.revision()).isEqualTo(4);
        InOrder order = inOrder(jobRepository, questionRepository);
        order.verify(jobRepository).findOpenJobById(jobId);
        order.verify(questionRepository).findRevision(jobId);
        order.verify(questionRepository).findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(jobId);
        // L2 - F1 chay readOnly, Postgres cam FOR SHARE trong transaction chi doc.
        verify(questionRepository, never()).findRevisionForShare(any());
    }

    @Test
    void getForm_jobNotOpen_throwsJobNotFound_withoutReadingQuestions() {
        ScreeningCandidateService service = new ScreeningCandidateService(jobRepository, questionRepository);
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findOpenJobById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForm(jobId)).isInstanceOf(JobNotFoundException.class);
        verifyNoInteractions(questionRepository);
    }
}
