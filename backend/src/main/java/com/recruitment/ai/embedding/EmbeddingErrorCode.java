package com.recruitment.ai.embedding;

// Khong implement common.FormattedErrorCode: interface do danh cho enum ghi xuong MOT COT LOI
// trong DB (xem comment trong FormattedErrorCode.java). job_embeddings va resume_parsed_data khong
// co cot loi nao ca (xem Plan Mode F1, muc A) - that bai o day chi dung de log.debug/log.warn, ban
// ghi tu dong duoc thu lai o lan poll ke tiep vi dieu kien quet (NOT EXISTS / IS NULL) van con dung,
// khong co markFailed/trang thai FAILED nao de ghi ma nay vao.
public enum EmbeddingErrorCode {
    // Dot 4 (chore/hardening) - phan loai tam thoi (mang/timeout, 429, 5xx) tach khoi API_ERROR
    // (con lai). Khong doi hanh vi hien tai: F1 khong retry, khong markFailed (khong co trang thai
    // FAILED) - loi luon tu dong thu lai o lan poll ke tiep du la loai nao. Ma nay chi giup
    // log.warn ghi dung nguyen nhan hon.
    TEMPORARY_UNAVAILABLE("API tạm thời không phản hồi được do quá tải hoặc gián đoạn kết nối"),
    API_ERROR("Có lỗi xảy ra khi gọi API sinh embedding"),
    INVALID_DIMENSION("Embedding trả về sai số chiều so với schema (1536)"),
    ZERO_VECTOR("API trả về vector suy biến (toàn số 0)");

    private final String description;

    EmbeddingErrorCode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
