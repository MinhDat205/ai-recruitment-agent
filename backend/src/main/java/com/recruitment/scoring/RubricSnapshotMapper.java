package com.recruitment.scoring;

import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterion;
import java.util.List;

// Ham thuan: chup toan bo rubric + danh sach tieu chi tai thoi diem tao luot cham, dung luu vao
// scoring_runs.rubric_snapshot. Khong tu bia gia tri mac dinh cho scaleDescription null - giu
// nguyen null de tang goi LLM (Dot 3) tu quyet dinh dung thang mac dinh dung chung theo dung
// FR-H03, thay vi bia san o day.
public final class RubricSnapshotMapper {

    private RubricSnapshotMapper() {
    }

    public static RubricSnapshot toSnapshot(Rubric rubric, List<RubricCriterion> criteria) {
        List<RubricSnapshot.CriterionSnapshot> criterionSnapshots =
                criteria.stream().map(RubricSnapshotMapper::toCriterionSnapshot).toList();
        return new RubricSnapshot(rubric.getName(), criterionSnapshots);
    }

    private static RubricSnapshot.CriterionSnapshot toCriterionSnapshot(RubricCriterion criterion) {
        return new RubricSnapshot.CriterionSnapshot(
                criterion.getId(),
                criterion.getName(),
                criterion.getDescription(),
                criterion.getWeight(),
                criterion.getMaxScore(),
                criterion.getScaleDescription());
    }
}
