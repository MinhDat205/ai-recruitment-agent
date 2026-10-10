package com.recruitment.screening.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

// FR-H10 S5 - TOAN BO id cau dang hoat dong theo thu tu moi (R-Q6).
public record ScreeningQuestionOrderRequest(
        @NotNull(message = "Vui lòng gửi danh sách câu hỏi") List<UUID> questionIds) {
}
