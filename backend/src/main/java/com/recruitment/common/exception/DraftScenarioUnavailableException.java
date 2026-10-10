package com.recruitment.common.exception;

// FR-C07 R-S3 - tinh huong chua dung duoc voi trang thai hien tai cua don (vd "Thong bao ket qua" khi don chua co ket
// qua, "Nhac lich phong van" khi chua co giay moi). 409, cung ly do voi ConversationReadOnlyException: request hop le
// nhung don khong o trang thai cho phep. Khong goi AI.
public class DraftScenarioUnavailableException extends RuntimeException {

    public DraftScenarioUnavailableException() {
        super("Tình huống này chưa dùng được với trạng thái hiện tại của đơn.");
    }
}
