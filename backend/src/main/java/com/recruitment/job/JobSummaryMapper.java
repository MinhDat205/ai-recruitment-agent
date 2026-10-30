package com.recruitment.job;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.company.Company;
import com.recruitment.job.dto.CompanyRef;
import com.recruitment.job.dto.JobSummaryResponse;

// FR-U16 L1, L2 - ham dung JobSummaryResponse DUY NHAT (than chuyen nguyen tu JobPublicService.toSummary/toRef), de
// trang "Tin da luu" dung lai cho ca tin khong con mo. Static theo khuon JobCatalogFields.of: constructor
// JobPublicService giu nguyen. Ket qua khong doi cho moi noi goi cu.
public final class JobSummaryMapper {

    private JobSummaryMapper() {}

    public static JobSummaryResponse toSummary(Job j, Company c, CatalogRegistry catalogRegistry) {
        JobCatalogFields catalog = JobCatalogFields.of(j, catalogRegistry);
        return new JobSummaryResponse(
                j.getId(),
                j.getTitle(),
                catalog.categoryCode(),
                catalog.categoryLabel(),
                catalog.locationCode(),
                catalog.locationLabel(),
                catalog.legacyCategory(),
                catalog.legacyLocation(),
                j.getEmploymentType(),
                j.getWorkMode(),
                j.getSalaryMin(),
                j.getSalaryMax(),
                j.getSalaryCurrency(),
                j.getDeadline(),
                j.getPublishedAt(),
                toCompanyRef(c));
    }

    public static CompanyRef toCompanyRef(Company c) {
        return c == null ? null : new CompanyRef(c.getId(), c.getName(), c.logoUrlWithCacheBust());
    }
}
