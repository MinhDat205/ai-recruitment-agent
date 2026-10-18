package com.recruitment.talentpool;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.CandidateAlias;
import com.recruitment.common.dto.PageResponse;
import com.recruitment.common.exception.CompanyNotFoundException;
import com.recruitment.common.exception.InvalidTalentPoolNameException;
import com.recruitment.common.exception.TalentPoolEntryNotFoundException;
import com.recruitment.common.exception.TalentPoolNameTakenException;
import com.recruitment.common.exception.TalentPoolNotFoundException;
import com.recruitment.company.Company;
import com.recruitment.company.CompanyRepository;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.talentpool.dto.TalentPoolEntryResponse;
import com.recruitment.talentpool.dto.TalentPoolNameRequest;
import com.recruitment.talentpool.dto.TalentPoolResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H15 P1-P5, P8, P9. Quyen theo quy uoc nhom route HR (REQUIREMENT a9): cong ty cua HR truoc (404
// COMPANY_NOT_FOUND) -> kho khong ton tai / da xoa (404 TALENT_POOL_NOT_FOUND) -> kho cua cong ty khac (403,
// AccessDeniedException -> JsonAccessDeniedHandler). Khong goi AI (R-A1).
@Service
public class TalentPoolOwnerService {

    // Quy uoc phan trang co san (JobOwnerService.java:38-39, :272-281) - REQUIREMENT a11.
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private final CompanyRepository companyRepository;
    private final TalentPoolRepository talentPoolRepository;
    private final TalentPoolEntryRepository talentPoolEntryRepository;
    private final BlindReviewResolver blindReviewResolver;

    public TalentPoolOwnerService(
            CompanyRepository companyRepository,
            TalentPoolRepository talentPoolRepository,
            TalentPoolEntryRepository talentPoolEntryRepository,
            BlindReviewResolver blindReviewResolver) {
        this.companyRepository = companyRepository;
        this.talentPoolRepository = talentPoolRepository;
        this.talentPoolEntryRepository = talentPoolEntryRepository;
        this.blindReviewResolver = blindReviewResolver;
    }

    // P1 - khong phan trang, khong gioi han so kho (R-K6, R-K7).
    @Transactional(readOnly = true)
    public List<TalentPoolResponse> list(UUID ownerId) {
        Company company = requireCompany(ownerId);
        return talentPoolRepository.findReadableByCompanyId(company.getId()).stream()
                .map(TalentPoolOwnerService::toResponse)
                .toList();
    }

    // P2 - R-K2, R-K3. existsActiveName chi de tra 409 som; chot chan that la uq_talent_pool_name_per_company (saveAndFlush
    // de vi pham noi len ngay trong request, GlobalExceptionHandler dich sang 409).
    @Transactional
    public TalentPoolResponse create(UUID ownerId, TalentPoolNameRequest request) {
        Company company = requireCompany(ownerId);
        String name = normalizeName(request);
        if (talentPoolRepository.existsActiveName(company.getId(), name, null)) {
            throw new TalentPoolNameTakenException();
        }
        TalentPool pool = new TalentPool();
        pool.setCompanyId(company.getId());
        pool.setName(name);
        TalentPool saved = talentPoolRepository.saveAndFlush(pool);
        return readable(saved.getId());
    }

    // P3
    @Transactional(readOnly = true)
    public TalentPoolResponse get(UUID ownerId, UUID poolId) {
        loadOwned(ownerId, poolId);
        return readable(poolId);
    }

    // P4 - R-K4: loai tru chinh kho (doi hoa thuong ten cua chinh minh duoc phep).
    @Transactional
    public TalentPoolResponse rename(UUID ownerId, UUID poolId, TalentPoolNameRequest request) {
        TalentPool pool = loadOwned(ownerId, poolId);
        String name = normalizeName(request);
        if (talentPoolRepository.existsActiveName(pool.getCompanyId(), name, pool.getId())) {
            throw new TalentPoolNameTakenException();
        }
        pool.setName(name);
        talentPoolRepository.saveAndFlush(pool);
        return readable(poolId);
    }

    // P5 - R-K5: xoa mem, KHONG cham muc (muc cua kho da xoa khong truy cap duoc nua).
    @Transactional
    public void delete(UUID ownerId, UUID poolId) {
        TalentPool pool = loadOwned(ownerId, poolId);
        pool.setDeletedAt(Instant.now());
        talentPoolRepository.saveAndFlush(pool);
    }

    // P8 - R-M6 (loc dong y), R-M7 (added_at DESC), phan trang theo a11.
    @Transactional(readOnly = true)
    public PageResponse<TalentPoolEntryResponse> listEntries(UUID ownerId, UUID poolId, Integer page, Integer size) {
        loadOwned(ownerId, poolId);
        Page<TalentPoolEntryRow> rows =
                talentPoolEntryRepository.findReadableByPoolId(poolId, PageRequest.of(safePage(page), safeSize(size)));
        // FR-H16 R-K1 [Q5] - nhan theo TUNG DONG (moi don xet theo Job cua no). Mot cau cho ca trang (khong N+1).
        Set<UUID> maskedIds = blindReviewResolver.maskedAmong(
                rows.getContent().stream().map(TalentPoolEntryRow::getApplicationId).toList());
        return PageResponse.from(rows, row -> toEntryResponse(row, maskedIds.contains(row.getApplicationId())));
    }

    // P9 - KHONG loc dong y (R-M5): go duoc ca muc lech. 0 dong -> 404.
    @Transactional
    public void removeEntry(UUID ownerId, UUID poolId, UUID applicationId) {
        loadOwned(ownerId, poolId);
        if (talentPoolEntryRepository.removeOne(poolId, applicationId) == 0) {
            throw new TalentPoolEntryNotFoundException();
        }
    }

    private Company requireCompany(UUID ownerId) {
        return companyRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> new CompanyNotFoundException("HR chưa tạo hồ sơ công ty"));
    }

    private TalentPool loadOwned(UUID ownerId, UUID poolId) {
        Company company = requireCompany(ownerId);
        TalentPool pool = talentPoolRepository
                .findById(poolId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(TalentPoolNotFoundException::new);
        if (!pool.getCompanyId().equals(company.getId())) {
            throw new AccessDeniedException("Khong co quyen truy cap kho ung vien nay");
        }
        return pool;
    }

    private TalentPoolResponse readable(UUID poolId) {
        return talentPoolRepository
                .findReadableById(poolId)
                .map(TalentPoolOwnerService::toResponse)
                .orElseThrow(TalentPoolNotFoundException::new);
    }

    private static String normalizeName(TalentPoolNameRequest request) {
        return TalentPoolNameRules.normalize(request == null ? null : request.name())
                .orElseThrow(InvalidTalentPoolNameException::new);
    }

    private static TalentPoolResponse toResponse(TalentPoolRow row) {
        return new TalentPoolResponse(
                row.getId(), row.getName(), row.getEntryCount() == null ? 0 : row.getEntryCount(), row.getCreatedAt());
    }

    private static TalentPoolEntryResponse toEntryResponse(TalentPoolEntryRow row, boolean identityMasked) {
        return new TalentPoolEntryResponse(
                row.getApplicationId(),
                identityMasked ? CandidateAlias.of(row.getApplicationId()) : row.getCandidateName(),
                row.getJobId(),
                row.getJobTitle(),
                Boolean.TRUE.equals(row.getJobDeleted()),
                ApplicationStatus.valueOf(row.getApplicationStatus()),
                row.getAppliedAt(),
                row.getAddedAt());
    }

    private static int safePage(Integer page) {
        return (page == null || page < 0) ? 0 : page;
    }

    private static int safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
