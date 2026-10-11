package com.recruitment.common.exception;

import com.recruitment.common.FormattedErrorCode;

// FR-C07 R-K3-6 - ma loi chuan hoa cua K3 (goi AI dong bo). Dat o common/exception (L2), KHONG o ai/sync: handler
// HTTP nam trong GlobalExceptionHandler (common/), ma common/ khong duoc import ai/ (xem FormattedErrorCode).
// message() la cau tieng Viet co dinh tra cho nguoi dung (UI.md muc 7) - KHONG bao gio chua e.getMessage() hay
// output tho cua LLM. Anh xa sang HTTP 502/503/504 lam o handler (dot 4), khong o day.
public enum AiSyncErrorCode implements FormattedErrorCode {
    // Lan goi ChatModel het han (R-K3-1) - khong thu lai.
    AI_TIMEOUT("AI phản hồi quá lâu, vui lòng thử lại."),
    // Loi nha cung cap (tam thoi - SDK da tu thu lai ngam - hoac loi khac), hoac het luong executor (R-K3-2, R-K3-5).
    AI_UNAVAILABLE("Dịch vụ AI đang bận hoặc tạm thời gián đoạn, vui lòng thử lại sau."),
    // Output hong (JSON, validate, cham tran token) o ca 2 lan goi (R-K3-2, R-K3-9).
    AI_INVALID_OUTPUT("AI trả về kết quả không hợp lệ, vui lòng thử lại.");

    private final String message;

    AiSyncErrorCode(String message) {
        this.message = message;
    }

    // Cau hien cho nguoi dung.
    public String message() {
        return message;
    }

    @Override
    public String formatted() {
        return name() + ": " + message;
    }
}
