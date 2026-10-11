package com.recruitment.screening;

import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H10 S6 - HR doc ban chup cau tra loi cua mot don. Chi DOC. Quyen so huu kiem DUY NHAT qua
// HrApplicationAccess.loadOwned (R-A3): cong ty truoc (404 COMPANY_NOT_FOUND), roi don (404 APPLICATION_NOT_FOUND),
// don cua cong ty khac -> 403. Khong trang thai Job/don nao khoa S6 (R-A4).
@Service
public class ApplicationScreeningHrService {

    private final HrApplicationAccess hrApplicationAccess;
    private final ApplicationScreeningAnswerRepository answerRepository;

    public ApplicationScreeningHrService(
            HrApplicationAccess hrApplicationAccess, ApplicationScreeningAnswerRepository answerRepository) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.answerRepository = answerRepository;
    }

    @Transactional(readOnly = true)
    public ApplicationScreeningResponse getAnswers(UUID ownerId, UUID applicationId) {
        hrApplicationAccess.loadOwned(ownerId, applicationId);

        // R-A3: trang thai xet screening_captured_at TRUOC - dong ban chup (neu co) cua don chua danh dau khong hien.
        Instant capturedAt = answerRepository.findScreeningCapturedAt(applicationId);
        if (capturedAt == null) {
            return new ApplicationScreeningResponse(applicationId, ScreeningCaptureState.NOT_CAPTURED, null, List.of());
        }

        List<ApplicationScreeningAnswer> rows = answerRepository.findByApplicationIdOrderByDisplayOrderAsc(applicationId);
        if (rows.isEmpty()) {
            return new ApplicationScreeningResponse(
                    applicationId, ScreeningCaptureState.NO_QUESTIONS, capturedAt, List.of());
        }

        // Chi dung cot *_snapshot (R-S3) - sua/xoa cau hoi goc sau do khong doi ket qua (R-S2).
        List<ApplicationScreeningResponse.Item> items = rows.stream()
                .map(row -> new ApplicationScreeningResponse.Item(
                        row.getDisplayOrder(),
                        row.getQuestionTypeSnapshot(),
                        row.getQuestionTextSnapshot(),
                        row.getOptionsSnapshot(),
                        row.isRequiredSnapshot(),
                        row.getAnswerText(),
                        row.getAnswerYesNo()))
                .toList();
        return new ApplicationScreeningResponse(applicationId, ScreeningCaptureState.CAPTURED, capturedAt, items);
    }
}
