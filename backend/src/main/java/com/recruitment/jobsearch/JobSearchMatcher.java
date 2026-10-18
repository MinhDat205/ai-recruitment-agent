package com.recruitment.jobsearch;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.job.Job;
import com.recruitment.job.WorkModes;
import com.recruitment.jobsearch.JobSearchConditionResolver.Resolution;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

// FR-U13 R-M - dieu kien khop tung viec, XAC DINH, khong AI. Thu tu co dinh Khu vuc . Nganh . Hinh thuc . Luong (khuon
// U15), chi them khi THUC SU khop dieu kien da nhan (R-C). Viec lot qua do ma NULL (tin chua chuan hoa - U07 R-N) hoac
// luong ngoai te/thoa thuan khong co dong tuong ung (R-M2). Khong %, khong diem, khong nhan "phu hop".
@Component
public class JobSearchMatcher {

    static final String SALARY_MATCH_LABEL = "Mức lương khớp";

    private static final BigDecimal VND_PER_MILLION = BigDecimal.valueOf(1_000_000);

    private final CatalogRegistry catalogRegistry;

    public JobSearchMatcher(CatalogRegistry catalogRegistry) {
        this.catalogRegistry = catalogRegistry;
    }

    public List<String> matchedConditions(Job job, Resolution conditions) {
        List<String> matched = new ArrayList<>();

        String locationCode = job.getLocationCode();
        if (locationCode != null && conditions.locationCodes().contains(locationCode)) {
            String label = catalogRegistry.provinceLabel(locationCode);
            if (label != null) {
                matched.add(label);
            }
        }
        String categoryCode = job.getCategoryCode();
        if (categoryCode != null && conditions.industryCodes().contains(categoryCode)) {
            String label = catalogRegistry.industryLabel(categoryCode);
            if (label != null) {
                matched.add(label);
            }
        }
        String workMode = job.getWorkMode();
        if (workMode != null && conditions.workModes().contains(workMode)) {
            String label = WorkModes.LABELS.get(workMode);
            if (label != null) {
                matched.add(label);
            }
        }
        if (matchesSalary(job, conditions)) {
            matched.add(SALARY_MATCH_LABEL);
        }
        return matched;
    }

    // R-M1 - co dieu kien luong VA tien te VND (chuan hoa nhu U15 matchesDesiredSalary) VA moi moc can so deu co gia tri
    // va thoa: co min -> salary_max khac NULL va >= min trieu; co max -> salary_min khac NULL va <= max trieu. Mot moc can
    // so ma NULL -> khong co dong luong (SQL van cho viec lot qua nhung khong du du lieu de noi la khop).
    static boolean matchesSalary(Job job, Resolution conditions) {
        if (!conditions.hasSalary()) {
            return false;
        }
        String currency = job.getSalaryCurrency();
        String normalizedCurrency = currency == null ? "VND" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"VND".equals(normalizedCurrency)) {
            return false;
        }
        if (conditions.salaryMin() != null) {
            BigDecimal jobMax = job.getSalaryMax();
            if (jobMax == null || jobMax.compareTo(toVnd(conditions.salaryMin())) < 0) {
                return false;
            }
        }
        if (conditions.salaryMax() != null) {
            BigDecimal jobMin = job.getSalaryMin();
            if (jobMin == null || jobMin.compareTo(toVnd(conditions.salaryMax())) > 0) {
                return false;
            }
        }
        return true;
    }

    // Cung cong thuc toVnd cua U07 (JobPublicService) - trieu VND -> VND.
    private static BigDecimal toVnd(Integer millions) {
        return BigDecimal.valueOf(millions).multiply(VND_PER_MILLION);
    }
}
