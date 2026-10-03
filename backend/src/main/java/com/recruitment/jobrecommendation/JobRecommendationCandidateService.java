package com.recruitment.jobrecommendation;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.job.Job;
import com.recruitment.job.JobPublicService;
import com.recruitment.job.JobRepository;
import com.recruitment.job.dto.JobSummaryResponse;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.CandidateProfile;
import com.recruitment.user.CandidateProfileRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-U15 - viet lai hoan toan (truoc day doc bo dem job_recommendations, F1/FR-U04). Tinh TRUC
// TIEP moi lan goi, KHONG con cache, KHONG con import/goi JobRecommendationRepository/
// JobRecommendation/JobRecommendationCacheService nao (4 lop do bi xoa o dot 4). @Transactional
// (readOnly = true) - chi doc, khong ghi gi vao DB (R-S4). KHONG goi EmbeddingModel/LLM o bat ky
// buoc nao - chi doc vector da co san (resume_parsed_data.embedding/candidate_profiles.embedding).
@Service
public class JobRecommendationCandidateService {

    // FR-U15 R-V9 - giu dung gia tri thuc nghiem cua FR-U04 (JobRecommendationCacheService cu, da
    // xoa o dot 4): 2 CV x 7 job, Postgres/OpenAI text-embedding-3-small that. CHI ap dung cho
    // nhanh CV (R-V6.1) - nhanh ho so (R-V6.2) KHONG ap nguong nao.
    static final double MIN_SIMILARITY_SCORE = 0.40;

    // FR-U15 R-L1 - toi da 6 viec, LIMIT nam trong SQL (JobRepository), khong cat o tang Java.
    static final int RECOMMENDATION_LIMIT = 6;

    // Sentinel cho danh sach rong khi khong loc theo ma - Postgres khong nhan "IN ()" rong, cung
    // ky thuat voi JobPublicService.UNSET_CODE_SENTINEL/UNSET_WORK_MODE_SENTINEL.
    private static final String UNSET_CODE_SENTINEL = "__NONE__";

    // FR-U15 - nhan hinh thuc lam viec (dung cho matchedConditions, R-M2). Backend CHUA co bang
    // nhan tuong duong truoc FR-U15 (da kiem: grep toan bo backend/src/main/java khong ra ket qua
    // nao ngoai frontend) - sao y CHINH XAC 3 chuoi tu frontend/src/features/jobs/jobLabels.ts:
    // 39-43 (WORK_MODE_LABELS), KHONG bia chuoi moi. De xuat cho FR sau: rut thanh hang so chung o
    // backend (vi du package catalog/) khi co FR thu hai can dung - chua lam vi ngoai pham vi FR
    // nay (chi mot noi dung toi).
    private static final Map<String, String> WORK_MODE_LABELS =
            Map.of("ONSITE", "Tại văn phòng", "HYBRID", "Kết hợp", "REMOTE", "Từ xa");

    private static final String SALARY_MATCH_LABEL = "Lương đạt mong muốn";

    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobRepository jobRepository;
    private final JobPublicService jobPublicService;
    private final CatalogRegistry catalogRegistry;

    public JobRecommendationCandidateService(
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository,
            CandidateProfileRepository candidateProfileRepository,
            JobRepository jobRepository,
            JobPublicService jobPublicService,
            CatalogRegistry catalogRegistry) {
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.jobRepository = jobRepository;
        this.jobPublicService = jobPublicService;
        this.catalogRegistry = catalogRegistry;
    }

