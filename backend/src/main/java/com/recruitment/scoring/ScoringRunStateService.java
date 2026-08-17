package com.recruitment.scoring;

import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho scoring - xem CLAUDE.md muc 3c va ResumeParsingStateService. Method create()
// o Dot nay; claim()/recordCriterionScore()/markFailed() se them o Dot 4. KHONG method nao o day
// duoc goi qua self-invocation tu ScoringRunService/ScoringRunOrchestrator - phai qua bean nay de
// @Transactional di qua proxy Spring.
@Service
public class ScoringRunStateService {

    private final ScoringRunRepository scoringRunRepository;
    private final RubricRepository rubricRepository;

    public ScoringRunStateService(ScoringRunRepository scoringRunRepository, RubricRepository rubricRepository) {
        this.scoringRunRepository = scoringRunRepository;
        this.rubricRepository = rubricRepository;
    }

    // Q6 (ke hoach D2): khoa rubric CUNG transaction voi viec tao scoring_runs, ngay tai thoi diem
    // tao (khong doi den luc claim/dong tieu chi dau tien) - dong voi thoi diem chup rubric_snapshot.
    // Vi ca hai cau ghi (INSERT scoring_runs, UPDATE rubrics.is_locked) cung mot @Transactional,
    // Spring commit/rollback ca hai cung luc - khong bao gio co trang thai nua voi (tao duoc
    // scoring_runs ma khoa rubric that bai, hay nguoc lai).
    //
    // Doc lai Rubric TUOI trong chinh transaction nay (khong dung lai object da load o
    // ScoringRunService) - cung tinh than voi ResumeParsingStateService.markDone doc lai Resume
    // moi thay vi tin object caller dang cam san.
    //
    // Khoa idempotent: chi UPDATE khi dang false. Lot cham thu hai tro di cho cung job la no-op
    // tren co khoa, khong loi (tinh huong 2 cua Q6).
    @Transactional
    public ScoringRun create(UUID applicationId, RubricSnapshot rubricSnapshot, UUID rubricId) {
        ScoringRun run = new ScoringRun();
        run.setApplicationId(applicationId);
        run.setStatus(ScoringRunStatus.PENDING);
        run.setRubricSnapshot(rubricSnapshot);
        // saveAndFlush: bat INSERT no ngay tai day thay vi hoan toi luc commit, de
        // DataIntegrityViolationException cua uq_scoring_run_in_progress (V4) noi len trong pham
        // vi request va GlobalExceptionHandler bat duoc, tra 409 xac dinh - cung ly do
        // ApplicationService.apply da dung saveAndFlush cho uq_application_per_cycle.
        run = scoringRunRepository.saveAndFlush(run);

        // Chi la luoi an toan: rubric da duoc ScoringRunService xac nhan ton tai va du dieu kien
        // ngay truoc do trong cung request. Khong dung RubricNotFoundException (danh cho 404
        // nguoi dung that) - day khong phai duong loi nguoi dung co the tao ra.
        Rubric rubric = rubricRepository.findById(rubricId).orElseThrow();
        if (!rubric.isLocked()) {
            rubric.setLocked(true);
            rubricRepository.save(rubric);
        }

        return run;
    }
}
