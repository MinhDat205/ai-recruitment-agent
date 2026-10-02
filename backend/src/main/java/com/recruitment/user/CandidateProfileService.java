package com.recruitment.user;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.catalog.dto.CatalogResponse;
import com.recruitment.user.dto.CandidateProfileRequest;
import com.recruitment.user.dto.CandidateProfileResponse;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {

    private static final BigDecimal VND_PER_MILLION = BigDecimal.valueOf(1_000_000);

    private final CandidateProfileRepository candidateProfileRepository;
    private final CatalogRegistry catalogRegistry;

    public CandidateProfileService(
            CandidateProfileRepository candidateProfileRepository, CatalogRegistry catalogRegistry) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.catalogRegistry = catalogRegistry;
    }

    @Transactional
    public CandidateProfileResponse getMine(UUID userId) {
        return toResponse(loadOrCreate(userId));
    }

    // Dot 1 - chi anh xa/quy doi don vi, CHUA dedupe, CHUA validate ma danh muc, CHUA set co
    // onboarding, CHUA dung embedding (R-F, R-K, R-M, R-O, R-E deu o dot 2-3).
    @Transactional
    public CandidateProfileResponse update(UUID userId, CandidateProfileRequest request) {
        CandidateProfile profile = loadOrCreate(userId);
        profile.setHeadline(request.headline());
        profile.setLocation(request.location());
        profile.setCurrentTitle(request.currentTitle());
        profile.setYearsExperience(request.yearsExperience());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setDesiredIndustryCodes(nullToEmptyArray(request.desiredIndustryCodes()));
        profile.setDesiredLocationCodes(nullToEmptyArray(request.desiredLocationCodes()));
        profile.setDesiredWorkModes(nullToEmptyArray(request.desiredWorkModes()));
        profile.setSkills(nullToEmptyArray(request.skills()));
        profile.setDesiredSalaryMin(toVnd(request.desiredSalaryMinMillions()));
        profile.setBio(request.bio());
        return toResponse(candidateProfileRepository.save(profile));
    }

    // Ho so duoc tao san luc dang ky (xem AuthService.registerCandidate) nen ly thuyet luon co san
    // o day. Van tu tao neu thieu de GET/PUT khong bao gio tra 500 vi mot ban ghi bi thieu du lieu.
    private CandidateProfile loadOrCreate(UUID userId) {
        return candidateProfileRepository
                .findByUserId(userId)
                .orElseGet(() -> candidateProfileRepository.save(new CandidateProfile(userId)));
    }

    // R-V1 - request thieu hoac gui null cho 4 truong mang thi coi la rong, khong NPE, khong vi
    // pham NOT NULL cua cot text[]. KHONG dedupe o day (R-F2/R-K2 - dot 2). Entity dung String[]
    // (khong phai List<String>) - xem CandidateProfile.java ve ly do (SqlTypes.ARRAY voi List bi
    // Hibernate 7.4.1 hieu nham thanh jsonb, da kiem chung thuc te).
    private static String[] nullToEmptyArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(new String[0]);
    }

    // R-S1 - request/response dung don vi trieu VND, cot DB dung VND (NUMERIC(14,2), cung kieu
    // jobs.salary_min). Validate khoang [0,1000] da co o Bean Validation cua request (R-V1).
    private static BigDecimal toVnd(Integer millions) {
        return millions == null ? null : BigDecimal.valueOf(millions).multiply(VND_PER_MILLION);
    }

    private static Integer toMillions(BigDecimal vnd) {
        return vnd == null ? null : vnd.divide(VND_PER_MILLION).intValueExact();
    }

    // Tra nhan qua CatalogRegistry (FR-C05) cho dung hinh dang {code,label} da chot o muc 4 - ma
    // khong co trong danh muc (chua validate o dot 1, xem R-F1) tra label null, khong loai bo.
    private List<CatalogResponse.Item> toCatalogItems(String[] codes, boolean industry) {
        return Arrays.stream(codes)
                .map(code -> new CatalogResponse.Item(
                        code, industry ? catalogRegistry.industryLabel(code) : catalogRegistry.provinceLabel(code)))
                .toList();
    }

    private CandidateProfileResponse toResponse(CandidateProfile p) {
        return new CandidateProfileResponse(
                p.getId(),
                p.getHeadline(),
                p.getLocation(),
                p.getCurrentTitle(),
                p.getYearsExperience(),
                p.getDateOfBirth(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                toCatalogItems(p.getDesiredIndustryCodes(), true),
                toCatalogItems(p.getDesiredLocationCodes(), false),
                List.of(p.getDesiredWorkModes()),
                List.of(p.getSkills()),
                toMillions(p.getDesiredSalaryMin()),
                p.getBio(),
                p.getOnboardingCompletedAt());
    }
}
