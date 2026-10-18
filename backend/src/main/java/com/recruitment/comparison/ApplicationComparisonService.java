package com.recruitment.comparison;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.CandidateAlias;
import com.recruitment.blindreview.IdentityMaskSource;
import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.common.exception.CompareApplicationNotScoredException;
import com.recruitment.common.exception.InvalidCompareSelectionException;
import com.recruitment.comparison.dto.ApplicationComparisonResponse;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.Column;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.CriterionCell;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.CriterionRow;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.EducationItem;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.Experience;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.ExperienceRow;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.ExplanationCell;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.IntRow;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.ProfileCell;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.Row;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.Screening;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.ScreeningCell;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.ScreeningCellState;
import com.recruitment.comparison.dto.ApplicationComparisonResponse.ScreeningRow;
import com.recruitment.job.Job;
import com.recruitment.jobapplication.ApplicationComparisonSource;
import com.recruitment.jobapplication.ApplicationOwnerService;
import com.recruitment.jobapplication.ScoreTextMasking;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.resume.ExperienceCalculator;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.scoring.RubricSnapshot;
import com.recruitment.scoring.ScoringRunStatus;
import com.recruitment.screening.ApplicationScreeningReader;
import com.recruitment.screening.ApplicationScreeningReader.ScreeningBundle;
import com.recruitment.screening.ScreeningAnswerSnapshot;
import com.recruitment.screening.ScreeningCaptureState;
import com.recruitment.screening.ScreeningQuestionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

// FR-H14 C1 - DIEM DUNG DTO DUY NHAT cua bang so sanh (R-B1): FR-H16 che thong tin nhan than tai CHINH method compare.
// KHONG @Transactional (ke ca readOnly) - cung quy uoc ApplicationOwnerService: chi doc, cac repository doc lap, JSON doc
// qua @JdbcTypeCode trong cung cau SELECT. Khong goi AI, khong ghi gi (R-A1, R-A2). Hang va tong diem lay NGUYEN tu
// rankApplications cua FR-H05 qua evaluateApplications (mot cong thuc hang - R-D3), khong tinh lai trong nhom.
// So query co dinh (REQUIREMENT muc 4.6): 2 so huu + 9 rankApplications + 1 resume_parsed_data + 1 ban chup sang loc.
@Service
public class ApplicationComparisonService {

    private final ApplicationOwnerService applicationOwnerService;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final ApplicationScreeningReader applicationScreeningReader;
    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public ApplicationComparisonService(
            ApplicationOwnerService applicationOwnerService,
            ResumeParsedDataRepository resumeParsedDataRepository,
            ApplicationScreeningReader applicationScreeningReader,
            BlindReviewResolver blindReviewResolver,
            IdentityMaskSource identityMaskSource) {
        this.applicationOwnerService = applicationOwnerService;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.applicationScreeningReader = applicationScreeningReader;
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
    }

