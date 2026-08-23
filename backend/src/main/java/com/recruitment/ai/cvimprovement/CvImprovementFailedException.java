package com.recruitment.ai.cvimprovement;

// Message la formatted() cua errorCode (chuoi tieng Viet co dinh) - KHONG bao gio la message goc
// cua exception that bai (Jackson parse error, loi mang...) hay noi dung output LLM. cause() (khi
// co) van giu exception goc de log.debug o noi bat, nhung khong bao gio duoc doc ra ngoai qua
// getMessage(). Mau y het ScoreExplanationFailedException (ai/explanation).
public class CvImprovementFailedException extends RuntimeException {

    private final CvImprovementErrorCode errorCode;

    public CvImprovementFailedException(CvImprovementErrorCode errorCode) {
        super(errorCode.formatted());
        this.errorCode = errorCode;
    }

    public CvImprovementFailedException(CvImprovementErrorCode errorCode, Throwable cause) {
        super(errorCode.formatted(), cause);
        this.errorCode = errorCode;
    }

    public CvImprovementErrorCode errorCode() {
        return errorCode;
    }
}
