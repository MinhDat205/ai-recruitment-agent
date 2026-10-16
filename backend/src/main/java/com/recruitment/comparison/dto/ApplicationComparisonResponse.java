package com.recruitment.comparison.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import com.recruitment.screening.ScreeningCaptureState;
import com.recruitment.screening.ScreeningQuestionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

// FR-H14 C1 - DTO DUY NHAT cua bang so sanh (R-B1, diem che cua FR-H16 - danh sach field can che o REQUIREMENT R-B3).
// Khong field phan quyet hay danh dau nguoi thang nao (R-W3, CLAUDE.md muc 7); khong id cau hoi, khong id tieu chi tho
// (ngoai CriterionRow.key), khong van ban tho CV, model, phien ban prompt, token, lien he, diem trung binh hoc tap
// (Q10), hai danh sach tieu chi dat/thieu cua FR-H06 (R-W4). Moi List chuan hoa null -> List.of(), TRU
// CriterionRow.cells (phan tu null co nghia - R-C4). So tu DB giu BigDecimal nhu DTO hien co.
public record ApplicationComparisonResponse(
        UUID jobId,
        String jobTitle,
        int recruitmentCycle,
        boolean rubricVersionsDiffer,                 // R-C6
        List<Column> columns,                         // dung thu tu ids (R-O1), 2..3 phan tu
        Row totalScore,                               // R-D2
        IntRow rank,                                  // R-D3 [Q2]
        List<CriterionRow> criteria,                  // R-C
        ExperienceRow experience,                     // R-D5
        List<ProfileCell> profiles,                   // R-D6, R-D7 - theo thu tu columns
        List<ExplanationCell> explanations,           // R-D8 - theo thu tu columns
        Screening screening) {                        // R-Q

    public ApplicationComparisonResponse {
        columns = columns == null ? List.of() : columns;
        criteria = criteria == null ? List.of() : criteria;
        profiles = profiles == null ? List.of() : profiles;
        explanations = explanations == null ? List.of() : explanations;
    }

    public record Column(
            UUID applicationId,
            String candidateName,
            ApplicationStatus status,
            Instant appliedAt,
            UUID scoringRunId,                        // luot DONE moi nhat
            Instant scoredAt,
            boolean newerRunInProgress) {             // R-D10 [Q6]
    }

    // Gia tri so theo cot; valuesDiffer = khong giong nhau o moi cot (R-W2)
    public record Row(List<BigDecimal> values, boolean valuesDiffer) {    // tong diem (so sanh bang compareTo)

        public Row {
            values = values == null ? List.of() : values;
        }
    }

    public record IntRow(List<Integer> values, boolean valuesDiffer) {    // hang (FR-H05), cung kieu Integer voi danh sach

        public IntRow {
            values = values == null ? List.of() : values;
        }
    }

    public record CriterionRow(
            String key,                               // criterionId (hoac "name:" + ten, R-C2) - chi de React key
            String criterionName,                     // ten o cot dau tien co tieu chi (R-C5)
            boolean rubricVersionsDiffer,             // R-C4/R-C5
            boolean valuesDiffer,
            List<CriterionCell> cells) {              // theo thu tu columns; phan tu null = khong co tieu chi (R-C4)

        // KHONG List.of()/List.copyOf(): ca hai nem NPE voi phan tu null.
        public CriterionRow {
            cells = cells == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(cells));
        }
    }

    public record CriterionCell(
            String criterionName,
            BigDecimal score,
            int maxScore,
            BigDecimal weight,
            String reasoning,
            List<EvidenceEntry> evidence) {

        public CriterionCell {
            evidence = evidence == null ? List.of() : evidence;
        }
    }

    public record ExperienceRow(List<Experience> values, boolean valuesDiffer) {

        public ExperienceRow {
            values = values == null ? List.of() : values;
        }
    }

    public record Experience(Integer months, BigDecimal years) {   // ca hai null = "Chua co du lieu" (R-D5)
    }

    public record ProfileCell(boolean parsedDataAvailable, List<String> skills, List<EducationItem> education) {

        public ProfileCell {
            skills = skills == null ? List.of() : skills;
            education = education == null ? List.of() : education;
        }
    }

    // Khong diem trung binh hoc tap [Q10].
    public record EducationItem(String degree, String major, String school, String startDate, String endDate) {
    }

    // Khong hai danh sach tieu chi dat/thieu (R-W4). status chi khac null khi luot DONE chua co giai thich (giu quy uoc
    // ApplicationHrListItemResponse.ExplanationStatus).
    public record ExplanationCell(
            String summary,
            List<ExplanationPoint> strengths,
            List<ExplanationPoint> weaknesses,
            ApplicationHrListItemResponse.ExplanationStatus status) {

        public ExplanationCell {
            strengths = strengths == null ? List.of() : strengths;
            weaknesses = weaknesses == null ? List.of() : weaknesses;
        }
    }

    public record Screening(
            List<ScreeningCaptureState> states,      // theo thu tu columns
            List<Instant> capturedAt,                // theo thu tu columns; phan tu null khi NOT_CAPTURED
            List<ScreeningRow> rows) {

        public Screening {
            states = states == null ? List.of() : states;
            capturedAt = capturedAt == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(capturedAt));
            rows = rows == null ? List.of() : rows;
        }
    }

    public record ScreeningRow(
            ScreeningQuestionType questionType,
            String questionText,
            List<String> options,
            boolean valuesDiffer,                    // chi YES_NO/SINGLE_CHOICE; SHORT_TEXT luon false
            List<ScreeningCell> cells) {

        public ScreeningRow {
            options = options == null ? List.of() : options;
            cells = cells == null ? List.of() : cells;
        }
    }

    public record ScreeningCell(ScreeningCellState state, String answerText, Boolean answerYesNo) {
    }

    public enum ScreeningCellState { ANSWERED, NOT_ANSWERED, NOT_ASKED, NOT_CAPTURED, NO_QUESTIONS }
}