    public ApplicationComparisonResponse compare(UUID ownerId, UUID jobId, String idsParam) {
        // R-S7: quyen Job -> R-S2/R-S1 (parser) -> R-S3 -> R-S4 -> R-S5. Loi dau tien gap duoc tra ve.
        Job job = applicationOwnerService.loadOwnedJob(jobId, ownerId);
        List<UUID> ids = CompareSelectionParser.parse(idsParam);

        Map<UUID, ApplicationComparisonSource> sourceById = applicationOwnerService.evaluateApplications(job, ids);
        // R-S3: don khong ton tai / thuoc Job khac / cong ty khac -> CUNG mot cau (khong lo don co ton tai hay khong).
        if (sourceById.size() != ids.size()) {
            throw InvalidCompareSelectionException.notInJob();
        }
        List<ApplicationComparisonSource> unmaskedSources = ids.stream().map(sourceById::get).toList();   // R-O1
        if (unmaskedSources.stream().map(ApplicationComparisonSource::recruitmentCycle).distinct().count() > 1) {
            throw InvalidCompareSelectionException.differentCycles();                               // R-S4
        }
        if (unmaskedSources.stream().anyMatch(source -> source.scoringRunId() == null)) {
            throw new CompareApplicationNotScoredException();                                       // R-S5
        }

        // FR-H16 (H14 R-B1 - diem che duy nhat cua bang so sanh): moi COT xet theo don cua no (R-S1). Mot cau co an danh +
        // hai cau gia tri danh tinh cho ca nhom (khong N+1). Che SAU khi da co tong diem/hang (R-A2): ten -> nhan, chuoi
        // cham diem qua ScoreTextMasking (cung kieu voi danh sach theo Job, L6); ho so tu CV va o sang loc SHORT_TEXT che
        // theo cot ben duoi. maskers.get(id) == null <=> cot khong an danh.
        Set<UUID> maskedIds = blindReviewResolver.maskedAmong(ids);
        Map<UUID, IdentityMasker> maskers = identityMaskSource.load(maskedIds);
        List<IdentityMasker> maskerPerColumn =
                unmaskedSources.stream().map(source -> maskers.get(source.applicationId())).toList();
        List<ApplicationComparisonSource> sources = unmaskedSources.stream()
                .map(source -> masked(source, maskers.get(source.applicationId())))
                .toList();

        Map<UUID, ResumeParsedData> parsedByResumeId = resumeParsedDataRepository
                .findByResumeIdIn(sources.stream().map(ApplicationComparisonSource::resumeId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ResumeParsedData::getResumeId, Function.identity()));
        // Map cho phep value null (don chua chup) - khong dung Map.of/Collectors.toMap.
        Map<UUID, Instant> capturedAtByApplicationId = new HashMap<>();
        sources.forEach(source -> capturedAtByApplicationId.put(source.applicationId(), source.screeningCapturedAt()));
        Map<UUID, ScreeningBundle> screeningById = applicationScreeningReader.readMany(capturedAtByApplicationId);

        List<CriterionRow> criteria = buildCriterionRows(sources);
        List<ResumeParsedData> parsedPerColumn =
                sources.stream().map(source -> parsedByResumeId.get(source.resumeId())).toList();
        List<ScreeningBundle> screeningPerColumn =
                sources.stream().map(source -> screeningById.get(source.applicationId())).toList();

        List<BigDecimal> totalScores = sources.stream().map(ApplicationComparisonSource::totalScore).toList();
        List<Integer> ranks = sources.stream().map(ApplicationComparisonSource::rank).toList();
        List<Experience> experiences = parsedPerColumn.stream().map(ApplicationComparisonService::toExperience).toList();

        return new ApplicationComparisonResponse(
                job.getId(),
                job.getTitle(),
                sources.getFirst().recruitmentCycle(),
                criteria.stream().anyMatch(CriterionRow::rubricVersionsDiffer),                        // R-C6
                sources.stream().map(ApplicationComparisonService::toColumn).toList(),
                new Row(totalScores, decimalsDiffer(totalScores)),
                new IntRow(ranks, valuesDiffer(ranks)),
                criteria,
                new ExperienceRow(experiences, valuesDiffer(experiences.stream().map(Experience::months).toList())),
                IntStream.range(0, sources.size())
                        .mapToObj(column -> toProfile(parsedPerColumn.get(column), maskerPerColumn.get(column)))
                        .toList(),
                sources.stream().map(ApplicationComparisonService::toExplanation).toList(),
                buildScreening(screeningPerColumn, maskerPerColumn));
    }

    // FR-H16 - ban sao cua nguon mot cot khi don dang an danh; diem, hang, trang thai giu nguyen.
    private static ApplicationComparisonSource masked(ApplicationComparisonSource source, IdentityMasker masker) {
        if (masker == null) {
            return source;
        }
        return new ApplicationComparisonSource(
                source.applicationId(),
                CandidateAlias.of(source.applicationId()),
                source.status(),
                source.appliedAt(),
                source.resumeId(),
                source.recruitmentCycle(),
                source.screeningCapturedAt(),
                source.scoringRunId(),
                source.scoredAt(),
                source.totalScore(),
                source.rank(),
                source.latestRunStatus(),
                ScoreTextMasking.criterionScores(source.criterionScores(), masker),
                source.rubricCriteria(),
                source.explanationStatus(),
                ScoreTextMasking.explanation(source.explanation(), masker));
    }

