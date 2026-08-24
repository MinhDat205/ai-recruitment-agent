package com.recruitment.common.exception;

import java.util.UUID;

public class RubricCriterionNotFoundException extends RuntimeException {

    public RubricCriterionNotFoundException(UUID id) {
        super("Không tìm thấy tiêu chí rubric: " + id);
    }
}
