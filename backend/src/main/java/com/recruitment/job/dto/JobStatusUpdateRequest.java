package com.recruitment.job.dto;

import com.recruitment.job.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(@NotNull JobStatus status) {
}
