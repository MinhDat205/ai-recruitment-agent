package com.recruitment.ai.explanation;

// Message la formatted() cua errorCode (chuoi tieng Viet co dinh) - KHONG bao gio la message goc
// cua exception that bai (Jackson parse error, loi mang...) hay noi dung bao cao LLM. cause() (khi
// co) van giu exception goc de log.debug o noi bat, nhung khong bao gio duoc doc ra ngoai qua
// getMessage(). Mau y het CriterionScoringFailedException (ke hoach D2).
//
// Hai constructor: co cause() danh cho loi bat duoc tu mot exception khac (JSON hong, loi mang -
// LLM_INVALID_JSON, LLM_ERROR); khong cause() danh cho loi phat hien o buoc validate SAU KHI da
// parse JSON thanh cong (summary rong, criterionName khong thuoc tap da gui) - khong co exception
// goc nao de boc, chinh ScoreExplanationService la noi phat hien ra loi.
public class ScoreExplanationFailedException extends RuntimeException {

    private final ScoreExplanationErrorCode errorCode;

    public ScoreExplanationFailedException(ScoreExplanationErrorCode errorCode) {
        super(errorCode.formatted());
        this.errorCode = errorCode;
    }

    public ScoreExplanationFailedException(ScoreExplanationErrorCode errorCode, Throwable cause) {
        super(errorCode.formatted(), cause);
        this.errorCode = errorCode;
    }

    public ScoreExplanationErrorCode errorCode() {
        return errorCode;
    }
}