    // Cot khong an danh (masker null) giu nguyen van.
    private static String mask(IdentityMasker masker, String text) {
        return masker == null ? text : masker.mask(text);
    }

    private static Column toColumn(ApplicationComparisonSource source) {
        // R-D10 [Q6]: luot moi nhat PENDING/RUNNING thi chac chan moi hon luot DONE dang hien. RUNNING luon chua co
        // total_score (finishAggregation dat DONE va total_score trong cung mot UPDATE). Luot moi nhat FAILED -> false.
        boolean newerRunInProgress = source.latestRunStatus() == ScoringRunStatus.PENDING
                || source.latestRunStatus() == ScoringRunStatus.RUNNING;
        return new Column(
                source.applicationId(),
                source.candidateName(),
                source.status(),
                source.appliedAt(),
                source.scoringRunId(),
                source.scoredAt(),
                newerRunInProgress);
    }

    // ---- Tieu chi (R-C1..R-C6) ----

    // Khoa ghep giua cac cot = criterionId trong rubric_snapshot cua luot DONE (R-C1); criterionId null -> "name:" + ten
    // (R-C2). Trong MOT luot, diem gan voi tieu chi snapshot THEO TEN (giu dung cach buildCriterionScores); tieu chi
    // snapshot khong co dong diem thi bo qua (cung luoi an toan cua buildCriterionScores). Thu tu hang: snapshot cot 1,
    // tieu chi chi co o cot sau them vao cuoi theo thu tu snapshot cua cot do (R-C3).
    private static List<CriterionRow> buildCriterionRows(List<ApplicationComparisonSource> sources) {
        int columnCount = sources.size();
        Map<String, CriterionCell[]> cellsByKey = new LinkedHashMap<>();
        for (int column = 0; column < columnCount; column++) {
            ApplicationComparisonSource source = sources.get(column);
            Map<String, ApplicationHrListItemResponse.CriterionScoreItem> scoreByName = source.criterionScores().stream()
                    .collect(Collectors.toMap(
                            ApplicationHrListItemResponse.CriterionScoreItem::criterionNameSnapshot,
                            Function.identity(),
                            (first, second) -> first));
            for (RubricSnapshot.CriterionSnapshot criterion : source.rubricCriteria()) {
                ApplicationHrListItemResponse.CriterionScoreItem item = scoreByName.get(criterion.name());
                if (item == null) {
                    continue;
                }
                String key = criterion.criterionId() != null
                        ? criterion.criterionId().toString()
                        : "name:" + criterion.name();
                cellsByKey.computeIfAbsent(key, ignored -> new CriterionCell[columnCount])[column] = new CriterionCell(
                        item.criterionNameSnapshot(),
                        item.score(),
                        item.maxScoreSnapshot(),
                        item.weightSnapshot(),
                        item.reasoning(),
                        item.evidence());
            }
        }

        List<CriterionRow> rows = new ArrayList<>(cellsByKey.size());
        cellsByKey.forEach((key, cellArray) -> {
            List<CriterionCell> cells = Arrays.asList(cellArray);
            List<CriterionCell> present = cells.stream().filter(Objects::nonNull).toList();
            CriterionCell first = present.getFirst();
            // R-C4 (thieu o mot cot) hoac R-C5 (khac ten/trong so/diem toi da) -> lech phien ban; khong quy doi diem.
            boolean versionsDiffer = present.size() < cells.size()
                    || present.stream().anyMatch(cell -> !cell.criterionName().equals(first.criterionName())
                            || cell.maxScore() != first.maxScore()
                            || cell.weight().compareTo(first.weight()) != 0);
            List<BigDecimal> scores = cells.stream().map(cell -> cell == null ? null : cell.score()).toList();
            rows.add(new CriterionRow(key, first.criterionName(), versionsDiffer, decimalsDiffer(scores), cells));
        });
        return rows;
    }

