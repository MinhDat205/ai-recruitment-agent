package com.recruitment.jobsearch;

import com.recruitment.ai.citation.VerbatimQuoteVerifier;
import com.recruitment.ai.jobsearch.JobSearchParsePayload;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.CodeCondition;
import com.recruitment.ai.jobsearch.JobSearchParsePayload.SalaryCondition;
import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.job.WorkModes;
import com.recruitment.resume.ResumeParsingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

// FR-U13 R-C - chap nhan dieu kien AI tach ra, XAC DINH, khong AI (Q-T4). Ap tren payload DA qua R-V va DA doi nguoc
// ‹ ›. Moi dieu kien co ket cuc NHAN hoac BO; doan chu cua dieu kien bi bo o lai phan mo ta (R-D1). Chi bon loai dieu
// kien - thong tin nhan than khong bieu dien duoc bang bat ky loai nao (R-C5).
@Component
public class JobSearchConditionResolver {

    // R-C1/R-C2 - khop MAX_CODES cua U07.
    static final int MAX_CODES = 3;
    // R-C4 - trieu VND; tran 1 000 chan truong hop AI tra theo don vi dong.
    static final int MIN_SALARY_MILLION = 0;
    static final int MAX_SALARY_MILLION = 1000;

    private final CatalogRegistry catalogRegistry;

    public JobSearchConditionResolver(CatalogRegistry catalogRegistry) {
        this.catalogRegistry = catalogRegistry;
    }

    // L15 - kieu trung gian (khong thanh file rieng): dieu kien da nhan + doan chu cho R-D1. acceptedSourceTexts/
    // droppedSourceTexts la doan da normalizeWhitespace (K2), giu thu tu xuat hien dau.
    public record Resolution(
            List<String> locationCodes,
            List<String> industryCodes,
            Integer salaryMin,
            Integer salaryMax,
            List<String> workModes,
            Set<String> acceptedSourceTexts,
            Set<String> droppedSourceTexts,
            List<String> personalInfoTexts) {

        public Resolution {
            locationCodes = List.copyOf(locationCodes);
            industryCodes = List.copyOf(industryCodes);
            workModes = List.copyOf(workModes);
            // Set.copyOf lam mat thu tu -> ban bat bien giu thu tu (R-D2 xep theo do dai, bang nhau giu thu tu nay).
            acceptedSourceTexts = Collections.unmodifiableSet(new LinkedHashSet<>(acceptedSourceTexts));
            droppedSourceTexts = Collections.unmodifiableSet(new LinkedHashSet<>(droppedSourceTexts));
            personalInfoTexts = List.copyOf(personalInfoTexts);
        }

        public boolean hasSalary() {
            return salaryMin != null || salaryMax != null;
        }
    }

    public Resolution resolve(JobSearchParsePayload payload) {
        Set<String> accepted = new LinkedHashSet<>();
        Set<String> dropped = new LinkedHashSet<>();

        List<String> locationCodes = acceptCodes(payload.locations(), catalogRegistry::isProvince, MAX_CODES,
                accepted, dropped);
        List<String> industryCodes = acceptCodes(payload.industries(), this::isAcceptedIndustry, MAX_CODES,
                accepted, dropped);
        List<String> workModes = acceptCodes(payload.workModes(), JobSearchConditionResolver::isWorkMode,
                Integer.MAX_VALUE, accepted, dropped);

        Integer salaryMin = null;
        Integer salaryMax = null;
        SalaryCondition salary = payload.salary();
        if (salary != null) {
            if (isAcceptedSalary(salary.minMillionVnd(), salary.maxMillionVnd())) {
                salaryMin = salary.minMillionVnd();
                salaryMax = salary.maxMillionVnd();
                accepted.add(normalized(salary.sourceText()));
            } else {
                dropped.add(normalized(salary.sourceText()));
            }
        }

        return new Resolution(locationCodes, industryCodes, salaryMin, salaryMax, workModes, accepted, dropped,
                payload.personalInfoTexts());
    }

    // Khu trung giu thu tu xuat hien dau. Ma trung voi ma da nhan -> NHAN (cung dieu kien); ma hop le vuot limit -> BO.
    private static List<String> acceptCodes(
            List<CodeCondition> conditions,
            Predicate<String> isValidCode,
            int limit,
            Set<String> accepted,
            Set<String> dropped) {
        List<String> codes = new ArrayList<>();
        for (CodeCondition condition : conditions) {
            String code = condition.code();
            String text = normalized(condition.sourceText());
            if (!isValidCode.test(code)) {
                dropped.add(text);
            } else if (codes.contains(code)) {
                accepted.add(text);
            } else if (codes.size() < limit) {
                codes.add(code);
                accepted.add(text);
            } else {
                dropped.add(text);
            }
        }
        return codes;
    }

    // R-C2 - co trong danh muc va khac OTHER.
    private boolean isAcceptedIndustry(String code) {
        return catalogRegistry.isIndustry(code) && !ResumeParsingService.EXCLUDED_INDUSTRY_CODE.equals(code);
    }

    // R-C3 - WorkModes.CODES la Set.of: contains(null) nem NPE -> chan null truoc.
    private static boolean isWorkMode(String code) {
        return code != null && WorkModes.CODES.contains(code);
    }

    // R-C4 - moi moc khac null trong [0, 1000]; co ca hai thi min <= max; khong moc nao -> bo.
    static boolean isAcceptedSalary(Integer min, Integer max) {
        if (min == null && max == null) {
            return false;
        }
        if (!inRange(min) || !inRange(max)) {
            return false;
        }
        return min == null || max == null || min <= max;
    }

    private static boolean inRange(Integer value) {
        return value == null || (value >= MIN_SALARY_MILLION && value <= MAX_SALARY_MILLION);
    }

    private static String normalized(String text) {
        return VerbatimQuoteVerifier.normalizeWhitespace(text);
    }
}
