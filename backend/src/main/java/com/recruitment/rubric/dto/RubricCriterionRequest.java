package com.recruitment.rubric.dto;

import com.recruitment.rubric.ScaleLevelDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

// maxScore va displayOrder de null duoc: DB co DEFAULT (5 va 0), service tu ap dung fallback
// giong cach JobOwnerService xu ly salaryCurrency - Hibernate insert null tuong minh se de len DEFAULT.
public record RubricCriterionRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @Valid List<ScaleLevelDescription> scaleDescription,
        @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "100") BigDecimal weight,
        @Min(1) Integer maxScore,
        Integer displayOrder) {
}
