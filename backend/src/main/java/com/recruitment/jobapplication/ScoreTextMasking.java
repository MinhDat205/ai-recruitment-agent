package com.recruitment.jobapplication;

import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse.CriterionScoreItem;
import com.recruitment.jobapplication.dto.ApplicationHrListItemResponse.Explanation;
import com.recruitment.scoring.EvidenceEntry;
import com.recruitment.scoring.ExplanationPoint;
import java.util.List;

// FR-H16 R-M4 - ban sao DA CHE cua ket qua cham tra cho HR khi don dang an danh: reasoning, evidence[].quote cua tieu
// chi; summary, strengths[].point, weaknesses[].point cua giai thich. Ten tieu chi, section, metCriteria/missingCriteria
// (ten tieu chi) giu nguyen - du lieu cua HR (R-M6). Chi chay o tang dung response, SAU khi da tinh diem/hang; khong
// ghi gi xuong DB (R-A2, R-A3). Dung chung cho danh sach theo Job va E3/E4.
public final class ScoreTextMasking {

    private ScoreTextMasking() {
    }

    public static List<CriterionScoreItem> criterionScores(List<CriterionScoreItem> items, IdentityMasker masker) {
        return items.stream()
                .map(item -> new CriterionScoreItem(
                        item.criterionNameSnapshot(),
                        item.score(),
                        item.maxScoreSnapshot(),
                        item.weightSnapshot(),
                        masker.mask(item.reasoning()),
                        evidence(item.evidence(), masker)))
                .toList();
    }

    public static Explanation explanation(Explanation explanation, IdentityMasker masker) {
        if (explanation == null) {
            return null;
        }
        return new Explanation(
                masker.mask(explanation.summary()),
                points(explanation.strengths(), masker),
                points(explanation.weaknesses(), masker),
                explanation.metCriteria(),
                explanation.missingCriteria());
    }

    private static List<EvidenceEntry> evidence(List<EvidenceEntry> evidence, IdentityMasker masker) {
        return evidence.stream().map(e -> new EvidenceEntry(masker.mask(e.quote()), e.section())).toList();
    }

    private static List<ExplanationPoint> points(List<ExplanationPoint> points, IdentityMasker masker) {
        if (points == null) {
            return null;
        }
        return points.stream().map(p -> new ExplanationPoint(p.criterionName(), masker.mask(p.point()))).toList();
    }
}
