package com.recruitment.scoring;

import com.recruitment.ai.explanation.ScoreExplanationPayload;
import com.recruitment.ai.explanation.ScoreExplanationResult;
import com.recruitment.common.FormattedErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho sinh bao cao giai thich - xem CLAUDE.md muc 3c va ScoringRunStateService. KHONG
// method nao o day duoc goi qua self-invocation tu ScoreExplanationOrchestrator - phai qua bean nay
// de @Transactional di qua proxy Spring.
@Service
public class ScoreExplanationStateService {

    private final ScoreExplanationRepository scoreExplanationRepository;
    private final ScoreExplanationAttemptRepository scoreExplanationAttemptRepository;

    public ScoreExplanationStateService(
            ScoreExplanationRepository scoreExplanationRepository,
            ScoreExplanationAttemptRepository scoreExplanationAttemptRepository) {
        this.scoreExplanationRepository = scoreExplanationRepository;
        this.scoreExplanationAttemptRepository = scoreExplanationAttemptRepository;
    }

    // saveAndFlush: bat INSERT chay NGAY trong pham vi transaction nay, de
    // DataIntegrityViolationException cua UNIQUE tren scoring_run_id (V1) noi len ngay tai day cho
    // ScoreExplanationOrchestrator bat duoc va coi la no-op (hai nhip poll chong nhau ghi cung mot
    // luot) - cung ly do voi ScoringRunStateService.create/uq_scoring_run_in_progress. metCriteria/
    // missingCriteria nhan tu THAM SO (Java thuan tinh o Orchestrator, xem Q3), KHONG doc tu
    // result.payload() - payload cua LLM khong co hai truong nay (xem ScoreExplanationPayload).
    @Transactional
    public void saveExplanation(
            UUID scoringRunId, ScoreExplanationResult result, List<String> metCriteria, List<String> missingCriteria) {
        ScoreExplanation explanation = new ScoreExplanation();
        explanation.setScoringRunId(scoringRunId);
        explanation.setSummary(result.payload().summary());
        explanation.setStrengths(toExplanationPoints(result.payload().strengths()));
        explanation.setWeaknesses(toExplanationPoints(result.payload().weaknesses()));
        explanation.setMetCriteria(metCriteria);
        explanation.setMissingCriteria(missingCriteria);
        explanation.setModel(result.model());
        explanation.setPromptVersion(result.promptVersion());
        scoreExplanationRepository.saveAndFlush(explanation);
    }

    // Anh xa ScoreExplanationPayload.CriterionPoint (ai.explanation, doc lap DB) -> ExplanationPoint
    // (scoring, JSONB cua score_explanations.strengths/weaknesses) - cung tinh than voi
    // ScoringRunStateService.toEvidenceEntries.
    private static List<ExplanationPoint> toExplanationPoints(List<ScoreExplanationPayload.CriterionPoint> points) {
        return points.stream().map(p -> new ExplanationPoint(p.criterionName(), p.point())).toList();
    }

    // Goi khi ScoreExplanationOrchestrator that bai sau retry (LLM loi/validate sai - errorCode tu
    // ScoreExplanationFailedException) HOAC bat duoc mot RuntimeException khong luong truoc (luoi an
    // toan cuoi cung, xem ScoreExplanationOrchestrator.processOne). Upsert nguyen tu, xem
    // ScoreExplanationAttemptRepository.recordFailedAttempt (Dot 1) - @Transactional o day BAT BUOC
    // vi recordFailedAttempt la mot @Modifying query doi hoi mot transaction dang mo de thuc thi.
    @Transactional
    public void recordFailedAttempt(UUID scoringRunId, FormattedErrorCode errorCode) {
        scoreExplanationAttemptRepository.recordFailedAttempt(scoringRunId, errorCode.formatted());
    }
}
