package com.recruitment.common.exception;

// FR-H10 R-A2 - cau hoi khong ton tai, khong thuoc Job tren duong dan, hoac da xoa mem. Cung mot cau cho ca ba
// truong hop (UI.md muc 7), khong noi id vao message.
public class ScreeningQuestionNotFoundException extends RuntimeException {

    public ScreeningQuestionNotFoundException() {
        super("Không tìm thấy câu hỏi sàng lọc.");
    }
}
