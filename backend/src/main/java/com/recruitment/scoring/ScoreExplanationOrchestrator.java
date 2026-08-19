package com.recruitment.scoring;

import com.recruitment.ai.explanation.ScoreExplanationErrorCode;
import com.recruitment.ai.explanation.ScoreExplanationFailedException;
import com.recruitment.ai.explanation.ScoreExplanationResult;
import com.recruitment.ai.explanation.ScoreExplanationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// KHONG @Transactional o day - mau AggregationOrchestrator/ScoringRunOrchestrator (CLAUDE.md muc
// 3c): doc du lieu bang tung repository call doc lap, goi LLM NGOAI transaction
// (ScoreExplanationService.explain() co the mat vai giay), roi ghi ket qua bang mot transaction NGAN
// RIENG (ScoreExplanationStateService).
@Component
public class ScoreExplanationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ScoreExplanationOrchestrator.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;
    private final ScoreExplanationService scoreExplanationService;
    private final ScoreExplanationStateService stateService;

    public ScoreExplanationOrchestrator(
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository,
            ScoreExplanationService scoreExplanationService,
            ScoreExplanationStateService stateService) {
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
        this.scoreExplanationService = scoreExplanationService;
        this.stateService = stateService;
    }

    public void processOne(UUID scoringRunId) {
        try {
            doProcess(scoringRunId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi cung, mau AggregationOrchestrator.processOne - nhung KHAC ban chat:
            // ScoreAggregator (D3) la Java thuan mien phi, mot loi khong luong truoc o do khong tac
            // dung phu gi ca ngoai lam kẹt neu khong bat lai. O day (D4), mot loi khong luong truoc
            // CO THE xay ra SAU KHI da goi LLM that (ScoreExplanationService da tu bat rieng loi cua
            // chinh no va nem ScoreExplanationFailedException o nhanh ben duoi - nhanh nay chi con
            // bat phan con lai: loi doc DB, loi map du lieu...). Van BAT BUOC ghi attempts o day -
            // khong the de trong nhu D2/D3 "chi log roi thoi" - vi khong ghi thi nhip poll sau se
            // thu lai VO HAN cho toi khi loi tu het (Q5, ke hoach D4 dot duyet lai: moi lan thu la
            // mot lan LLM ton tien that, khac han phep tinh mien phi cua D3).
            log.debug("Loi khong luong truoc trong luc sinh bao cao giai thich: scoringRunId={}", scoringRunId, e);
            stateService.recordFailedAttempt(scoringRunId, ScoreExplanationErrorCode.LLM_ERROR);
        }
    }

    private void doProcess(UUID scoringRunId) {
        // Chi dung de xac nhan luot TON TAI (fail-fast bang NoSuchElementException neu khong, roi
        // ghi attempts o luoi an toan cua processOne) - KHONG doc field nao tu ScoringRun (khac
        // AggregationOrchestrator: D4 khong can rubric_snapshot, Q3 chi tinh tu criterion_scores.score).
        // Neu bo qua buoc nay, mot scoringRunId khong ton tai se lam findByScoringRunId tra ve danh
        // sach RONG thay vi loi ro rang, roi goi LLM voi danh sach tieu chi rong - vo nghia va ton
        // tien mot cach am tham.
        scoringRunRepository.findById(scoringRunId).orElseThrow();
        List<CriterionScore> scores = criterionScoreRepository.findByScoringRunId(scoringRunId);

        List<CriterionScoreExplanationInput> inputs = scores.stream()
                .map(s -> new CriterionScoreExplanationInput(
                        s.getCriterionNameSnapshot(),
                        s.getWeightSnapshot(),
                        s.getMaxScoreSnapshot(),
                        s.getScore(),
                        s.getReasoning()))
                .toList();

        // Q3 (ke hoach D4): Java thuan tinh met/missing theo score > 0 / score = 0 - dung luat D2
        // "evidence rong <=> score = 0" (khong nho LLM phan doan, khong dua vao ScoreExplanationPayload
        // - xem comment trong record do). So sanh BigDecimal bang compareTo(), KHONG equals(): score
        // co the la "0.00" (scale 2, tu NUMERIC(5,2)) trong khi ZERO la BigDecimal.ZERO (scale 0) -
        // equals() phan biet ca scale nen "0.00".equals(BigDecimal.ZERO) tra ve FALSE du cung gia
        // tri, la loi kinh dien khi so sanh BigDecimal; compareTo() moi so sanh dung GIA TRI.
        List<String> metCriteria = scores.stream()
                .filter(s -> s.getScore().compareTo(ZERO) > 0)
                .map(CriterionScore::getCriterionNameSnapshot)
                .toList();
        List<String> missingCriteria = scores.stream()
                .filter(s -> s.getScore().compareTo(ZERO) == 0)
                .map(CriterionScore::getCriterionNameSnapshot)
                .toList();

        ScoreExplanationResult result;
        try {
            result = scoreExplanationService.explain(inputs);
        } catch (ScoreExplanationFailedException e) {
            stateService.recordFailedAttempt(scoringRunId, e.errorCode());
            return;
        }

        try {
            stateService.saveExplanation(scoringRunId, result, metCriteria, missingCriteria);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE tren scoring_run_id (V1) la CHOT CHAN THAT chong ghi trung khi hai nhip poll
            // chong nhau (D4 KHONG claim rieng - cung tinh than voi D3, xem comment trong
            // ScoreExplanation) - day la NO-OP AN TOAN, KHONG phai loi: mot lan goi khac da ghi bao
            // cao THAT truoc do roi, khong can lam gi them. KHAC voi nhanh loi LLM o tren - o day
            // KHONG ghi attempts, vi day khong phai mot lan thu that bai, ma la mot lan thu (da goi
            // LLM, da co ket qua hop le) bi tre chan boi UNIQUE luc ghi, ban chat la "da co nguoi
            // khac lam xong roi" chu khong phai "lan nay khong lam duoc".
            log.debug(
                    "score_explanations da co dong tu truoc (mot nhip poll khac da ghi truoc do) - bo qua: scoringRunId={}",
                    scoringRunId);
        }
    }
}
