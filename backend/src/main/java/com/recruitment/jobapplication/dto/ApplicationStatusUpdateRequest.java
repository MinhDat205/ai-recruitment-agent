package com.recruitment.jobapplication.dto;

import com.recruitment.jobapplication.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusUpdateRequest(@NotNull ApplicationStatus status) {
}
