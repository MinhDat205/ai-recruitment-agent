package com.recruitment.screening.dto;

import com.recruitment.screening.ScreeningQuestionType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// FR-H10 S2/S3. KHONG @Size/@NotBlank tren questionText/options: Bean Validation chay TRUOC khi trim, se chan nham
// " " + 300 ky tu va tra loi dang map truong thay vi INVALID_SCREENING_QUESTION - moi kiem van ban o service (R-Q4b).
// options null -> List.of(); KHONG List.copyOf vi copyOf nem NullPointerException voi phan tu null, phai de
// service tra 400 "Phuong an khong duoc de trong" (T3).
public record ScreeningQuestionRequest(
        @NotNull(message = "Vui lòng chọn loại câu hỏi") ScreeningQuestionType questionType,
        String questionText,
        List<String> options,
        Boolean required) {

    public ScreeningQuestionRequest {
        options = options == null ? List.of() : options;
    }
}
