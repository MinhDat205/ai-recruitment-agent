package com.recruitment.job.dto;

import com.recruitment.interviewtemplate.dto.InterviewTemplateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// Tao Job va tao Mau giay moi phong van la MOT hanh dong cua HR (FR-H02: "khi dang tin, HR
// dong thoi tao Mau Giay moi Phong van gan voi Job nay") - gop chung vao mot request de khong
// co duong code nao goi JobOwnerService.create() ma thieu du lieu template.
public record JobCreateRequest(
        @Valid @NotNull JobRequest job, @Valid @NotNull InterviewTemplateRequest interviewTemplate) {
}
