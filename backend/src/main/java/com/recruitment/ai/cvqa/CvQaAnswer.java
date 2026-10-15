package com.recruitment.ai.cvqa;

import java.util.List;

// FR-C08 (muc 4.2) - ket qua DA KIEM cua mot luot hoi dap. quotes la trich dan da doi nguoc ‹ › (R-I3), da trim (R-O5),
// da gop trung (R-O4) - khop raw_text that. Khong chua raw_text, model hay prompt (Q3).
public record CvQaAnswer(
        CvQaOutcome outcome,
        // chi khi ANSWERED; null o hai truong hop con lai
        String answer,
        // 1-5 khi ANSWERED; rong o hai truong hop con lai
        List<String> quotes,
        // chi khi REFUSED; null o hai truong hop con lai
        CvQaRefusalReason refusalReason,
        // R-A3, O8 - chi ANSWERED/NOT_FOUND moi co the true; REFUSED luon false
        boolean cvTruncated) {

    public CvQaAnswer {
        quotes = quotes == null ? List.of() : List.copyOf(quotes);
    }

    public static CvQaAnswer answered(String answer, List<String> quotes, boolean cvTruncated) {
        return new CvQaAnswer(CvQaOutcome.ANSWERED, answer, quotes, null, cvTruncated);
    }

    public static CvQaAnswer notFound(boolean cvTruncated) {
        return new CvQaAnswer(CvQaOutcome.NOT_FOUND, null, List.of(), null, cvTruncated);
    }

    // Dung cho ca bo chan tu khoa (R-G1, khong doc CV) lan AI tu choi - REFUSED luon cvTruncated = false (R-A3).
    public static CvQaAnswer refused(CvQaRefusalReason reason) {
        return new CvQaAnswer(CvQaOutcome.REFUSED, null, List.of(), reason, false);
    }
}
