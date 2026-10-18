package com.recruitment.job;

import java.util.Map;
import java.util.Set;

// FR-U13 O3 - hang so hinh thuc lam viec khai MOT cho, dung chung boi U07 (JobPublicService - kiem tham so workMode),
// U15 (JobRecommendationCandidateService - nhan matchedConditions) va U13. Truoc U13 moi FR tu khai rieng; gia tri giu
// nguyen y het: CODES = JobRequest.workMode (@Pattern "ONSITE|HYBRID|REMOTE") = chk constraint work_mode cua V1;
// LABELS = 3 chuoi frontend/src/features/jobs/jobLabels.ts (WORK_MODE_LABELS). Map.of/Set.of (bat bien, get(null)/
// contains(null) nem NPE) - cung kieu hai hang so cu, hanh vi noi dung khong doi.
public final class WorkModes {

    public static final Set<String> CODES = Set.of("ONSITE", "HYBRID", "REMOTE");

    public static final Map<String, String> LABELS =
            Map.of("ONSITE", "Tại văn phòng", "HYBRID", "Kết hợp", "REMOTE", "Từ xa");

    private WorkModes() {
    }
}
