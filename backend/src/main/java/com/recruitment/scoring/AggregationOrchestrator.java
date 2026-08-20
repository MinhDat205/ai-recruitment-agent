package com.recruitment.scoring;

import com.recruitment.jobapplication.JobApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.notification.AggregationFinishedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// KHONG method nao o day duoc @Transactional - xem CLAUDE.md muc 3c va ScoringRunOrchestrator.
// KHAC voi ScoringRunOrchestrator: KHONG co buoc claim rieng, va KHONG import gi tu package ai/ -
// day la rang buoc cung nhat cua nhanh D3 (FR-H05): tong hop la phep cong co trong so xac dinh,
// Java thuan, KHONG goi LLM o bat ky dau. Toan bo phep tinh (doc criterion_scores, kiem toan ven,
// cong) chay xong trong mot lan doc DB + tinh toan trong bo nho - khong co ly do de giu transaction
// mo hay claim truoc mot buoc cham (khong ton tai o day).
@Component
public class AggregationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AggregationOrchestrator.class);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final ScoringRunStateService stateService;
    private final ScoringRunRepository scoringRunRepository;
    private final CriterionScoreRepository criterionScoreRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AggregationOrchestrator(
            ScoringRunStateService stateService,
            ScoringRunRepository scoringRunRepository,
            CriterionScoreRepository criterionScoreRepository,
            JobApplicationRepository jobApplicationRepository,
            ApplicationEventPublisher eventPublisher) {
        this.stateService = stateService;
        this.scoringRunRepository = scoringRunRepository;
        this.criterionScoreRepository = criterionScoreRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.eventPublisher = eventPublisher;
    }

    public void processOne(UUID scoringRunId) {
        try {
            doProcess(scoringRunId);
        } catch (RuntimeException e) {
            // Luoi an toan cuoi cung, mau ScoringRunOrchestrator.processOne - nhung hau qua cua
            // mot luot D3 ket LAI NHE HON D2 (xem AggregationOrchestrator javadoc tren dau file va
            // bao cao Dot 3, ke hoach D3): luot toi duoc day da co finished_at KHAC NULL tu D2 -
            // uq_scoring_run_in_progress (predicate doi finished_at IS NULL) KHONG chan HR tao
            // luot cham moi cho don nay, du luot cu co ket lai vinh vien. Va vi D3 KHONG claim
            // (khong doi status), mot luot chua kip markFailed (vd JVM crash giua doProcess) VAN
            // con nam trong pham vi query nhat luot (status='RUNNING' AND finished_at IS NOT NULL
            // AND total_score IS NULL) - nhip poll SAU se tu dong thu lai, khong ket cung nhu D2
            // (D2 chi quet PENDING, mot luot da RUNNING roi crash se vinh vien vo hinh voi poller).
            // Van giu luoi an toan nay vi hai ly do: (1) neu loi la that su vinh vien (khong phai
            // JVM crash), khong co no luot se bi thu lai MOI 5 giay vo han, spam log/DB; markFailed
            // dung retry lai va de lai tin hieu FAILED ro rang cho van hanh. (2) nhat quan voi mau
            // job nen da chot trong toan bo du an (CLAUDE.md muc 3c) - moi job nen deu co dung
            // hinh dang nay, lech o day se gay kho hieu khi review/bao tri sau nay.
            log.debug("Loi khong luong truoc trong luc tong hop diem: scoringRunId={}", scoringRunId, e);
            stateService.markFailed(scoringRunId, ScoreAggregationErrorCode.UNEXPECTED_ERROR);
        }
    }

    private void doProcess(UUID scoringRunId) {
        ScoringRun run = scoringRunRepository.findById(scoringRunId).orElseThrow();
        List<CriterionScore> scores = criterionScoreRepository.findByScoringRunId(scoringRunId);

        AggregationResult result;
        try {
            List<String> scoredCriterionNames =
                    scores.stream().map(CriterionScore::getCriterionNameSnapshot).toList();
            AggregationIntegrityChecker.requireMatchesSnapshot(run.getRubricSnapshot(), scoredCriterionNames);

            List<CriterionScoreInput> inputs = scores.stream()
                    .map(s -> new CriterionScoreInput(s.getScore(), s.getMaxScoreSnapshot(), s.getWeightSnapshot()))
                    .toList();
            result = ScoreAggregator.aggregate(inputs);
        } catch (ScoreAggregationException e) {
            // Ca hai nguon loi (AggregationIntegrityChecker.CRITERIA_MISMATCH,
            // ScoreAggregator.INVALID_WEIGHT_SUM) deu la loi toan ven du lieu, xu ly GIONG HET
            // nhau: markFailed voi DUNG ma loi cua chinh no (khong boc lai thanh UNEXPECTED_ERROR),
            // KHONG cong, dung lai ngay. markFailed tu giu nguyen finished_at da co (guard sua o
            // Dot 2, ke hoach D3) - moc "D2 cham xong" khong bi ghi de boi thoi diem D3 phat hien
            // loi.
            stateService.markFailed(scoringRunId, e.errorCode());
            return;
        }

        // Q4 (ke hoach D3): Sigma(weight_snapshot) ve ly thuyet luon la 100 (da kiem luc TAO luot
        // cham), nhung rubric_snapshot la du lieu cu khong doi lai duoc - neu lech, VAN cong binh
        // thuong (cong thuc tu chuan hoa, xem ScoreAggregator) nhung phai de lai dau vet van hanh.
        // KHONG dung log.debug: day la du lieu bat thuong can duoc de y, khong phai chi tiet ky
        // thuat noi bo; va KHONG log noi dung criterion_scores (reasoning la van ban do LLM sinh tu
        // CV - du lieu ca nhan, chi duoc log.debug theo CLAUDE.md muc 4) - o day chi log dung hai
        // con so (scoringRunId, weightSum), khong dinh kem gi khac.
        if (result.weightSum().compareTo(ONE_HUNDRED) != 0) {
            log.warn(
                    "Trong so snapshot (weight_snapshot) cua luot cham nay khong bang 100%: scoringRunId={}, weightSum={}",
                    scoringRunId,
                    result.weightSum());
        }

        boolean written = stateService.finishAggregation(scoringRunId, result.totalScore());
        if (!written) {
            // Khong phai loi - mot nhip poll khac da ghi truoc do (ca hai deu tinh ra CUNG gia tri
            // tu cung du lieu da commit, xem ScoringRunRepository.finishAggregation). Chi log.debug
            // de truy vet neu can, khong markFailed, KHONG publish event (nhip da ghi truoc do da
            // publish roi - publish lai o day se tao thong bao trung).
            log.debug(
                    "finishAggregation khong ghi duoc (co the da duoc mot nhip khac ghi truoc do): scoringRunId={}",
                    scoringRunId);
            return;
        }

        // FR-C03: publish NGOAI transaction (doProcess khong @Transactional - xem CLAUDE.md muc
        // 3c) - KHONG publish trong ScoringRunStateService.finishAggregation, tranh keo dai
        // transaction ghi ngan cua no chi de phuc vu thong bao. run.getApplicationId() da co san
        // tu lan doc dau method, chi can them 1 truy van de lay jobId (chap nhan duoc vi dang o
        // ngoai moi transaction).
        JobApplication application =
                jobApplicationRepository.findById(run.getApplicationId()).orElseThrow();
        eventPublisher.publishEvent(
                new AggregationFinishedEvent(scoringRunId, run.getApplicationId(), application.getJobId()));
    }
}
