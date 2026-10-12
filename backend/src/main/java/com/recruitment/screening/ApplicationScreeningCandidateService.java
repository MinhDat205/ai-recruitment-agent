package com.recruitment.screening;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-U09 F3 - ung vien xem lai ban chup cau tra loi cua CHINH don minh (R-D2). Quyen so huu theo khuon
// findByIdAndCandidateId -> 404 nhu ApplicationService.withdraw (R-A2; R-A5 cua FR-H10) - KHONG dung HrApplicationAccess.
// Khong trang thai don/tin nao khoa F3 (R-D3).
@Service
public class ApplicationScreeningCandidateService {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationScreeningReader reader;

    public ApplicationScreeningCandidateService(
            JobApplicationRepository applicationRepository, ApplicationScreeningReader reader) {
        this.applicationRepository = applicationRepository;
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public ApplicationScreeningResponse getMyAnswers(UUID candidateId, UUID applicationId) {
        // Don khong ton tai HOAC cua nguoi khac -> CUNG 404, khong phan biet.
        applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        return reader.read(applicationId);
    }
}
