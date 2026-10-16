package com.recruitment.common.exception;

// FR-H14 R-S8 - danh sach don cua bang so sanh khong hop le (400 INVALID_COMPARE_SELECTION). Cung khuon
// InvalidJobFilterException: mot class, moi loai loi mot factory method, thong diep tieng Viet CO DINH - khong nhan
// chuoi tu do, khong chua ten ung vien (de FR-H16 khong phai che cau loi).
public class InvalidCompareSelectionException extends RuntimeException {

    private InvalidCompareSelectionException(String message) {
        super(message);
    }

    // R-S2: thieu ids, phan tu rong, khong phai UUID.
    public static InvalidCompareSelectionException malformed() {
        return new InvalidCompareSelectionException("Danh sách đơn cần so sánh không hợp lệ.");
    }

    // R-S1: id trung (so sau khi parse UUID).
    public static InvalidCompareSelectionException duplicate() {
        return new InvalidCompareSelectionException("Mỗi đơn chỉ được chọn một lần.");
    }

    // R-S1: so id ngoai {2, 3}.
    public static InvalidCompareSelectionException wrongCount() {
        return new InvalidCompareSelectionException("Chọn từ 2 đến 3 đơn để so sánh.");
    }

    // R-S3: don khong ton tai, thuoc Job khac, hay cong ty khac - CUNG mot cau (khong lo don co ton tai).
    public static InvalidCompareSelectionException notInJob() {
        return new InvalidCompareSelectionException("Có đơn không thuộc tin tuyển dụng này.");
    }

    // R-S4: khac recruitment_cycle.
    public static InvalidCompareSelectionException differentCycles() {
        return new InvalidCompareSelectionException("Chỉ so sánh được các đơn cùng một chu kỳ tuyển dụng.");
    }
}