    // FR-U15 R-V6 - thu tu chon nguon, tinh lai TU DAU moi lan goi (khong cache):
    // 1) CV san sang (R-V1) -> nguon CV, ap nguong.
    // 2) Khac (1), ho so san sang (R-V3) -> nguon PROFILE, khong ap nguong.
    // 3) Khac (1)(2), co khai mong muon (R-V5) -> nguon DESIRES, sap theo thoi gian dang.
    // 4) Khac (1)(2)(3), CV dang cho (R-V2) hoac ho so dang cho (R-V4) -> PREPARING (CV uu tien
    //    hon ho so khi CA HAI cung dang cho - da chot).
    // 5) Con lai -> NO_DATA.
    @Transactional(readOnly = true)
    public JobRecommendationResponse getRecommendationsForCandidate(UUID candidateId) {
        Resume primaryResume = resumeRepository.findByCandidateIdAndIsPrimaryTrue(candidateId).orElse(null);
        CandidateProfile profile = candidateProfileRepository.findByUserId(candidateId).orElse(null);

        // R-V1/R-V2 - FAILED coi nhu "khong co CV" (khong bao gio tu co embedding, khac dang cho).
        boolean cvUsable = primaryResume != null && primaryResume.getParseStatus() != ParseStatus.FAILED;
        String cvVector =
                cvUsable
                        ? resumeParsedDataRepository
                                .findEmbeddingTextByResumeId(primaryResume.getId())
                                .orElse(null)
                        : null;
        boolean cvPending = cvUsable && cvVector == null;

        // R-V3/R-V4 - ho so "dang cho" la van ban dai dien khac rong nhung embedding chua co (U14
        // R-E6: luon tu thu lai vo han, khong co trang thai loi vinh vien nhu CV FAILED).
        String profileVector =
                profile == null ? null : candidateProfileRepository.findEmbeddingTextByUserId(candidateId).orElse(null);
        boolean profilePending = profileVector == null && hasRepresentativeText(profile);
        // R-V3 - "ho so san sang" PHAI co CA van ban dai dien khac rong VA embedding. Phong thu
        // chieu sau: van ban dai dien da rong (headline/skills/bio deu xoa) nhung embedding CU van
        // con (truong hop le ra khong xay ra theo R-E2/R-E3, nhung khong dua vao do) - coi nhu
        // CHUA san sang, roi xuong nhanh ke tiep, khong dung vector cu khong con khop du lieu hien
        // tai cua ho so.
        boolean profileReady = profileVector != null && hasRepresentativeText(profile);

        List<String> categoryCodes = desiredCodes(profile == null ? null : profile.getDesiredIndustryCodes());
        List<String> locationCodes = desiredCodes(profile == null ? null : profile.getDesiredLocationCodes());
        boolean hasDesires = !categoryCodes.isEmpty() || !locationCodes.isEmpty();

        if (cvVector != null) {
            return searchByVector(
                    candidateId, categoryCodes, locationCodes, cvVector, true, JobRecommendationSource.CV, profile);
        }
        if (profileReady) {
            return searchByVector(
                    candidateId,
                    categoryCodes,
                    locationCodes,
                    profileVector,
                    false,
                    JobRecommendationSource.PROFILE,
                    profile);
        }
        if (hasDesires) {
            return searchByDesiresOnly(candidateId, categoryCodes, locationCodes, profile);
        }
        if (cvPending) {
            return new JobRecommendationResponse(JobRecommendationStatus.PREPARING, JobRecommendationSource.CV, List.of());
        }
        if (profilePending) {
            return new JobRecommendationResponse(
                    JobRecommendationStatus.PREPARING, JobRecommendationSource.PROFILE, List.of());
        }
        return new JobRecommendationResponse(JobRecommendationStatus.NO_DATA, null, List.of());
    }

    // Bien the (i)/(ii) - mot phuong thuc Java dung chung, applyThreshold phan biet nhanh CV/ho so.
    private JobRecommendationResponse searchByVector(
            UUID candidateId,
            List<String> categoryCodes,
            List<String> locationCodes,
            String queryVector,
            boolean applyThreshold,
            JobRecommendationSource source,
            CandidateProfile profile) {
        boolean categoryCodesPresent = !categoryCodes.isEmpty();
        List<String> categoryCodeParams = categoryCodesPresent ? categoryCodes : List.of(UNSET_CODE_SENTINEL);
        boolean locationCodesPresent = !locationCodes.isEmpty();
        List<String> locationCodeParams = locationCodesPresent ? locationCodes : List.of(UNSET_CODE_SENTINEL);

        List<Job> rankedJobs = jobRepository.findRankedMatchesByVector(
                null,
                null,
                null,
                categoryCodesPresent,
                categoryCodeParams,
                locationCodesPresent,
                locationCodeParams,
                null,
                null,
                false,
                false,
                List.of(UNSET_CODE_SENTINEL),
                null,
                candidateId,
                queryVector,
                applyThreshold,
                MIN_SIMILARITY_SCORE,
                RECOMMENDATION_LIMIT);

        return toResponse(rankedJobs, source, profile);
    }

    // Bien the (iii) - khong vector, sap theo thoi gian dang moi nhat.
    private JobRecommendationResponse searchByDesiresOnly(
            UUID candidateId, List<String> categoryCodes, List<String> locationCodes, CandidateProfile profile) {
        boolean categoryCodesPresent = !categoryCodes.isEmpty();
        List<String> categoryCodeParams = categoryCodesPresent ? categoryCodes : List.of(UNSET_CODE_SENTINEL);
        boolean locationCodesPresent = !locationCodes.isEmpty();
        List<String> locationCodeParams = locationCodesPresent ? locationCodes : List.of(UNSET_CODE_SENTINEL);

        List<Job> rankedJobs = jobRepository.findRankedMatchesByDesiresOnly(
                null,
                null,
                null,
                categoryCodesPresent,
                categoryCodeParams,
                locationCodesPresent,
                locationCodeParams,
                null,
                null,
                false,
                false,
                List.of(UNSET_CODE_SENTINEL),
                null,
                candidateId,
                RECOMMENDATION_LIMIT);

        return toResponse(rankedJobs, JobRecommendationSource.DESIRES, profile);
    }

