package com.recruitment.reconsideration;

import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.ReconsiderationAlreadyRequestedException;
import com.recruitment.common.exception.ReconsiderationDeadlinePassedException;
import com.recruitment.common.exception.ReconsiderationJobRemovedException;
import com.recruitment.common.exception.ReconsiderationNotEligibleException;
import com.recruitment.common.exception.ReconsiderationTextTooLongException;
import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.notification.ReconsiderationRequestedEvent;
import com.recruitment.reconsideration.dto.CandidateReconsiderationResponse;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-C09 C1 - ung vien gui yeu cau xem xet lai. Khong goi AI, khong job nen: mot request dong bo ngan, chi ghi DB.
@Service
public class ReconsiderationCandidateService {

    private final JobApplicationRepository applicationRepository;
    private final ReconsiderationRequestRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public ReconsiderationCandidateService(
            JobApplicationRepository applicationRepository,
            ReconsiderationRequestRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    // R-E5 - thu tu kiem, tra loi dau tien gap: quyen (404) -> da co yeu cau -> R-E1 b/c -> tin da xoa -> han -> van ban.
    // Buoc kiem truoc chi de tra dung ma; chot chan that la cau INSERT co dieu kien (R-T3) va unique o DB (R-E2).
    @Transactional
    public CandidateReconsiderationResponse request(UUID candidateId, UUID applicationId, String rawReason) {
        // R-C4: don khong ton tai HOAC cua ung vien khac -> CUNG 404 (quy uoc FR-U08 R-Q2).
        JobApplication application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        ReconsiderationCandidateRow row = repository
                .findCandidateRow(applicationId, candidateId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        requireEligible(row);

        String reason = ReconsiderationText.normalize(rawReason);
        if (ReconsiderationText.tooLong(reason)) {
            throw new ReconsiderationTextTooLongException();
        }

        if (repository.insertIfEligible(applicationId, candidateId, reason) == 0) {
            // R-T3 - doc lai de tra dung ma theo R-E5; neu van thay du dieu kien thi chi con ly do cham moc han giua hai
            // buoc -> 409 DEADLINE_PASSED, khong ghi duoc yeu cau qua han.
            repository.findCandidateRow(applicationId, candidateId).ifPresent(ReconsiderationCandidateService::requireEligible);
            throw new ReconsiderationDeadlinePassedException();
        }

        // R-N1 - publish TRONG transaction; NotificationEventListener xu ly AFTER_COMMIT. Request thua (409 do unique)
        // khong toi duoc day, nen khong co thong bao (R-E2).
        eventPublisher.publishEvent(
                new ReconsiderationRequestedEvent(applicationId, application.getJobId(), candidateId));

        // clearAutomatically da xoa persistence context; cau native doc lai dong vua ghi trong cung transaction.
        return repository
                .findCandidateRow(applicationId, candidateId)
                .map(ReconsiderationReader::toCandidateBlock)
                .orElseThrow(() -> new IllegalStateException("Khong doc lai duoc yeu cau vua ghi: " + applicationId));
    }

    private static void requireEligible(ReconsiderationCandidateRow row) {
        if (row.getRequestStatus() != null) {
            throw new ReconsiderationAlreadyRequestedException();
        }
        if (!ReconsiderationReader.rejectedAtScreening(row)) {
            throw new ReconsiderationNotEligibleException();
        }
        if (Boolean.TRUE.equals(row.getJobRemoved())) {
            throw new ReconsiderationJobRemovedException();
        }
        if (!Boolean.TRUE.equals(row.getWithinDeadline())) {
            throw new ReconsiderationDeadlinePassedException();
        }
    }
}
