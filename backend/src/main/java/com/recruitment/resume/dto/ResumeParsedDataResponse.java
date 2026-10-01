package com.recruitment.resume.dto;

import com.recruitment.catalog.dto.CatalogResponse;
import com.recruitment.resume.ResumeParsedPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// KHONG co rawText/model/promptVersion/tokenUsage: do la du lieu audit noi bo, khong can thiet
// cho ung vien xem lai du lieu da trich xuat cua chinh minh.
// FR-C05 (REQUIREMENT muc 4) them "tong quan nghe nghiep": schemaVersion (1|2), currentTitle va
// locationText nguyen van, industry/location la {code,label} tu COT truy van (da qua kiem R-C3, khong
// doc lai ma AI tra ve), experience null khi chua tinh. Chi chu CV doc duoc endpoint nay.
public record ResumeParsedDataResponse(
        UUID resumeId,
        ResumeParsedPayload data,
        Instant parsedAt,
        Integer schemaVersion,
        String currentTitle,
        CatalogResponse.Item industry,
        CatalogResponse.Item location,
        String locationText,
        Experience experience) {

    // months null khi khong co muc nao doc duoc (years cung null); years do backend quy doi (R-E8),
    // frontend khong tu tinh. referenceMonth dang "YYYY-MM" (R-E2, thang cua thoi diem tinh, gio VN).
    public record Experience(
            Integer months, BigDecimal years, int countedEntries, int skippedEntries, String referenceMonth) {
    }
}
