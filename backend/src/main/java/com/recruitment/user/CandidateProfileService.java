package com.recruitment.user;

import com.recruitment.catalog.CatalogRegistry;
import com.recruitment.catalog.dto.CatalogResponse;
import com.recruitment.common.exception.InvalidCatalogCodeException;
import com.recruitment.common.exception.InvalidProfileFieldException;
import com.recruitment.common.exception.PrimaryResumeNotParsedException;
import com.recruitment.resume.ParseStatus;
import com.recruitment.resume.Resume;
import com.recruitment.resume.ResumeParsedData;
import com.recruitment.resume.ResumeParsedDataRepository;
import com.recruitment.resume.ResumeParsedPayload;
import com.recruitment.resume.ResumeRepository;
import com.recruitment.user.dto.CandidateProfileRequest;
import com.recruitment.user.dto.CandidateProfileResponse;
import com.recruitment.user.dto.ResumeAutofillResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {

    private static final BigDecimal VND_PER_MILLION = BigDecimal.valueOf(1_000_000);
    private static final Set<String> VALID_WORK_MODES = Set.of("ONSITE", "HYBRID", "REMOTE");
    private static final int MAX_DESIRED_CODES = 3;
    private static final int MAX_SKILLS = 20;
    private static final int MIN_SKILL_LENGTH = 1;
    private static final int MAX_SKILL_LENGTH = 50;

    private final CandidateProfileRepository candidateProfileRepository;
    private final CatalogRegistry catalogRegistry;
    private final ResumeRepository resumeRepository;
    private final ResumeParsedDataRepository resumeParsedDataRepository;

    public CandidateProfileService(
            CandidateProfileRepository candidateProfileRepository,
            CatalogRegistry catalogRegistry,
            ResumeRepository resumeRepository,
            ResumeParsedDataRepository resumeParsedDataRepository) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.catalogRegistry = catalogRegistry;
        this.resumeRepository = resumeRepository;
        this.resumeParsedDataRepository = resumeParsedDataRepository;
    }

    @Transactional
    public CandidateProfileResponse getMine(UUID userId) {
        return toResponse(loadOrCreate(userId));
    }

    // Dot 3 - them R-E3 (so van ban dai dien cu/moi, dat embedding/model NULL khi doi). Dot 2 da lam
    // dedupe/validate ma danh muc (R-F), hinh thuc lam viec (R-M), ky nang (R-K), dat co onboarding
    // (R-O3).
    @Transactional
    public CandidateProfileResponse update(UUID userId, CandidateProfileRequest request) {
        // Validate/dedupe TRUOC khi dong vao entity - loi thi chua dong gi vao profile (du rollback
        // da dam bao an toan, lam vay van ro rang hon).
        List<String> industries = processDesiredCodes(request.desiredIndustryCodes(), true);
        List<String> locations = processDesiredCodes(request.desiredLocationCodes(), false);
        List<String> workModes = processWorkModes(request.desiredWorkModes());
        List<String> skills = processSkills(request.skills());

        CandidateProfile profile = loadOrCreate(userId);

        // R-E3 - van ban dai dien CU, doc TRUOC khi dong gia tri moi vao entity.
        String oldText = CandidateProfileEmbeddingOrchestrator.buildEmbeddingText(
                profile.getHeadline(), profile.getSkills(), profile.getBio());

        profile.setHeadline(request.headline());
        profile.setLocation(request.location());
        profile.setCurrentTitle(request.currentTitle());
        profile.setYearsExperience(request.yearsExperience());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setDesiredIndustryCodes(toArray(industries));
        profile.setDesiredLocationCodes(toArray(locations));
        profile.setDesiredWorkModes(toArray(workModes));
        profile.setSkills(toArray(skills));
        profile.setDesiredSalaryMin(toVnd(request.desiredSalaryMinMillions()));
        profile.setBio(request.bio());

        // R-O3 - dat co lan dau (dang NULL), lan sau giu nguyen gia tri cu.
        if (profile.getOnboardingCompletedAt() == null) {
            profile.setOnboardingCompletedAt(Instant.now());
        }

        CandidateProfile saved = candidateProfileRepository.save(profile);

        // R-E3 - van ban dai dien MOI, so voi CU: khac nhau moi dat embedding/model NULL (native
        // UPDATE flushAutomatically = true se tu flush thay doi entity o tren TRUOC khi chay -
        // CandidateProfileRepository.clearEmbedding). Giong nhau (vd chi doi dateOfBirth) khong dung
        // gi den embedding.
        String newText = CandidateProfileEmbeddingOrchestrator.buildEmbeddingText(
                saved.getHeadline(), saved.getSkills(), saved.getBio());
        if (!oldText.equals(newText)) {
            candidateProfileRepository.clearEmbedding(saved.getId());
        }

        return toResponse(saved);
    }

    // R-O3(b) - chi dat co, khong dung field nao khac (gom ca embedding/embedding_model - khong goi
    // CandidateProfileEmbeddingOrchestrator.buildEmbeddingText/clearEmbedding o day). Idempotent:
    // goi lai khi co da co gia tri thi khong doi gi, van 200.
    @Transactional
    public CandidateProfileResponse skipOnboarding(UUID userId) {
        CandidateProfile profile = loadOrCreate(userId);
        if (profile.getOnboardingCompletedAt() == null) {
            profile.setOnboardingCompletedAt(Instant.now());
        }
        return toResponse(candidateProfileRepository.save(profile));
    }

    // R-A1 - doc xac dinh tu CV chinh da DONE, khong goi AI. 409 khi chua co CV chinh hoac CV chinh
    // chua phan tich xong.
    @Transactional(readOnly = true)
    public ResumeAutofillResponse autofillFromResume(UUID userId) {
        Resume resume = resumeRepository.findByCandidateIdAndIsPrimaryTrue(userId).orElse(null);
        if (resume == null || resume.getParseStatus() != ParseStatus.DONE) {
            throw new PrimaryResumeNotParsedException();
        }
        ResumeParsedData parsedData = resumeParsedDataRepository
                .findByResumeId(resume.getId())
                .orElseThrow(PrimaryResumeNotParsedException::new);
        ResumeParsedPayload payload = parsedData.getData();
        return new ResumeAutofillResponse(
                payload.currentTitle(), payload.skills(), toYearsRoundedToHalf(parsedData.getExperienceMonths()));
    }

    // Ho so duoc tao san luc dang ky (xem AuthService.registerCandidate) nen ly thuyet luon co san
    // o day. Van tu tao neu thieu de GET/PUT khong bao gio tra 500 vi mot ban ghi bi thieu du lieu.
    private CandidateProfile loadOrCreate(UUID userId) {
        return candidateProfileRepository
                .findByUserId(userId)
                .orElseGet(() -> candidateProfileRepository.save(new CandidateProfile(userId)));
    }

    // R-F2/R-F3/R-F1 - dedupe GIU THU TU truoc, roi moi kiem ma hop le va dem so luong. Dedupe truoc
    // de khong tu choi nham input hop le co phan tu trung (vd 4 ma co 1 trung, sau dedupe con 3).
    private List<String> processDesiredCodes(List<String> rawCodes, boolean industry) {
        List<String> deduped = dedupePreserveOrder(nullToEmpty(rawCodes));
        for (String code : deduped) {
            boolean valid = industry ? catalogRegistry.isIndustry(code) : catalogRegistry.isProvince(code);
            if (!valid) {
                throw industry ? InvalidCatalogCodeException.industry() : InvalidCatalogCodeException.province();
            }
        }
        if (deduped.size() > MAX_DESIRED_CODES) {
            throw industry
                    ? InvalidProfileFieldException.tooManyDesiredIndustries()
                    : InvalidProfileFieldException.tooManyDesiredLocations();
        }
        return deduped;
    }

    // R-M1/R-M2 - dedupe GIU THU TU, chi nhan ONSITE/HYBRID/REMOTE. Khong gioi han so luong ngoai
    // viec chi co 3 gia tri kha di.
    private List<String> processWorkModes(List<String> rawModes) {
        List<String> deduped = dedupePreserveOrder(nullToEmpty(rawModes));
        for (String mode : deduped) {
            if (!VALID_WORK_MODES.contains(mode)) {
                throw InvalidProfileFieldException.invalidWorkMode();
            }
        }
        return deduped;
    }

    // R-K1/R-K2 - cat khoang trang hai dau, dedupe KHONG phan biet hoa/thuong (giu cach viet cua lan
    // xuat hien DAU TIEN va giu THU TU nhap), kiem do dai 1-50 tren tap da dedupe, roi kiem toi da 20.
    private List<String> processSkills(List<String> rawSkills) {
        List<String> trimmed = nullToEmpty(rawSkills).stream().map(String::trim).toList();
        List<String> deduped = dedupeSkillsCaseInsensitive(trimmed);
        for (String skill : deduped) {
            int length = skill.length();
            if (length < MIN_SKILL_LENGTH || length > MAX_SKILL_LENGTH) {
                throw InvalidProfileFieldException.invalidSkillLength();
            }
        }
        if (deduped.size() > MAX_SKILLS) {
            throw InvalidProfileFieldException.tooManySkills();
        }
        return deduped;
    }

    private static List<String> dedupeSkillsCaseInsensitive(List<String> values) {
        List<String> result = new ArrayList<>();
        Set<String> seenLower = new HashSet<>();
        for (String value : values) {
            if (seenLower.add(value.toLowerCase())) {
                result.add(value);
            }
        }
        return result;
    }

    // Dung cho ma danh muc/hinh thuc lam viec: so khop CHINH XAC (khong chuan hoa hoa/thuong - R-F2),
    // khac dedupeSkillsCaseInsensitive o tren.
    private static List<String> dedupePreserveOrder(List<String> values) {
        return values.stream().distinct().toList();
    }

    // R-V1 - request thieu hoac gui null cho 4 truong mang thi coi la rong, khong NPE.
    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String[] toArray(List<String> values) {
        return values.toArray(new String[0]);
    }

    // R-S1 - request/response dung don vi trieu VND, cot DB dung VND (NUMERIC(14,2), cung kieu
    // jobs.salary_min). Validate khoang [0,1000] da co o Bean Validation cua request (R-V1).
    private static BigDecimal toVnd(Integer millions) {
        return millions == null ? null : BigDecimal.valueOf(millions).multiply(VND_PER_MILLION);
    }

    private static Integer toMillions(BigDecimal vnd) {
        return vnd == null ? null : vnd.divide(VND_PER_MILLION).intValueExact();
    }

    // R-A1 - quy doi THANG thanh NAM, lam tron toi BUOC 0,5 (khac R-E8 cua C05 lam tron toi 1 chu so
    // thap phan - KHONG tai dung field experience.years da lam tron san, tranh lam tron hai lan sai
    // so). Vi du: 7 thang -> 7/6=1.1667 -> HALF_UP ve 1 -> *0.5 = 0.5. 9 thang -> 9/6=1.5 -> HALF_UP
    // ve 2 -> *0.5 = 1.0.
    private static BigDecimal toYearsRoundedToHalf(Integer months) {
        if (months == null) {
            return null;
        }
        return BigDecimal.valueOf(months)
                .divide(BigDecimal.valueOf(6), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(0.5));
    }

    // Tra nhan qua CatalogRegistry (FR-C05) cho dung hinh dang {code,label} da chot o muc 4 - ma
    // khong co trong danh muc (khong the xay ra sau dot 2 vi update() da chan o R-F1, nhung du lieu
    // cu/seed truoc do co the chua qua kiem) tra label null, khong loai bo.
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
