package com.recruitment.common.exception;

import java.math.BigDecimal;

public class RubricIncompleteException extends RuntimeException {

    // GIU NGUYEN cho JobOwnerService (FR-H02/H03, mo tin tuyen dung) - khong doi chu ky, khong
    // doi call site, de test B2/B3 hien co van xanh.
    public RubricIncompleteException(BigDecimal currentTotal) {
        super("Không thể mở tin tuyển dụng vì tổng trọng số rubric hiện là " + currentTotal
                + "%, cần đúng 100%. Vui lòng bổ sung hoặc điều chỉnh tiêu chí trước khi mở tuyển dụng.");
    }

    private RubricIncompleteException(String message) {
        super(message);
    }

    // Dung cho D2 (FR-H04) khi tao luot cham: message noi ro "cham diem" thay vi "mo tin tuyen
    // dung" - tai su dung dung mot lop/mot ma loi RUBRIC_INCOMPLETE thay vi tao lop trung nghia,
    // nhung khong the dung nguyen constructor tren vi cau chu sai ngu canh se gay hieu lam cho HR.
    public static RubricIncompleteException forScoring(BigDecimal currentTotal) {
        return new RubricIncompleteException("Không thể chấm điểm vì tổng trọng số rubric hiện là "
                + currentTotal
                + "%, cần đúng 100% và có ít nhất một tiêu chí. Vui lòng bổ sung hoặc điều chỉnh tiêu chí trước"
                + " khi chấm điểm.");
    }
}
