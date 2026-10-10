package com.recruitment.common.exception;

// FR-C07 R-K3-6 - K3 that bai. getMessage() la formatted() cua errorCode (chuoi co dinh) - KHONG bao gio la message
// goc cua exception gay loi hay output LLM. cause() giu exception goc chi de log.debug, khong doc ra ngoai.
// Mau CvImprovementFailedException (ai/cvimprovement).
public class AiSyncFailedException extends RuntimeException {

    private final AiSyncErrorCode errorCode;

    public AiSyncFailedException(AiSyncErrorCode errorCode, Throwable cause) {
        super(errorCode.formatted(), cause);
        this.errorCode = errorCode;
    }

    public AiSyncErrorCode errorCode() {
        return errorCode;
    }
}
