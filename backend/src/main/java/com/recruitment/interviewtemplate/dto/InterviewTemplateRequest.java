package com.recruitment.interviewtemplate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// KHONG co truong ngay gio o day - "com trong" trong FR-H02 nghia la khong ton tai o tang
// template, khong phai mot truong nullable cho HR bo qua. Xem InterviewTemplate.java.
public record InterviewTemplateRequest(
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String body,
        @NotBlank @Size(max = 150) String senderName,
        @Size(max = 150) String senderTitle,
        String address) {
}
