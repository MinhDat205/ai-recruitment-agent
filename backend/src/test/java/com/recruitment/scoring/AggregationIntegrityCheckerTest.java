package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Java thuan, khong DB - AggregationIntegrityChecker chi can List<String> (ten da cham) doi chieu
// voi RubricSnapshot. Dot 3 se doc ten that tu criterion_scores (cung danh sach dung de dung
// ScoreAggregator), o day mo phong bang du lieu Java thuan.
class AggregationIntegrityCheckerTest {

    private static RubricSnapshot snapshotWithNames(String... names) {
        List<RubricSnapshot.CriterionSnapshot> criteria = List.of(names).stream()
                .map(name -> new RubricSnapshot.CriterionSnapshot(
                        UUID.randomUUID(), name, null, new BigDecimal("50.00"), 5, null))
                .toList();
        return new RubricSnapshot("Rubric Test", criteria);
    }

    @Test
    void requireMatchesSnapshot_countAndNamesMatch_doesNotThrow() {
        RubricSnapshot snapshot = snapshotWithNames("Kinh nghiem Java", "Kinh nghiem Docker");

        assertThatCode(() -> AggregationIntegrityChecker.requireMatchesSnapshot(
                        snapshot, List.of("Kinh nghiem Java", "Kinh nghiem Docker")))
                .doesNotThrowAnyException();
    }

    @Test
    void requireMatchesSnapshot_oneCriterionMissing_throwsCriteriaMismatch() {
        RubricSnapshot snapshot = snapshotWithNames("Kinh nghiem Java", "Kinh nghiem Docker");

        ScoreAggregationException exception = assertThrows(
                ScoreAggregationException.class,
                () -> AggregationIntegrityChecker.requireMatchesSnapshot(snapshot, List.of("Kinh nghiem Java")));

        assertThat(exception.errorCode()).isEqualTo(ScoreAggregationErrorCode.CRITERIA_MISMATCH);
    }

    @Test
    void requireMatchesSnapshot_extraScoredNameNotInSnapshot_throwsCriteriaMismatch() {
        RubricSnapshot snapshot = snapshotWithNames("Kinh nghiem Java", "Kinh nghiem Docker");

        assertThrows(
                ScoreAggregationException.class,
                () -> AggregationIntegrityChecker.requireMatchesSnapshot(
                        snapshot, List.of("Kinh nghiem Java", "Kinh nghiem Docker", "Tieu chi la")));
    }

    @Test
    void requireMatchesSnapshot_sameCountButWrongName_throwsCriteriaMismatch() {
        // Dung so luong (2 = 2) nhung SAI mot ten - day la truong hop chi dem SO LUONG se bo lot,
        // chinh la ly do chon so sanh TAP ten thay vi dem don thuan (xem AggregationIntegrityChecker).
        RubricSnapshot snapshot = snapshotWithNames("Kinh nghiem Java", "Kinh nghiem Docker");

        assertThrows(
                ScoreAggregationException.class,
                () -> AggregationIntegrityChecker.requireMatchesSnapshot(
                        snapshot, List.of("Kinh nghiem Java", "Ten khong khop nao ca")));
    }

    @Test
    void requireMatchesSnapshot_duplicateScoredNameWithSetStillMatching_throwsCriteriaMismatch() {
        // TAP ten (Set) co the TRUNG KHOP du danh sach GOC co mot ten bi lap (vd "A","A","B" va
        // "A","B" cho CUNG mot Set {"A","B"}) - chi so sanh Set se BO LOT truong hop nay (3 dong
        // nhung rubric chi co 2 tieu chi). Kiem THEM kich thuoc danh sach goc de bat duoc ca truong
        // hop nay - ve ly thuyet khong xay ra (uq_score_per_criterion chan trung ten trong CUNG
        // mot luot cham o tang DB) nhung la luoi an toan them cho tang goi (Dot 3).
        RubricSnapshot snapshot = snapshotWithNames("Kinh nghiem Java", "Kinh nghiem Docker");

        assertThrows(
                ScoreAggregationException.class,
                () -> AggregationIntegrityChecker.requireMatchesSnapshot(
                        snapshot, List.of("Kinh nghiem Java", "Kinh nghiem Java", "Kinh nghiem Docker")));
    }

    @Test
    void requireMatchesSnapshot_emptySnapshotAndEmptyScoredList_doesNotThrow() {
        // Truong hop ly thuyet khong xay ra trong thuc te (rubric phai co it nhat mot tieu chi moi
        // du 100% trong so, xem ScoringRunService.requireRubricComplete) nhung ham nay khong tu
        // gia dinh dieu do - hai tap rong bang nhau thi qua, khong nem loi.
        RubricSnapshot emptySnapshot = new RubricSnapshot("Rubric rong", List.of());

        assertThatCode(() -> AggregationIntegrityChecker.requireMatchesSnapshot(emptySnapshot, List.of()))
                .doesNotThrowAnyException();
    }
}
