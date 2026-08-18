package com.recruitment.ai.explanation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Schema co dinh cho output LLM khi tong hop bao cao giai thich cho CA mot luot cham (Dot 3, ke
// hoach D4) - dung BeanOutputConverter, khong Map tu do. @JsonIgnoreProperties: LLM co the them
// field thua ngoai y muon, khong duoc lam vo deserialize.
//
// CO Y KHONG co metCriteria/missingCriteria: hai truong do duoc TINH BANG JAVA THUAN o
// ScoreExplanationOrchestrator (Dot 3, theo criterion_scores.score > 0 / = 0 - dung luat D2
// "evidence rong <=> score = 0"), khong nho LLM phan doan (Q3, ke hoach D4). Khong de cho trong
// schema nay cho LLM dien vao, tranh LLM tu suy dien mot phien ban khac voi Java tinh duoc.
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreExplanationPayload(String summary, List<CriterionPoint> strengths, List<CriterionPoint> weaknesses) {

    public ScoreExplanationPayload {
        strengths = strengths == null ? List.of() : strengths;
        weaknesses = weaknesses == null ? List.of() : weaknesses;
    }

    // criterionName PHAI khop DUNG mot ten trong danh sach tieu chi da gui cho LLM (kiem o
    // ScoreExplanationService.validate(), khong phai record nay) - moi luan diem dan ve DUNG mot
    // tieu chi cu the (Q3, ke hoach D4), khong phai nhan xet chung chung.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CriterionPoint(String criterionName, String point) {
    }
}
