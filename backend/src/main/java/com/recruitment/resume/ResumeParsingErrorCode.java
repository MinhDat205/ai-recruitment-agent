package com.recruitment.resume;

// Ma loi chuan hoa cho resumes.parse_error - KHONG luu message tho tu PDFBox/POI/LLM vao DB, chi
// luu "MA: mo ta tieng Viet co dinh" qua formatted(). Stack trace va output tho chi vao log muc
// DEBUG (xem cac noi goi) - noi dung CV la du lieu ca nhan, khong duoc ro vao DB/log o muc thuong.
public enum ResumeParsingErrorCode {

    EXTRACT_EMPTY("Không trích xuất được nội dung văn bản từ file CV (có thể là bản scan ảnh, hệ thống chưa hỗ trợ nhận dạng chữ trong ảnh)"),
    EXTRACT_CORRUPT("File CV bị hỏng hoặc không đúng định dạng, không thể đọc được"),
    LLM_INVALID_JSON("AI trả về dữ liệu không đúng định dạng sau khi đã thử lại"),
    LLM_TIMEOUT("Hết thời gian chờ phản hồi từ AI"),
    LLM_ERROR("Có lỗi xảy ra khi gọi AI để phân tích CV");

    private final String description;

    ResumeParsingErrorCode(String description) {
        this.description = description;
    }

    public String formatted() {
        return name() + ": " + description;
    }
}
