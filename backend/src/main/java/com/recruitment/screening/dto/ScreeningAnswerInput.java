package com.recruitment.screening.dto;

import java.util.UUID;

// FR-U09 - mot cau tra loi ung vien gui kem don. KHONG @NotNull/@Size: kiem o ScreeningCaptureService sau khi bo khoang
// trang hai dau (R-V, tien le R-Q4b cua FR-H10).
public record ScreeningAnswerInput(UUID questionId, String answerText, Boolean answerYesNo) {
}