    // ---- Ho so tu CV (R-D5..R-D7) ----

    // R-D5 (S1): khong co dong trich xuat, chua tinh (experience_computed_at null) hoac months null -> ca hai null.
    // Nam quy doi bang CHINH ExperienceCalculator.toYears (khong cong thuc thu hai).
    private static Experience toExperience(ResumeParsedData parsed) {
        if (parsed == null || parsed.getExperienceComputedAt() == null || parsed.getExperienceMonths() == null) {
            return new Experience(null, null);
        }
        int months = parsed.getExperienceMonths();
        return new Experience(months, ExperienceCalculator.toYears(months));
    }

    // R-D6, R-D7, R-D11: chuoi nguyen van CV, dung thu tu; hoc van khong co diem trung binh [Q10]. FR-H16 R-M4: cot dang
    // an danh -> moi chuoi ky nang/hoc van qua bo che.
    private static ProfileCell toProfile(ResumeParsedData parsed, IdentityMasker masker) {
        if (parsed == null || parsed.getData() == null) {
            return new ProfileCell(false, List.of(), List.of());
        }
        ResumeParsedPayload data = parsed.getData();
        List<EducationItem> education = data.education().stream()
                .map(item -> new EducationItem(
                        mask(masker, item.degree()),
                        mask(masker, item.major()),
                        mask(masker, item.school()),
                        mask(masker, item.startDate()),
                        mask(masker, item.endDate())))
                .toList();
        List<String> skills = masker == null ? data.skills() : data.skills().stream().map(masker::mask).toList();
        return new ProfileCell(true, skills, education);
    }

    // R-D8, R-W4: chi tom tat + diem manh/yeu cua CHINH luot DONE; khong hai danh sach tieu chi dat/thieu.
    private static ExplanationCell toExplanation(ApplicationComparisonSource source) {
        ApplicationHrListItemResponse.Explanation explanation = source.explanation();
        if (explanation == null) {
            return new ExplanationCell(null, List.of(), List.of(), source.explanationStatus());
        }
        return new ExplanationCell(
                explanation.summary(), explanation.strengths(), explanation.weaknesses(), source.explanationStatus());
    }

    // ---- Sang loc (R-Q1..R-Q6) ----

    // Mot hang = mot cau hoi GIONG HET nhau giua cac don: cung questionId (khac null) VA cung loai, cau chu, phuong an
    // (R-Q1; required khac nhau khong tach hang). Khac noi dung hoac questionId null -> hang rieng (R-Q2). Khong ghep
    // theo display_order. Thu tu hang: display_order cot 1, hang chi co o cot sau them vao cuoi (R-Q3).
    private static Screening buildScreening(List<ScreeningBundle> bundles, List<IdentityMasker> maskerPerColumn) {
        int columnCount = bundles.size();
        Map<Object, ScreeningRowBuilder> rowsByKey = new LinkedHashMap<>();
        for (int column = 0; column < columnCount; column++) {
            ScreeningBundle bundle = bundles.get(column);
            if (bundle.state() != ScreeningCaptureState.CAPTURED) {
                continue;
            }
            for (ScreeningAnswerSnapshot answer : bundle.answers()) {
                Object key = answer.questionId() != null
                        ? new QuestionKey(answer.questionId(), answer.questionType(), answer.questionText(),
                                answer.options())
                        : new UnlinkedQuestionKey(column, answer.displayOrder());
                rowsByKey.computeIfAbsent(key, ignored -> new ScreeningRowBuilder(answer, columnCount))
                        .answers[column] = answer;
            }
        }

        List<ScreeningRow> rows = new ArrayList<>(rowsByKey.size());
        for (ScreeningRowBuilder builder : rowsByKey.values()) {
            List<ScreeningCell> cells = new ArrayList<>(columnCount);
            for (int column = 0; column < columnCount; column++) {
                cells.add(toScreeningCell(
                        bundles.get(column).state(), builder.answers[column], maskerPerColumn.get(column)));
            }
            // R-W2: SHORT_TEXT (hang van ban) luon false; YES_NO/SINGLE_CHOICE so cau tra loi, moi trang thai khac
            // ANSWERED la mot gia tri rieng.
            boolean differ = builder.questionType != ScreeningQuestionType.SHORT_TEXT
                    && cells.stream().map(ApplicationComparisonService::screeningValue).distinct().count() > 1;
            rows.add(new ScreeningRow(builder.questionType, builder.questionText, builder.options, differ, cells));
        }

        return new Screening(
                bundles.stream().map(ScreeningBundle::state).toList(),
                bundles.stream().map(ScreeningBundle::capturedAt).collect(Collectors.toList()),
                rows);
    }

