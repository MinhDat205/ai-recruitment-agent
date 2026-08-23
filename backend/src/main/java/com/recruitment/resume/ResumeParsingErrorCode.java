package com.recruitment.resume;

import com.recruitment.common.FormattedErrorCode;

// Ma loi chuan hoa cho resumes.parse_error - KHONG luu message tho tu PDFBox/POI/LLM vao DB, chi
// luu "MA: mo ta tieng Viet co dinh" qua formatted(). Stack trace va output tho chi vao log muc
// DEBUG (xem cac noi goi) - noi dung CV la du lieu ca nhan, khong duoc ro vao DB/log o muc thuong.
//
// implements FormattedErrorCode (Dot 4, chore/hardening) - truoc do enum nay lech chuan so voi
// CriterionScoringErrorCode/ScoringRunErrorCode (da implement tu D2). Khong co lo hong thuc te truoc
// khi sua (ResumeParsingStateService.markFailed da nhan dung kieu enum nay, khong nhan String tu
// do) - chi la dong bo interface chung, formatted() da khop chu ky san.
public enum ResumeParsingErrorCode implements FormattedErrorCode {

    EXTRACT_EMPTY("Không trích xuất được nội dung văn bản từ file CV (có thể là bản scan ảnh, hệ thống chưa hỗ trợ nhận dạng chữ trong ảnh)"),
    EXTRACT_CORRUPT("File CV bị hỏng hoặc không đúng định dạng, không thể đọc được"),
    LLM_INVALID_JSON("AI trả về dữ liệu không đúng định dạng sau khi đã thử lại"),
    // Thay the LLM_TIMEOUT cu (Dot 1, D1) - mo rong nghia dung, bao gom CA mang/timeout LAN 429/5xx
    // (khong chi rieng timeout), va co duong tao ra that (xem nhanh catch moi trong
    // ResumeParsingService, xac minh bang javap tren anthropic-java-core that). LLM_TIMEOUT cu
    // khong bao gio co duong code nao tao ra duoc - da xoa han, khong giu lai ben canh ma moi.
    LLM_TEMPORARILY_UNAVAILABLE("AI tạm thời không phản hồi được do quá tải hoặc gián đoạn kết nối, hệ thống sẽ tự thử lại"),
    // Ma CUOI khi het so lan thu tu dong (app.hardening.llm.max-attempts) - phan biet ro voi loi
    // noi dung CV (EXTRACT_EMPTY/EXTRACT_CORRUPT/LLM_INVALID_JSON). Dot 4e (nhip sau) se la noi GHI
    // ma nay that su.
    LLM_RETRY_EXHAUSTED("Đã thử lại nhiều lần do lỗi kết nối/quá tải của AI nhưng không thành công"),
    // Rieng cho stale-claim reaper (Dot 4h, nhip sau) - JVM restart giua chung lam mot ban ghi
    // PROCESSING "ket" qua app.hardening.stale-timeout-ms. Tach ma rieng voi
    // LLM_TEMPORARILY_UNAVAILABLE de log phan biet duoc nguyen nhan (ha tang restart vs loi goi
    // API that), nhung dung CHUNG ngan sach attempt_count/LLM_RETRY_EXHAUSTED - xem ROADMAP.
    STALE_CLAIM_TIMEOUT("Quá trình xử lý bị gián đoạn do hệ thống khởi động lại, đang tự động thử lại"),
    LLM_ERROR("Có lỗi xảy ra khi gọi AI để phân tích CV");

    private final String description;

    ResumeParsingErrorCode(String description) {
        this.description = description;
    }

    @Override
    public String formatted() {
        return name() + ": " + description;
    }
}
