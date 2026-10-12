package com.recruitment.jobapplication.dto;

import com.recruitment.screening.dto.ScreeningAnswerInput;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

// recruitmentCycle KHONG co o day - backend tu lay tu Job hien tai, khong tin client (FR-U02).
// FR-U09: ba field moi o CUOI; JSON cu (chi jobId/resumeId/aiConsent/coverLetter) van hop le (R-R1, R-K1).
// answers KHONG @Valid/@Size va KHONG List.copyOf (phan tu null phai thanh 400 INVALID_SCREENING_ANSWER, R-V1).
public record ApplicationCreateRequest(
        @NotNull UUID jobId,
        @NotNull UUID resumeId,
        @AssertTrue(message = "Bạn phải đồng ý cho phép AI phân tích CV để ứng tuyển") boolean aiConsent,
        String coverLetter,
        Integer screeningRevision, // null = client khong thay cau hoi nao (R-R1)
        List<ScreeningAnswerInput> answers, // null -> List.of()
        Boolean talentPoolConsent) { // null -> false (R-K1)

    public ApplicationCreateRequest {
        answers = answers == null ? List.of() : answers;
    }
}
