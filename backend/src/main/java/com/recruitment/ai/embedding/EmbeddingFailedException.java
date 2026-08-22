package com.recruitment.ai.embedding;

// Message la description() cua errorCode (chuoi tieng Viet co dinh) - KHONG bao gio la message goc
// cua exception that bai (loi mang, loi API...). Mau y het CvImprovementFailedException/
// ScoreExplanationFailedException.
public class EmbeddingFailedException extends RuntimeException {

    private final EmbeddingErrorCode errorCode;

    public EmbeddingFailedException(EmbeddingErrorCode errorCode) {
        super(errorCode.description());
        this.errorCode = errorCode;
    }

    public EmbeddingFailedException(EmbeddingErrorCode errorCode, Throwable cause) {
        super(errorCode.description(), cause);
        this.errorCode = errorCode;
    }

    public EmbeddingErrorCode errorCode() {
        return errorCode;
    }
}