    // R-Q4: NOT_CAPTURED/NO_QUESTIONS theo trang thai don; CAPTURED khong co hang do -> NOT_ASKED; co hang, khong tra loi
    // -> NOT_ANSWERED; co tra loi -> nguyen van answer_text / answer_yes_no.
    // FR-H16 R-M4: cot dang an danh -> answer_text cua cau SHORT_TEXT qua bo che; SINGLE_CHOICE (phuong an do HR dat) va
    // YES_NO giu nguyen (H14 R-B3).
    private static ScreeningCell toScreeningCell(
            ScreeningCaptureState state, ScreeningAnswerSnapshot answer, IdentityMasker masker) {
        if (state == ScreeningCaptureState.NOT_CAPTURED) {
            return new ScreeningCell(ScreeningCellState.NOT_CAPTURED, null, null);
        }
        if (state == ScreeningCaptureState.NO_QUESTIONS) {
            return new ScreeningCell(ScreeningCellState.NO_QUESTIONS, null, null);
        }
        if (answer == null) {
            return new ScreeningCell(ScreeningCellState.NOT_ASKED, null, null);
        }
        if (answer.answerText() == null && answer.answerYesNo() == null) {
            return new ScreeningCell(ScreeningCellState.NOT_ANSWERED, null, null);
        }
        String answerText = answer.questionType() == ScreeningQuestionType.SHORT_TEXT
                ? mask(masker, answer.answerText())
                : answer.answerText();
        return new ScreeningCell(ScreeningCellState.ANSWERED, answerText, answer.answerYesNo());
    }

    private static Object screeningValue(ScreeningCell cell) {
        return cell.state() == ScreeningCellState.ANSWERED
                ? List.of(String.valueOf(cell.answerText()), String.valueOf(cell.answerYesNo()))
                : cell.state();
    }

    // ---- So sanh gia tri theo hang (R-W2) ----

    // O thieu (null) la mot gia tri rieng; so BigDecimal bang compareTo (70.000 = 70.0).
    private static boolean decimalsDiffer(List<BigDecimal> values) {
        BigDecimal first = values.getFirst();
        return values.stream().anyMatch(value -> first == null
                ? value != null
                : value == null || value.compareTo(first) != 0);
    }

    private static boolean valuesDiffer(List<?> values) {
        Object first = values.getFirst();
        return values.stream().anyMatch(value -> !Objects.equals(value, first));
    }

    private record QuestionKey(UUID questionId, ScreeningQuestionType type, String text, List<String> options) {
    }

    // questionId null -> hang rieng cua dung don do (R-Q2).
    private record UnlinkedQuestionKey(int column, int displayOrder) {
    }

    private static final class ScreeningRowBuilder {
        private final ScreeningQuestionType questionType;
        private final String questionText;
        private final List<String> options;
        private final ScreeningAnswerSnapshot[] answers;

        private ScreeningRowBuilder(ScreeningAnswerSnapshot first, int columnCount) {
            this.questionType = first.questionType();
            this.questionText = first.questionText();
            this.options = first.options();
            this.answers = new ScreeningAnswerSnapshot[columnCount];
        }
    }
}
