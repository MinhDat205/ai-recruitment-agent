package com.recruitment.reconsideration;

import com.recruitment.common.exception.InvalidApplicationStatusTransitionException;
import com.recruitment.common.exception.ReconsiderationNotOpenException;
import com.recruitment.common.exception.ReconsiderationTextTooLongException;
import com.recruitment.jobapplication.ApplicationOwnerService;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.jobapplication.HrApplicationAccess.OwnedApplication;
import com.recruitment.notification.ReconsiderationDeclinedEvent;
import com.recruitment.reconsideration.dto.HrReconsiderationListResponse;
import com.recruitment.reconsideration.dto.HrReconsiderationResponse;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-C09 phia HR: H1 (danh sach theo tin), H2 (giu quyet dinh tu choi), claimForAcceptance (buoc (0)-(1) cua R-S3, goi tu
// InterviewInvitationService.sendInvitation). Khong goi AI, khong job nen, khong quyet dinh tu dong (R-D6). Moi quyet
// dinh la UPDATE ... WHERE status = 'OPEN' tren CUNG mot hang (R-D3): luot thua nhan 409, khong SELECT ... FOR UPDATE.
@Service
public class ReconsiderationHrService {

    private static final String FILTER_ALL = "ALL";

    private final ReconsiderationRequestRepository repository;
    private final ReconsiderationReader reader;
    private final HrApplicationAccess hrApplicationAccess;
    private final ApplicationOwnerService applicationOwnerService;
    private final ApplicationEventPublisher eventPublisher;

    public ReconsiderationHrService(
            ReconsiderationRequestRepository repository,
            ReconsiderationReader reader,
            HrApplicationAccess hrApplicationAccess,
            ApplicationOwnerService applicationOwnerService,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.reader = reader;
        this.hrApplicationAccess = hrApplicationAccess;
        this.applicationOwnerService = applicationOwnerService;
        this.eventPublisher = eventPublisher;
    }

    // H1 - quyen theo tin (R-D5: 404 COMPANY_NOT_FOUND / 404 JOB_NOT_FOUND / 403). statusParam "ALL" (khong phan biet hoa
    // thuong) = moi trang thai; thieu hoac gia tri la = OPEN (khuon ApplicationSortOption.fromParam).
    @Transactional(readOnly = true)
    public HrReconsiderationListResponse list(UUID ownerId, UUID jobId, String statusParam) {
        applicationOwnerService.loadOwnedJob(jobId, ownerId);
        String filter = FILTER_ALL.equalsIgnoreCase(statusParam) ? null : ReconsiderationStatus.OPEN.name();
        return new HrReconsiderationListResponse(
                jobId, Math.toIntExact(repository.countOpenByJob(jobId)), reader.hrList(jobId, filter));
    }

    // H2 (R-D2) - thu tu: quyen -> van ban (400) -> UPDATE co dieu kien (409). Don khong doi trang thai, khong ghi lich su.
    @Transactional
    public HrReconsiderationResponse decline(UUID ownerId, UUID applicationId, String rawResponse) {
        OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId);
        String response = ReconsiderationText.normalize(rawResponse);
        if (ReconsiderationText.tooLong(response)) {
            throw new ReconsiderationTextTooLongException();
        }
        // 0 dong: khong co yeu cau, da ACCEPTED, da DECLINED, hoac thua tranh chap (R-D3).
        if (repository.declineIfOpen(applicationId, ownerId, response) == 0) {
            throw new ReconsiderationNotOpenException();
        }
        // R-N3 - publish TRONG transaction; listener AFTER_COMMIT gui ung vien.
        eventPublisher.publishEvent(new ReconsiderationDeclinedEvent(
                applicationId, owned.job().getId(), owned.application().getCandidateId()));
        return reader.hrBlock(applicationId);
    }

    // R-S3 (0), (1) - CHI InterviewInvitationService.sendInvitation goi, cho don dang REJECTED, SAU khi da kiem quyen va
    // kiem an danh. Tham gia transaction cua sendInvitation (REQUIRED): loi o buoc sau (tao giay moi) rollback ca buoc nay.
    // (0) Khong co yeu cau, hoac yeu cau ACCEPTED/DECLINED -> 400 nhu FR-H12 hien nay (cung exception, cung cau), khong ghi
    //     gi. (1) Yeu cau doc duoc la OPEN -> chuyen ACCEPTED bang UPDATE co dieu kien; 0 dong (vua bi quyet o request
    //     khac giua (0) va (1)) -> 409 RECONSIDERATION_NOT_OPEN (R-D3).
    @Transactional
    public void claimForAcceptance(UUID applicationId, UUID ownerId) {
        boolean open = repository
                .findByApplicationId(applicationId)
                .map(request -> request.getStatus() == ReconsiderationStatus.OPEN)
                .orElse(false);
        if (!open) {
            throw new InvalidApplicationStatusTransitionException(
                    ApplicationStatus.REJECTED.name(), ApplicationStatus.INTERVIEW_INVITED.name());
        }
        if (repository.acceptIfOpen(applicationId, ownerId) == 0) {
            throw new ReconsiderationNotOpenException();
        }
    }
}
