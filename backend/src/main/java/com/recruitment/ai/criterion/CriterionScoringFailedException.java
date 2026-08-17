package com.recruitment.ai.criterion;

// Message la formatted() cua errorCode (chuoi tieng Viet co dinh) - KHONG bao gio la message goc
// cua exception that bai (Jackson parse error, loi mang...) hay noi dung CV. cause() (khi co) van
// giu exception goc de log.debug o noi bat, nhung khong bao gio duoc doc ra ngoai qua getMessage().
//
// Hai constructor: co cause() danh cho loi bat duoc tu mot exception khac (JSON hong, loi mang -
// LLM_INVALID_JSON, LLM_ERROR); khong cause() danh cho loi phat hien o buoc validate SAU KHI da
// parse JSON thanh cong (score/evidence/section/quote khong hop le) - khong co exception goc nao
// de boc, chinh CriterionScoringService la noi phat hien ra loi.
public class CriterionScoringFailedException extends RuntimeException {

    private final CriterionScoringErrorCode errorCode;

    public CriterionScoringFailedException(CriterionScoringErrorCode errorCode) {
        super(errorCode.formatted());
        this.errorCode = errorCode;
    }

    public CriterionScoringFailedException(CriterionScoringErrorCode errorCode, Throwable cause) {
        super(errorCode.formatted(), cause);
        this.errorCode = errorCode;
    }

    public CriterionScoringErrorCode errorCode() {
        return errorCode;
    }
}
