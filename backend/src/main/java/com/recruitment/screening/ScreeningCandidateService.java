package com.recruitment.screening;

import com.recruitment.common.exception.JobNotFoundException;
import com.recruitment.job.JobRepository;
import com.recruitment.screening.dto.ScreeningFormResponse;
import com.recruitment.screening.dto.ScreeningQuestionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-U09 phia ung vien cua sang loc. F1: bo cau hoi de dien form nop don.
@Service
public class ScreeningCandidateService {

    private final JobRepository jobRepository;
    private final ScreeningQuestionRepository questionRepository;

    public ScreeningCandidateService(JobRepository jobRepository, ScreeningQuestionRepository questionRepository) {
        this.jobRepository = jobRepository;
        this.questionRepository = questionRepository;
    }

    // F1 (R-F1-R-F3). Chi tin xem duoc cong khai: CHINH findOpenJobById (cung dieu kien voi apply va trang tin cong
    // khai). THU TU DOC LA QUY TAC CUNG (R-F3): revision TRUOC, cau hoi SAU - HR commit xen giua chi lam lan nop sau
    // bi 409 (an toan); dao thu tu se cho client revision moi + cau hoi cu. readOnly nen dung findRevision, KHONG
    // findRevisionForShare (Postgres cam FOR SHARE trong transaction chi doc, L2).
    @Transactional(readOnly = true)
    public ScreeningFormResponse getForm(UUID jobId) {
        jobRepository.findOpenJobById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
        int revision = questionRepository.findRevision(jobId);
        List<ScreeningQuestionResponse> questions =
                questionRepository.findByJobIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(jobId).stream()
                        .map(ScreeningCandidateService::toResponse)
                        .toList();
        return new ScreeningFormResponse(jobId, revision, questions);
    }

    private static ScreeningQuestionResponse toResponse(ScreeningQuestion q) {
        return new ScreeningQuestionResponse(
                q.getId(), q.getQuestionType(), q.getQuestionText(), q.getOptions(), q.isRequired(), q.getDisplayOrder());
    }
}
