package com.recruitment.resume;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.catalog.dto.CatalogResponse;
import com.recruitment.resume.dto.ResumeParsedDataResponse;
import org.springframework.stereotype.Component;

// FR-H09 Q2 - cach map ResumeParsedData -> ResumeParsedDataResponse DUNG CHUNG cho ca phia ung vien
// (ResumeService.getParsedData) lan phia HR (ResumeHrService.getParsedDataForApplication, E2). Tach
// nguyen van tu ResumeService (FR-C05), khong doi hanh vi, khong them/bot field cua DTO - ca hai phia
// nhan cung mot response cho cung mot CV (T5).
@Component
public class ResumeParsedDataResponseMapper {

    private final CatalogRegistry catalogRegistry;

    public ResumeParsedDataResponseMapper(CatalogRegistry catalogRegistry) {
        this.catalogRegistry = catalogRegistry;
    }

    public ResumeParsedDataResponse toResponse(ResumeParsedData data) {
        ResumeParsedPayload payload = data.getData();
        return new ResumeParsedDataResponse(
                data.getResumeId(),
                payload,
                data.getParsedAt(),
                ResumeSchemaVersions.of(data.getPromptVersion()),
                payload.currentTitle(),
                catalogItem(data.getIndustryCode(), catalogRegistry.industryLabel(data.getIndustryCode())),
                catalogItem(data.getRegionCode(), catalogRegistry.provinceLabel(data.getRegionCode())),
                payload.locationText(),
                toExperience(data));
    }

    private static CatalogResponse.Item catalogItem(String code, String label) {
        return code == null || label == null ? null : new CatalogResponse.Item(code, label);
    }

    // null khi chua tinh (job nen chua toi). years do backend quy doi (R-E8).
    private static ResumeParsedDataResponse.Experience toExperience(ResumeParsedData data) {
        if (data.getExperienceComputedAt() == null) {
            return null;
        }
        Integer months = data.getExperienceMonths();
        return new ResumeParsedDataResponse.Experience(
                months,
                months == null ? null : ExperienceCalculator.toYears(months),
                data.getExperienceEntriesCounted(),
                data.getExperienceEntriesSkipped(),
                ExperienceCalculator.referenceMonth(data.getExperienceComputedAt()).toString());
    }
}
