package com.recruitment.talentpool;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.common.exception.ApplicationNotFoundException;
import com.recruitment.common.exception.IdentityMaskedException;
import com.recruitment.common.exception.InvalidTalentPoolSelectionException;
import com.recruitment.common.exception.TalentPoolConsentRequiredException;
import com.recruitment.common.exception.TalentPoolNotFoundException;
import com.recruitment.jobapplication.ConsentLockRow;
import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.jobapplication.HrApplicationAccess.OwnedApplication;
import com.recruitment.jobapplication.JobApplicationRepository;
import com.recruitment.jobapplication.TalentPoolConsentState;
import com.recruitment.talentpool.dto.ApplicationTalentPoolsRequest;
import com.recruitment.talentpool.dto.ApplicationTalentPoolsResponse;
import com.recruitment.talentpool.dto.ApplicationTalentPoolsResponse.PoolMembership;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H15 P6, P7 - kho cua MOT don. Quyen don qua HrApplicationAccess.loadOwned (cong ty 404 -> don 404 -> don cong ty
// khac 403, REQUIREMENT a9). Khong goi AI (R-A1).
@Service
public class ApplicationTalentPoolService {

    private final HrApplicationAccess hrApplicationAccess;
    private final JobApplicationRepository jobApplicationRepository;
    private final TalentPoolRepository talentPoolRepository;
    private final TalentPoolEntryRepository talentPoolEntryRepository;
    private final BlindReviewResolver blindReviewResolver;

    public ApplicationTalentPoolService(
            HrApplicationAccess hrApplicationAccess,
            JobApplicationRepository jobApplicationRepository,
            TalentPoolRepository talentPoolRepository,
            TalentPoolEntryRepository talentPoolEntryRepository,
            BlindReviewResolver blindReviewResolver) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.jobApplicationRepository = jobApplicationRepository;
        this.talentPoolRepository = talentPoolRepository;
        this.talentPoolEntryRepository = talentPoolEntryRepository;
        this.blindReviewResolver = blindReviewResolver;
    }

    // P6 - consentState suy tu entity (doc, khong khoa); contains loc dong y (R-M6).
    @Transactional(readOnly = true)
    public ApplicationTalentPoolsResponse get(UUID ownerId, UUID applicationId) {
        OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId);
        return response(
                TalentPoolConsentState.of(owned.application()), owned.job().getCompanyId(), applicationId);
    }

    // P7 - R-P2 dung thu tu 7 buoc, dung o loi dau tien; moi loi la RuntimeException -> rollback toan bo (R-P3), va moi
    // kiem tra deu chay TRUOC cau ghi dau tien.
    @Transactional
    public ApplicationTalentPoolsResponse replace(UUID ownerId, UUID applicationId, ApplicationTalentPoolsRequest request) {
        // Buoc 1-2: poolIds null / phan tu null (body hong da bi TalentPoolExceptionAdvice chan truoc khi toi day), id trung.
        Set<UUID> requested = requestedPoolIds(request);

        // Buoc 3-4.
        OwnedApplication owned = hrApplicationAccess.loadOwned(ownerId, applicationId);
        UUID companyId = owned.job().getCompanyId();

        // R-C2: khoa hang don (FOR SHARE) TRUOC khi doc dong y, roi khoa cac kho. Dong y doc tu CHINH cau khoa - khong dung
        // owned.application() (nap truoc khoa, co the cu).
        ConsentLockRow consent = jobApplicationRepository
                .lockConsentForShare(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
        checkPools(requested, companyId);

        Set<UUID> current = new HashSet<>(talentPoolEntryRepository.findActivePoolIdsForWrite(applicationId, companyId));
        Set<UUID> toAdd = new LinkedHashSet<>(requested);
        toAdd.removeAll(current);
        Set<UUID> toRemove = new HashSet<>(current);
        toRemove.removeAll(requested);

        // FR-H16 R-K2 - buoc 6b (sau kiem kho, truoc kiem dong y): THEM vao kho khi don dang an danh -> 409; go van duoc.
        // README: "Luu vao kho chi mo sau khi bo an danh" - chot o backend, khong dua vao nut da khoa o UI.
        if (!toAdd.isEmpty() && blindReviewResolver.isMasked(applicationId)) {
            throw new IdentityMaskedException();
        }

        // Buoc 7 - chi chan THEM (R-M3); go luon duoc.
        TalentPoolConsentState state = TalentPoolConsentState.of(
                Boolean.TRUE.equals(consent.getTalentPoolConsent()), consent.getTalentPoolConsentRevokedAt());
        if (!toAdd.isEmpty() && state != TalentPoolConsentState.ACTIVE) {
            throw new TalentPoolConsentRequiredException();
        }

        if (!toRemove.isEmpty()) {
            talentPoolEntryRepository.removeFromPools(applicationId, toRemove);
        }
        for (UUID poolId : toAdd) {
            // ON CONFLICT ... DO NOTHING (L1): P7 dong thoi da them truoc thi tra 0, khong loi.
            talentPoolEntryRepository.insertIfAbsent(poolId, applicationId);
        }
        return response(state, companyId, applicationId);
    }

    private static Set<UUID> requestedPoolIds(ApplicationTalentPoolsRequest request) {
        if (request == null || request.poolIds() == null) {
            throw new InvalidTalentPoolSelectionException();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (UUID id : request.poolIds()) {
            // UUID da parse: khac hoa thuong cua cung mot id van la trung.
            if (id == null || !ids.add(id)) {
                throw new InvalidTalentPoolSelectionException();
            }
        }
        return ids;
    }

    // Buoc 5 xet tren TOAN danh sach truoc buoc 6: co mot id khong ton tai / da xoa thi 404 du id khac thuoc cong ty khac.
    private void checkPools(Set<UUID> requested, UUID companyId) {
        if (requested.isEmpty()) {
            return;
        }
        Map<UUID, PoolLockRow> locked = new HashMap<>();
        for (PoolLockRow row : talentPoolRepository.lockForShare(requested)) {
            locked.put(row.getId(), row);
        }
        for (UUID id : requested) {
            PoolLockRow row = locked.get(id);
            if (row == null || row.getDeletedAt() != null) {
                throw new TalentPoolNotFoundException();
            }
        }
        for (UUID id : requested) {
            if (!locked.get(id).getCompanyId().equals(companyId)) {
                throw new AccessDeniedException("Khong co quyen truy cap kho ung vien nay");
            }
        }
    }

    private ApplicationTalentPoolsResponse response(TalentPoolConsentState state, UUID companyId, UUID applicationId) {
        Set<UUID> containing = new HashSet<>(talentPoolEntryRepository.findReadablePoolIds(applicationId));
        List<PoolMembership> pools = talentPoolRepository.findReadableByCompanyId(companyId).stream()
                .map(row -> new PoolMembership(row.getId(), row.getName(), containing.contains(row.getId())))
                .toList();
        return new ApplicationTalentPoolsResponse(state, pools);
    }
}
