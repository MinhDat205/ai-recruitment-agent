package com.recruitment.screening;

import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

    // FR-H14 (REQUIREMENT muc 4.2, 4.6 cau 13) - trang thai + ban chup cua NHIEU don trong MOT query. capturedAt do noi
    // goi truyen tu JobApplication da nap (entity da map screening_captured_at tu FR-U09), khong doc lai cot nay. Quy
    // tac trang thai GIONG HET read(...): capturedAt null -> NOT_CAPTURED (dong ban chup neu co cung khong hien, R-A3
    // cua FR-H10); co capturedAt, 0 dong -> NO_QUESTIONS; >= 1 dong -> CAPTURED. KHONG kiem quyen - noi goi da kiem.
    // Map nhan null-value (don chua chup) nen key lay tu keySet, khong dung Map.copyOf.
    public Map<UUID, ScreeningBundle> readMany(Map<UUID, Instant> capturedAtByApplicationId) {
        if (capturedAtByApplicationId.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<ScreeningAnswerSnapshot>> answersByApplicationId = answerRepository
                .findByApplicationIdInOrderByApplicationIdAscDisplayOrderAsc(capturedAtByApplicationId.keySet())
                .stream()
                .collect(Collectors.groupingBy(
                        ApplicationScreeningAnswer::getApplicationId,
                        Collectors.mapping(ScreeningAnswerSnapshot::from, Collectors.toList())));

        Map<UUID, ScreeningBundle> result = new HashMap<>();
        capturedAtByApplicationId.forEach((applicationId, capturedAt) -> {
            if (capturedAt == null) {
                result.put(applicationId, new ScreeningBundle(ScreeningCaptureState.NOT_CAPTURED, null, List.of()));
                return;
            }
            List<ScreeningAnswerSnapshot> answers = answersByApplicationId.getOrDefault(applicationId, List.of());
            ScreeningCaptureState state =
                    answers.isEmpty() ? ScreeningCaptureState.NO_QUESTIONS : ScreeningCaptureState.CAPTURED;
            result.put(applicationId, new ScreeningBundle(state, capturedAt, answers));
        });
        return result;
    }

    // Ban chup sang loc cua mot don cho FR-H14 - chi dung noi bo backend. answers theo display_order tang dan; rong khi
    // khong phai CAPTURED.
    public record ScreeningBundle(
            ScreeningCaptureState state, Instant capturedAt, List<ScreeningAnswerSnapshot> answers) {

        public ScreeningBundle {
            answers = answers == null ? List.of() : List.copyOf(answers);
        }
    }
}
