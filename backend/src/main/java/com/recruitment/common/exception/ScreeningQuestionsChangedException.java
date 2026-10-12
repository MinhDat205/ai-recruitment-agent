package com.recruitment.common.exception;

// FR-U09 R-R1, R-R2 - bo cau hoi sang loc da doi so voi luc ung vien tai form (revision lech, hoac client gui revision
// null khi tin dang co cau hoi).
public class ScreeningQuestionsChangedException extends RuntimeException {

    public ScreeningQuestionsChangedException() {
        super("Nhà tuyển dụng vừa cập nhật câu hỏi sàng lọc. Vui lòng kiểm tra lại câu trả lời rồi nộp lại.");
    }
}
