package com.recruitment.screening;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.IdentityMaskSource;
import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H10 S6 - HR doc ban chup cau tra loi cua mot don. Chi DOC. Quyen so huu kiem DUY NHAT qua
// HrApplicationAccess.loadOwned (R-A3): cong ty truoc (404 COMPANY_NOT_FOUND), roi don (404 APPLICATION_NOT_FOUND),
// don cua cong ty khac -> 403. Khong trang thai Job/don nao khoa S6 (R-A4).
// FR-U09 R-C5: phan doc + tinh state chuyen sang ApplicationScreeningReader dung chung voi F3; hanh vi S6 khong doi.
// FR-H16 R-M4: don dang an danh -> che answerText cua cau SHORT_TEXT o DAY (khong o reader dung chung voi phia ung
// vien). answerText cua SINGLE_CHOICE (phuong an do HR dat) va answerYesNo giu nguyen.
@Service
public class ApplicationScreeningHrService {

    private final HrApplicationAccess hrApplicationAccess;
    private final ApplicationScreeningReader reader;
    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public ApplicationScreeningHrService(
            HrApplicationAccess hrApplicationAccess,
            ApplicationScreeningReader reader,
            BlindReviewResolver blindReviewResolver,
            IdentityMaskSource identityMaskSource) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.reader = reader;
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
    }

    @Transactional(readOnly = true)
    public ApplicationScreeningResponse getAnswers(UUID ownerId, UUID applicationId) {
        hrApplicationAccess.loadOwned(ownerId, applicationId);
        ApplicationScreeningResponse response = reader.read(applicationId);
        if (response.items().isEmpty() || !blindReviewResolver.isMasked(applicationId)) {
            return response;
        }
        IdentityMasker masker = identityMaskSource.load(applicationId);
        return new ApplicationScreeningResponse(
                response.applicationId(),
                response.state(),
                response.capturedAt(),
                response.items().stream()
                        .map(item -> item.questionType() != ScreeningQuestionType.SHORT_TEXT
                                ? item
                                : new ApplicationScreeningResponse.Item(
                                        item.displayOrder(),
                                        item.questionType(),
                                        item.questionText(),
                                        item.options(),
                                        item.required(),
                                        masker.mask(item.answerText()),
                                        item.answerYesNo()))
                        .toList());
    }
}
