package com.recruitment.job;

import com.recruitment.catalog.CatalogRegistry;

// Sau truong danh muc cua response Job (FR-C05 R-J2, R-J7), tinh o MOT cho cho ca JobOwnerService
// (HR) lan JobPublicService (cong khai, F1). legacy* chi khac null khi Job "chua chuan hoa": khong co
// ma nhung con gia tri cu. Co ma thi legacy* = null du cot cu van con gia tri (cot cu giu nguyen o DB).
// public (FR-U08 R-C6): chi tiet don phia ung vien (package jobapplication) dung CHINH cach tinh nay, khong
// viet bo chuyen ma -> nhan thu hai. Khong doi logic.
public record JobCatalogFields(
        String categoryCode,
        String categoryLabel,
        String locationCode,
        String locationLabel,
        String legacyCategory,
        String legacyLocation) {

    public static JobCatalogFields of(Job job, CatalogRegistry catalog) {
        String categoryCode = job.getCategoryCode();
        String locationCode = job.getLocationCode();
        return new JobCatalogFields(
                categoryCode,
                catalog.industryLabel(categoryCode),
                locationCode,
                catalog.provinceLabel(locationCode),
                categoryCode == null ? job.getCategory() : null,
                locationCode == null ? job.getLocation() : null);
    }

    // R-J9: van ban nganh nghe dua vao embedding - nhan cua ma, khong co ma thi gia tri cu.
    static String categoryText(Job job, CatalogRegistry catalog) {
        String label = catalog.industryLabel(job.getCategoryCode());
        return label != null ? label : job.getCategory();
    }
}
