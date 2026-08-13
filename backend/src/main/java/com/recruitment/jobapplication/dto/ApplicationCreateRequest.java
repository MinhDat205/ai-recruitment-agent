package com.recruitment.jobapplication.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// recruitmentCycle KHONG co o day - backend tu lay tu Job hien tai, khong tin client (FR-U02).
public record ApplicationCreateRequest(
        @NotNull UUID jobId,
        @NotNull UUID resumeId,
        @AssertTrue(message = "Bạn phải đồng ý cho phép AI phân tích CV để ứng tuyển") boolean aiConsent,
        String coverLetter) {
}
