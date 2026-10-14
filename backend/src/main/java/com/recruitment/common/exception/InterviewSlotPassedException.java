package com.recruitment.common.exception;

// FR-U10 R-W5 dong 4 (R-C6) - khung thuoc giay moi hieu luc nhung scheduled_at <= now -> 409. Ten lop = khung DA QUA gio,
// khong phai nhan danh gia ung vien (CLAUDE.md muc 7 chi cam cot/field mang ten do - O6, kiem tinh dung rg -w).
public class InterviewSlotPassedException extends RuntimeException {

    public InterviewSlotPassedException() {
        super("Khung giờ này đã qua. Vui lòng chọn khung giờ khác.");
    }
}
