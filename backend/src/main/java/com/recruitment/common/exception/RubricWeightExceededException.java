package com.recruitment.common.exception;

import java.math.BigDecimal;

public class RubricWeightExceededException extends RuntimeException {

    public RubricWeightExceededException(BigDecimal wouldBeTotal) {
        super("Tổng trọng số các tiêu chí sẽ vượt quá 100% (thành " + wouldBeTotal
                + "%). Vui lòng giảm trọng số của tiêu chí này hoặc điều chỉnh tiêu chí khác.");
    }
}
