package com.recruitment.resume.dto;

import com.recruitment.resume.ResumeParsedPayload;
import java.time.Instant;
import java.util.UUID;

// KHONG co rawText/model/promptVersion/tokenUsage: do la du lieu audit noi bo, khong can thiet
// cho ung vien xem lai du lieu da trich xuat cua chinh minh.
public record ResumeParsedDataResponse(UUID resumeId, ResumeParsedPayload data, Instant parsedAt) {
}
