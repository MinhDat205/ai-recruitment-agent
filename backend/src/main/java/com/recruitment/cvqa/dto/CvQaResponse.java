package com.recruitment.cvqa.dto;

import com.recruitment.ai.cvqa.CvQaAnswer;
import com.recruitment.ai.cvqa.CvQaOutcome;
import com.recruitment.ai.cvqa.CvQaRefusalReason;
import java.util.List;

// FR-C08 Q1, Q2 - response (muc 4.5). KHONG them field nao khac: khong van ban CV goc, khong cau hoi, khong model/prompt (T16).
public record CvQaResponse(
        // ANSWERED | NOT_FOUND | REFUSED
        CvQaOutcome outcome,
        // chi khi ANSWERED; null o hai truong hop con lai
        String answer,
        // 1-5 khi ANSWERED; [] o hai truong hop con lai
        List<Citation> citations,
        // chi khi REFUSED; null o hai truong hop con lai
        CvQaRefusalReason refusalReason,
        // R-A3: true khi CV bi cat phan giua (chi ANSWERED/NOT_FOUND); REFUSED luon false
        boolean cvTruncated) {

    public record Citation(String quote) {
    }

    public static CvQaResponse from(CvQaAnswer result) {
        List<Citation> citations = result.quotes().stream().map(Citation::new).toList();
        return new CvQaResponse(
                result.outcome(), result.answer(), citations, result.refusalReason(), result.cvTruncated());
    }
}
