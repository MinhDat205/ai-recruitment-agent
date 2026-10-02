package com.recruitment.common.exception;

// FR-U14 R-V2 - ma danh muc sai da co InvalidCatalogCodeException rieng; day la loi VE SO LUONG/GIA
// TRI cua cac truong mong muon (dedupe xong con qua 3/qua 20, ky nang sai do dai, hinh thuc lam viec
// ngoai tap). Cung khuon InvalidJobFilterException: mot class, nhieu factory method, thong diep
// tieng Viet co dinh. Dot 1 chi tao khung (chua co noi nao nem), logic nem o dot 2 (R-F3/R-K1/R-M1).
public class InvalidProfileFieldException extends RuntimeException {

    private InvalidProfileFieldException(String message) {
        super(message);
    }

    public static InvalidProfileFieldException tooManyDesiredIndustries() {
        return new InvalidProfileFieldException("Chỉ được chọn tối đa 3 ngành nghề mong muốn.");
    }

    public static InvalidProfileFieldException tooManyDesiredLocations() {
        return new InvalidProfileFieldException("Chỉ được chọn tối đa 3 khu vực mong muốn.");
    }

    public static InvalidProfileFieldException tooManySkills() {
        return new InvalidProfileFieldException("Chỉ được nhập tối đa 20 kỹ năng.");
    }

    public static InvalidProfileFieldException invalidSkillLength() {
        return new InvalidProfileFieldException("Mỗi kỹ năng phải có từ 1 đến 50 ký tự.");
    }

    public static InvalidProfileFieldException invalidWorkMode() {
        return new InvalidProfileFieldException("Hình thức làm việc không hợp lệ.");
    }
}
