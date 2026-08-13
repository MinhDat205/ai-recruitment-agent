package com.recruitment.common.exception;

import java.math.BigDecimal;

public class RubricIncompleteException extends RuntimeException {

    public RubricIncompleteException(BigDecimal currentTotal) {
        super("Không thể mở tin tuyển dụng vì tổng trọng số rubric hiện là " + currentTotal
                + "%, cần đúng 100%. Vui lòng bổ sung hoặc điều chỉnh tiêu chí trước khi mở tuyển dụng.");
    }
}
