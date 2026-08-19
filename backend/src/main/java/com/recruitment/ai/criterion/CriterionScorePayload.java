package com.recruitment.ai.criterion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// Schema co dinh cho output LLM khi cham MOT tieu chi - dung BeanOutputConverter, khong Map tu do.
// @JsonIgnoreProperties: LLM co the them field thua ngoai y muon, khong duoc lam vo deserialize.
//
// score dung Double (wrapper), khong dung primitive: LLM co the vang mat field nay trong JSON hong,
// Jackson se gan null thay vi 0 - phan biet "thieu field" (loi, can retry) voi "gia tri that la 0"
// (CV khong co thong tin lien quan - xem Q2 trong ke hoach D2) o tang validate cua
// CriterionScoringService, khong phai o tang record nay.
//
// evidence la List<EvidenceQuote> RIENG cua package nay - KHONG tai su dung
// com.recruitment.scoring.EvidenceEntry, du hinh dang JSON giong het nhau. Package nay doc lap
// hoan toan voi tang persistence (khong import repository/entity nao) - Dot 4 (package scoring) se
// tu anh xa EvidenceQuote -> EvidenceEntry khi ghi DB, khong nguoc lai.
@JsonIgnoreProperties(ignoreUnknown = true)
public record CriterionScorePayload(Double score, String reasoning, List<EvidenceQuote> evidence) {

    public CriterionScorePayload {
        evidence = evidence == null ? List.of() : evidence;
    }

    // section PHAI la mot trong 6 gia tri co dinh cua schema CV D1 (contact/education/experience/
    // skills/certifications/projects) - kiem tra gia tri nay la viec cua
    // CriterionScoringService.validate(), khong phai record nay.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceQuote(String quote, String section) {
    }
}