    // Hydrate JobSummaryResponse qua JobPublicService.getByIds (dung lai, khong tu viet converter
    // Job -> JobSummaryResponse rieng) - giu dung thu tu rankedJobs (da sap theo tung bien the).
    private JobRecommendationResponse toResponse(List<Job> rankedJobs, JobRecommendationSource source, CandidateProfile profile) {
        if (rankedJobs.isEmpty()) {
            return new JobRecommendationResponse(JobRecommendationStatus.NO_RESULT, source, List.of());
        }

        List<UUID> orderedIds = rankedJobs.stream().map(Job::getId).toList();
        Map<UUID, JobSummaryResponse> summariesById =
                jobPublicService.getByIds(orderedIds).stream()
                        .collect(Collectors.toMap(JobSummaryResponse::id, summary -> summary));

        List<JobRecommendationItemResponse> items = new ArrayList<>();
        for (Job job : rankedJobs) {
            JobSummaryResponse summary = summariesById.get(job.getId());
            if (summary == null) {
                // Phong thu chieu sau: job vua doi trang thai giua luc truy van va luc hydrate -
                // khong lien quan toi dieu kien goi y, chi bo qua, khong loi.
                continue;
            }
            items.add(new JobRecommendationItemResponse(summary, matchedConditions(job, profile)));
        }

        if (items.isEmpty()) {
            return new JobRecommendationResponse(JobRecommendationStatus.NO_RESULT, source, List.of());
        }
        return new JobRecommendationResponse(JobRecommendationStatus.READY, source, items);
    }

    // R-M2/R-M5 - thu tu co dinh Khu vuc . Nganh . Hinh thuc . Luong, chi dua vao danh sach khi
    // THUC SU khop. Luong/hinh thuc KHONG loai job (R-M4) - ham nay chi anh huong chip hien thi.
    private List<String> matchedConditions(Job job, CandidateProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<String> conditions = new ArrayList<>();

        if (job.getLocationCode() != null && contains(profile.getDesiredLocationCodes(), job.getLocationCode())) {
            String label = catalogRegistry.provinceLabel(job.getLocationCode());
            if (label != null) {
                conditions.add(label);
            }
        }
        if (job.getCategoryCode() != null && contains(profile.getDesiredIndustryCodes(), job.getCategoryCode())) {
            String label = catalogRegistry.industryLabel(job.getCategoryCode());
            if (label != null) {
                conditions.add(label);
            }
        }
        if (job.getWorkMode() != null && contains(profile.getDesiredWorkModes(), job.getWorkMode())) {
            String label = WORK_MODE_LABELS.get(job.getWorkMode());
            if (label != null) {
                conditions.add(label);
            }
        }
        if (matchesDesiredSalary(job, profile)) {
            conditions.add(SALARY_MATCH_LABEL);
        }
        return conditions;
    }

    // R-M2 - desired_salary_min (candidate_profiles) va salary_min/salary_max (jobs) CUNG don vi
    // VND (ca hai da quy doi tu trieu VND sang VND o tang service tuong ung truoc khi ghi xuong
    // DB - xem CandidateProfileService.java:101,235-237 va JobPublicService.java toVnd) - KHONG
    // can quy doi lai o day. Tin Thoa thuan (ca hai cot NULL) hoac ngoai te -> khong hien dieu
    // kien luong (khong du du lieu de so, khong phai "khong khop" - R-M2).
    private boolean matchesDesiredSalary(Job job, CandidateProfile profile) {
        BigDecimal desiredMin = profile.getDesiredSalaryMin();
        if (desiredMin == null) {
            return false;
        }
        String currency = job.getSalaryCurrency();
        String normalizedCurrency = currency == null ? "VND" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"VND".equals(normalizedCurrency)) {
            return false;
        }
        BigDecimal effective = job.getSalaryMax() != null ? job.getSalaryMax() : job.getSalaryMin();
        if (effective == null) {
            return false;
        }
        return effective.compareTo(desiredMin) >= 0;
    }

    // Dieu kien quet giong CandidateProfileRepository.findIdsNeedingEmbedding (R-E4 FR-U14): van
    // ban dai dien khac rong = headline/skills/bio co it nhat mot truong khac rong.
    private boolean hasRepresentativeText(CandidateProfile profile) {
        if (profile == null) {
            return false;
        }
        boolean hasHeadline = profile.getHeadline() != null && !profile.getHeadline().isBlank();
        boolean hasSkills = profile.getSkills() != null && profile.getSkills().length > 0;
        boolean hasBio = profile.getBio() != null && !profile.getBio().isBlank();
        return hasHeadline || hasSkills || hasBio;
    }

    private static List<String> desiredCodes(String[] codes) {
        return codes == null ? List.of() : List.of(codes);
    }

    private static boolean contains(String[] array, String value) {
        if (array == null) {
            return false;
        }
        for (String item : array) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
