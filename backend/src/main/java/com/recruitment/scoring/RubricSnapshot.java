package com.recruitment.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.recruitment.rubric.ScaleLevelDescription;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Anh chup toan bo rubric tai thoi diem tao luot cham (scoring_runs.rubric_snapshot) - xem
// RubricSnapshotMapper. @JsonIgnoreProperties tren moi record de doc lai duoc ca khi schema doi
// sau nay, dung tien le ResumeParsedPayload.
//
// Dot 3 (goi LLM tung tieu chi) phai doc criteria tu chinh record nay, KHONG query lai
// rubric_criteria song - dam bao mot luot cham luon nhat quan voi chinh no du HR co sua rubric sau
// do (yeu cau bat buoc #7 cua D2).
@JsonIgnoreProperties(ignoreUnknown = true)
public record RubricSnapshot(String name, List<CriterionSnapshot> criteria) {

    public RubricSnapshot {
        criteria = criteria == null ? List.of() : criteria;
    }

    // scaleDescription CO THE null - HR khong bat buoc mo ta thang diem rieng (FR-H03), null nghia
    // la "dung thang mac dinh dung chung". KHONG tu bia thang mac dinh o day - do la viec cua
    // prompt Dot 3 khi serialize vao noi dung goi LLM, khong phai viec cua tang snapshot nay.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CriterionSnapshot(
            UUID criterionId,
            String name,
            String description,
            BigDecimal weight,
            Integer maxScore,
            List<ScaleLevelDescription> scaleDescription) {
    }
}
