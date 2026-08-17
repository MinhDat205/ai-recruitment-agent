package com.recruitment.scoring;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Kiem toan ven TRUOC khi cong (Q3, ke hoach D3): so TAP ten tieu chi da cham
// (criterion_scores.criterion_name_snapshot) voi TAP ten tieu chi trong rubric_snapshot cua CHINH
// luot cham do - KHONG chi dem so luong. Dem don thuan se bo lot truong hop "dung so luong nhung
// SAI tieu chi" (vd mot dong bi ghi nham ten trong khi mot ten khac lai thieu, tuy hien tai khong
// co duong code nao tao ra duoc tinh huong nay) - so tap bat duoc CA hai loai loi (thieu/thua) LAN
// loai loi nay.
//
// Kiem THEM ca kich thuoc danh sach GOC (scoredCriterionNames.size(), truoc khi dua vao Set) chu
// khong chi so sanh hai Set voi nhau: mot danh sach co ten LAP (vd "A","A","B") van cho ra Set
// giong het danh sach khong lap ("A","B") - chi so sanh Set se am tham "nuot" ban trung roi bao
// qua. Ve ly thuyet khong xay ra (uq_score_per_criterion UNIQUE(scoring_run_id,
// criterion_name_snapshot) chan trung ten trong CUNG mot luot cham o tang DB), day la luoi an toan
// them cho tang goi (Dot 3) neu lo ghep nham du lieu.
//
// Set (khong phai dem theo tung ten/multiset) du an toan cho phia con lai vi CA HAI deu duoc DB/app
// dam bao khong trung ten trong pham vi lien quan: rubric_snapshot khong co ten tieu chi trung
// (fix/rubric-guard chan trung ten luc tao rubric, map 409).
//
// Ve ly thuyet khong bao gio mismatch: D2 chi INSERT criterion_scores voi ten lay THANG tu chinh
// rubric_snapshot cua luot do (ScoringRunOrchestrator.doProcess lap qua
// run.getRubricSnapshot().criteria()), va mot tieu chi loi la CA luot loi - khong bao gio chap
// nhan cham-duoc-bao-nhieu-ghi-bay-nhieu (xem docs/walkthrough/fr-h04-scoring.md muc 4e). Mot luot
// toi duoc D3 (status RUNNING, finished_at khac NULL) phai luon co du va dung tap ten. Day la luoi
// an toan phong khi gia dinh do sai (vd loi phat sinh sau nay), khong phai duong di binh thuong.
public final class AggregationIntegrityChecker {

    private AggregationIntegrityChecker() {
    }

    public static void requireMatchesSnapshot(RubricSnapshot rubricSnapshot, List<String> scoredCriterionNames) {
        Set<String> expectedNames = rubricSnapshot.criteria().stream()
                .map(RubricSnapshot.CriterionSnapshot::name)
                .collect(Collectors.toSet());
        Set<String> actualNames = Set.copyOf(scoredCriterionNames);
        boolean sizesMatch = scoredCriterionNames.size() == expectedNames.size();
        if (!sizesMatch || !actualNames.equals(expectedNames)) {
            throw new ScoreAggregationException(ScoreAggregationErrorCode.CRITERIA_MISMATCH);
        }
    }
}
