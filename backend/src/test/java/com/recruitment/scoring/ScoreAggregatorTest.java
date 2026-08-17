package com.recruitment.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

// Java thuan, KHONG Spring context / Testcontainers - dung mau RubricSnapshotMapperTest.
// ScoreAggregator khong goi LLM, khong cham DB, nen moi truong hop o day phai kiem chung duoc
// bang tay (may tinh bo tui) theo dung yeu cau cua FR-H05 (D3).
class ScoreAggregatorTest {

    private static CriterionScoreInput criterion(String score, int maxScore, String weight) {
        return new CriterionScoreInput(new BigDecimal(score), maxScore, new BigDecimal(weight));
    }

    @Test
    void aggregate_normalWeights_multipleCriteria_computesWeightedTotal() {
        // 4/5*50=40 ; 3/5*30=18 ; 2/5*20=8 ; tong=66, weightSum=100 -> total=66.000
        List<CriterionScoreInput> criteria = List.of(
                criterion("4.00", 5, "50.00"), criterion("3.00", 5, "30.00"), criterion("2.00", 5, "20.00"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("66.000");
        assertThat(result.weightSum()).isEqualByComparingTo("100.00");
    }

    @Test
    void aggregate_oneCriterionScoreZero_stillCountsItsWeightWithZeroContribution() {
        // Tieu chi diem 0 KHONG bi bo qua khoi phep cong - trong so cua no van tinh vao
        // weightSum, chi phan tu (score/max) cua no bang 0. 0/5*40=0 ; 5/5*60=60 ; tong=60,
        // weightSum=100 -> total=60.000 (khong phai 100 neu lo tinh tren "chi cac tieu chi > 0").
        List<CriterionScoreInput> criteria = List.of(criterion("0.00", 5, "40.00"), criterion("5.00", 5, "60.00"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("60.000");
        assertThat(result.weightSum()).isEqualByComparingTo("100.00");
    }

    @Test
    void aggregate_repeatingDecimalRatio_roundsHalfUpAtScale3() {
        // 1/3 = 0.3333333333... (vo han tuan hoan) - phai KHONG nem ArithmeticException
        // (MathContext cua phep chia noi bo phai xu ly duoc), va lam tron dung scale 3: chu so thu
        // 4 sau dau phay la 3 (<5) nen lam tron XUONG -> 33.333.
        List<CriterionScoreInput> criteria = List.of(criterion("1.00", 3, "100.00"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("33.333");
    }

    @Test
    void aggregate_criterionRepresentsDeletedRubricCriterion_stillComputesNormally() {
        // CriterionScoreInput CO Y KHONG co truong criterionId (xem javadoc cua record) - tinh
        // huong "tieu chi goc da bi HR xoa giua luc chup snapshot va luc cham" (criterion_id =
        // NULL o criterion_scores that, xem CriterionScore.java) khong the anh huong gi toi phep
        // tinh nay vi khong co duong nao de no lot vao duoc. Test nay dung du lieu binh thuong,
        // KHONG can bat ky xu ly dac biet nao, de chung minh dung dieu do.
        // 3/5*60=36 ; 4/5*40=32 ; tong=68, weightSum=100 -> total=68.000
        List<CriterionScoreInput> criteria = List.of(criterion("3.00", 5, "60.00"), criterion("4.00", 5, "40.00"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("68.000");
    }

    @Test
    void aggregate_handComputedThreeCriteria_matchesManualCalculation() {
        // Phep tinh tay day du, kiem duoc bang may tinh bo tui:
        // A: 2/3 * 25 = 50/3 = 16.6666666...
        // B: 3/5 * 40 = 24
        // C: 4/5 * 35 = 28
        // Sigma(weight) = 25 + 40 + 35 = 100
        // rawSum = 50/3 + 24 + 28 = 50/3 + 52 = (50 + 156)/3 = 206/3 = 68.6666666...
        // Lam tron scale 3, HALF_UP: chu so thu 4 sau dau phay la 6 (>=5) -> lam tron LEN chu so
        // thu 3 (6 -> 7) => 68.667.
        List<CriterionScoreInput> criteria = List.of(
                criterion("2.00", 3, "25.00"), criterion("3.00", 5, "40.00"), criterion("4.00", 5, "35.00"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("68.667");
        assertThat(result.weightSum()).isEqualByComparingTo("100.00");
    }

    @Test
    void aggregate_weightSumNot100_normalizesUsingActualWeightSum() {
        // Sigma(weight_snapshot) = 90 (ly thuyet khong xay ra vi da kiem du 100% luc TAO luot cham
        // - RubricSnapshot la du lieu cu, phong truong hop lech). Cong thuc TU chuan hoa bang cach
        // chia cho weightSum THAT (90), khong gia dinh no la 100:
        // 4/5*50=40 ; 3/5*40=24 ; rawSum=64 ; total = 64/90*100 = 6400/90 = 71.1111111...
        // Lam tron scale 3: chu so thu 4 la 1 (<5) -> lam tron xuong -> 71.111.
        List<CriterionScoreInput> criteria = List.of(criterion("4.00", 5, "50.00"), criterion("3.00", 5, "40.00"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("71.111");
        // weightSum tra ve dung GIA TRI THAT (90, khong phai 100) - day la gia tri tang goi (Dot
        // 3) doc de log.warn khi phat hien lech.
        assertThat(result.weightSum()).isEqualByComparingTo("90.00");
    }

    @Test
    void aggregate_weightSumZero_throwsScoreAggregationException() {
        List<CriterionScoreInput> criteria = List.of(criterion("5.00", 5, "0.00"));

        assertThatThrownBy(() -> ScoreAggregator.aggregate(criteria)).isInstanceOf(ScoreAggregationException.class);
    }

    @Test
    void aggregate_emptyCriteriaList_throwsSameExceptionAsWeightSumZero() {
        // Danh sach rong -> weightSum = 0 mot cach tu nhien (tong tren tap rong), TRUNG voi nhanh
        // loi cua case weightSum=0 o tren - khong co nhanh re rieng nao trong code cho "danh sach
        // rong", va test nay xac nhan dieu do bang cach kiem dung MOT loai exception duy nhat.
        assertThatThrownBy(() -> ScoreAggregator.aggregate(List.of())).isInstanceOf(ScoreAggregationException.class);
    }

    @Test
    void aggregate_roundOnceAtEnd_differsFromRoundingEachCriterionBeforeSumming() {
        // Test nay TON TAI de neu ai do sau nay "toi uu" bang cach lam tron tung tieu chi roi moi
        // cong (thay vi cong o do chinh xac cao roi lam tron MOT LAN o cuoi nhu ScoreAggregator
        // dang lam), no phai DO ngay - vi hai cach cho ra hai ket qua khac nhau.
        //
        // Du lieu CO Y dung nhieu chu so thap phan hon NUMERIC(5,2) thuc te (production luon la 2
        // chu so) - chi de dung ba tieu chi ma moi tieu chi co dong gop (contribution) roi DUNG
        // vao bien lam tron 0.0005, noi HALF_UP doi huong lam tron tuy thuoc lam tron SOM hay
        // MUON. max = weight cho moi tieu chi (33/33, 33/33, 34/34) de contribution = score * 1 =
        // score, tranh nhieu do phep chia khong lien quan toi diem dang kiem.
        //
        // Moi tieu chi: contribution = 10.0005 (score * (weight/max) = score vi weight=max).
        // Sigma(weight) = 33 + 33 + 34 = 100.
        //
        // Cach DUNG (cong truoc, lam tron MOT LAN o cuoi, dung ScoreAggregator dang lam):
        //   rawSum = 10.0005 * 3 = 30.0015 -> lam tron scale 3, HALF_UP: chu so thu 4 la 5 (>=5)
        //   -> lam tron LEN chu so thu 3 (1 -> 2) => 30.002.
        //
        // Cach SAI (lam tron TUNG tieu chi truoc roi moi cong, KHONG phai cach ScoreAggregator
        // dung, chi ghi lai de doi chieu): moi contribution 10.0005 lam tron scale 3 rieng le ->
        // chu so thu 4 la 5 -> lam tron LEN chu so thu 3 (0 -> 1) => 10.001 cho MOI tieu chi;
        // cong ba gia tri da lam tron: 10.001 * 3 = 30.003 - KHAC voi 30.002 o tren.
        List<CriterionScoreInput> criteria = List.of(
                criterion("10.0005", 33, "33"), criterion("10.0005", 33, "33"), criterion("10.0005", 34, "34"));

        AggregationResult result = ScoreAggregator.aggregate(criteria);

        assertThat(result.totalScore()).isEqualByComparingTo("30.002");
        assertThat(result.totalScore()).isNotEqualByComparingTo("30.003");
    }
}
