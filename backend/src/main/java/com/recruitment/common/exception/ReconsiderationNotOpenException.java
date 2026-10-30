package com.recruitment.common.exception;

// FR-C09 R-D2, R-D3 - yeu cau khong con OPEN luc cau UPDATE co dieu kien chay: giu tu choi (H2) khi khong co yeu cau /
// da quyet, hoac luot thua tranh chap (I2/H2 cung luc, hai tab, bam trung).
public class ReconsiderationNotOpenException extends RuntimeException {

    public ReconsiderationNotOpenException() {
        super("Yêu cầu xem xét lại này không còn chờ xử lý. Vui lòng tải lại trang.");
    }
}
