package com.recruitment.screening;

import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

// FR-U09 R-C5, R-D2 - doc ban chup cau tra loi cua MOT don va tinh state, dung chung cho S6 (HR, FR-H10) va F3 (ung
// vien). KHONG kiem quyen: noi goi phai kiem truoc (HrApplicationAccess.loadOwned / findByIdAndCandidateId). Than ham
// chuyen nguyen tu ApplicationScreeningHrService.getAnswers - hanh vi S6 khong doi. Chay trong transaction cua noi goi.
@Component
public class ApplicationScreeningReader {

    private final ApplicationScreeningAnswerRepository answerRepository;

    public ApplicationScreeningReader(ApplicationScreeningAnswerRepository answerRepository) {
        this.answerRepository = answerRepository;
    }

    ApplicationScreeningResponse read(UUID applicationId) {
        // R-A3 cua FR-H10: trang thai xet screening_captured_at TRUOC - dong ban chup (neu co) cua don chua danh dau
        // khong hien.
        Instant capturedAt = answerRepository.findScreeningCapturedAt(applicationId);
        if (capturedAt == null) {
            return new ApplicationScreeningResponse(applicationId, ScreeningCaptureState.NOT_CAPTURED, null, List.of());
        }

        List<ApplicationScreeningAnswer> rows = answerRepository.findByApplicationIdOrderByDisplayOrderAsc(applicationId);
        if (rows.isEmpty()) {
            return new ApplicationScreeningResponse(
                    applicationId, ScreeningCaptureState.NO_QUESTIONS, capturedAt, List.of());
        }

        // Chi dung cot *_snapshot (R-S3 cua FR-H10) - sua/xoa cau hoi goc sau do khong doi ket qua (R-S2).
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
