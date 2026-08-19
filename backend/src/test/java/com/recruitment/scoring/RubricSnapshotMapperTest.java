package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.rubric.Rubric;
import com.recruitment.rubric.RubricCriterion;
import com.recruitment.rubric.ScaleLevelDescription;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RubricSnapshotMapperTest {

    private Rubric rubricWithName(String name) {
        Rubric rubric = new Rubric();
        rubric.setId(UUID.randomUUID());
        rubric.setName(name);
        return rubric;
    }

    private RubricCriterion criterion(
            String name, String description, BigDecimal weight, int maxScore, List<ScaleLevelDescription> scale) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setId(UUID.randomUUID());
        criterion.setName(name);
        criterion.setDescription(description);
        criterion.setWeight(weight);
        criterion.setMaxScore(maxScore);
        criterion.setScaleDescription(scale);
        return criterion;
    }

    @Test
    void toSnapshot_fullData_mapsEveryFieldIncludingNestedScaleDescription() {
        Rubric rubric = rubricWithName("Rubric Backend Java");
        List<ScaleLevelDescription> scale = List.of(
                new ScaleLevelDescription(1, "Chua co kinh nghiem"), new ScaleLevelDescription(5, "Chuyen gia"));
        RubricCriterion criterion = criterion(
                "Kinh nghiem Docker", "Danh gia muc do thanh thao Docker", new BigDecimal("40.00"), 5, scale);

        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(rubric, List.of(criterion));

        assertThat(snapshot.name()).isEqualTo("Rubric Backend Java");
        assertThat(snapshot.criteria()).hasSize(1);
        RubricSnapshot.CriterionSnapshot criterionSnapshot = snapshot.criteria().get(0);
        assertThat(criterionSnapshot.criterionId()).isEqualTo(criterion.getId());
        assertThat(criterionSnapshot.name()).isEqualTo("Kinh nghiem Docker");
        assertThat(criterionSnapshot.description()).isEqualTo("Danh gia muc do thanh thao Docker");
        assertThat(criterionSnapshot.weight()).isEqualByComparingTo("40.00");
        assertThat(criterionSnapshot.maxScore()).isEqualTo(5);
        assertThat(criterionSnapshot.scaleDescription()).hasSize(2);
        assertThat(criterionSnapshot.scaleDescription().get(0).level()).isEqualTo(1);
        assertThat(criterionSnapshot.scaleDescription().get(0).description()).isEqualTo("Chua co kinh nghiem");
        assertThat(criterionSnapshot.scaleDescription().get(1).level()).isEqualTo(5);
    }

    @Test
    void toSnapshot_criterionWithNullScaleDescription_staysNullNotDefaulted() {
        Rubric rubric = rubricWithName("Rubric X");
        RubricCriterion criterion = criterion("Tieu chi khong co thang rieng", "Mo ta", new BigDecimal("100"), 5, null);

        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(rubric, List.of(criterion));

        // scaleDescription = null nghia la "dung thang mac dinh dung chung" (FR-H03) - tang
        // snapshot KHONG duoc tu bia mot thang mac dinh o day, viec do thuoc ve prompt o Dot 3.
        assertThat(snapshot.criteria().get(0).scaleDescription()).isNull();
    }

    @Test
    void toSnapshot_criterionWithNullDescription_staysNull() {
        Rubric rubric = rubricWithName("Rubric Y");
        RubricCriterion criterion = criterion("Tieu chi khong mo ta", null, new BigDecimal("100"), 5, null);

        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(rubric, List.of(criterion));

        assertThat(snapshot.criteria().get(0).description()).isNull();
    }

    @Test
    void toSnapshot_emptyCriteriaList_returnsEmptyListNotThrow() {
        Rubric rubric = rubricWithName("Rubric rong");

        RubricSnapshot snapshot = RubricSnapshotMapper.toSnapshot(rubric, List.of());

        assertThat(snapshot.criteria()).isNotNull().isEmpty();
    }
}
