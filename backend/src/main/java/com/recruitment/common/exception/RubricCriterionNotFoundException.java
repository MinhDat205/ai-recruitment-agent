package com.recruitment.common.exception;

import java.util.UUID;

public class RubricCriterionNotFoundException extends RuntimeException {

    public RubricCriterionNotFoundException(UUID id) {
        super("Khong tim thay tieu chi rubric: " + id);
    }
}
