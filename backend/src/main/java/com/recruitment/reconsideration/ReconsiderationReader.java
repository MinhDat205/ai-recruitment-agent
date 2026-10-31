package com.recruitment.reconsideration;

import com.recruitment.blindreview.BlindReviewResolver;
import com.recruitment.blindreview.CandidateAlias;
import com.recruitment.blindreview.IdentityMaskSource;
import com.recruitment.blindreview.IdentityMasker;
import com.recruitment.reconsideration.dto.CandidateReconsiderationResponse;
import com.recruitment.reconsideration.dto.CandidateReconsiderationState;
import com.recruitment.reconsideration.dto.HrReconsiderationResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

// FR-C09 Plan Mode L4 - noi DUY NHAT dung khoi xem xet lai tu du lieu DB, de jobapplication/ (E1 ung vien, E1 FR-H09)
// goi ma khong tu viet lai logic han 168 gio. Chi doc; khong @Transactional: tham gia transaction cua noi goi.
@Component
public class ReconsiderationReader {

    private final ReconsiderationRequestRepository repository;
    private final BlindReviewResolver blindReviewResolver;
    private final IdentityMaskSource identityMaskSource;

    public ReconsiderationReader(
            ReconsiderationRequestRepository repository,
            BlindReviewResolver blindReviewResolver,
            IdentityMaskSource identityMaskSource) {
        this.repository = repository;
        this.blindReviewResolver = blindReviewResolver;
        this.identityMaskSource = identityMaskSource;
    }

    // R-C1 - khoi o E1 ung vien; null khi don khong co yeu cau va chua tung bi tu choi o vong ho so (hoac khong phai don
    // cua candidateId - noi goi da kiem quyen truoc). Phia ung vien KHONG xet an danh (R-A5).
    public CandidateReconsiderationResponse candidateBlock(UUID applicationId, UUID candidateId) {
        return repository
                .findCandidateRow(applicationId, candidateId)
                .map(ReconsiderationReader::toCandidateBlock)
                .orElse(null);
    }

    // Khoi o E1 cua FR-H09 va response H2; null khi don khong co yeu cau. Noi goi da kiem quyen HR.
    public HrReconsiderationResponse hrBlock(UUID applicationId) {
        return repository
                .findHrRow(applicationId)
                .map(row -> toHrResponses(List.of(row)).get(0))
                .orElse(null);
    }

    // H1 - statusFilter null = moi trang thai, "OPEN" = dang cho. Noi goi da kiem quyen HR tren tin.
    public List<HrReconsiderationResponse> hrList(UUID jobId, String statusFilter) {
        return toHrResponses(repository.findHrRowsByJob(jobId, statusFilter));
    }

    // R-A1, R-A2, R-A2a - an danh CHI hoi BlindReviewResolver (ban theo tap: mot cau cho co an danh + hai cau nap gia tri
    // danh tinh cho cac don dang an - khong N+1). Don dang an: nhan thay ten, reason VA hrResponse qua IdentityMasker
    // (khuon tin nhan: che moi van ban tren don an danh, ke ca van ban HR viet). Che TRUOC khi dung DTO; ban goc chi o DB.
    private List<HrReconsiderationResponse> toHrResponses(List<ReconsiderationHrRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<UUID> maskedIds = blindReviewResolver.maskedAmong(
                rows.stream().map(ReconsiderationHrRow::getApplicationId).toList());
        Map<UUID, IdentityMasker> maskers = identityMaskSource.load(maskedIds);
        return rows.stream()
                .map(row -> toHrResponse(row, maskers.get(row.getApplicationId())))
                .toList();
    }

    // masker != null <=> don dang an danh.
    private static HrReconsiderationResponse toHrResponse(ReconsiderationHrRow row, IdentityMasker masker) {
        boolean masked = masker != null;
        return new HrReconsiderationResponse(
                row.getApplicationId(),
                masked ? CandidateAlias.of(row.getApplicationId()) : row.getCandidateFullName(),
                masked,
                row.getAppliedAt(),
                row.getRejectedAt(),
                row.getRequestedAt(),
                masked ? masker.mask(row.getReason()) : row.getReason(),
                ReconsiderationStatus.valueOf(row.getStatus()),
                row.getDecidedAt(),
                masked ? masker.mask(row.getHrResponse()) : row.getHrResponse());
    }

    // R-C1 - bang 6 state. Co yeu cau thi khoi LUON hien, moi trang thai don. AVAILABLE/EXPIRED doc withinDeadline da
    // tinh bang now() trong SQL (R-T4), Java khong so thoi gian.
    static CandidateReconsiderationResponse toCandidateBlock(ReconsiderationCandidateRow row) {
        if (row.getRequestStatus() != null) {
            CandidateReconsiderationState state = switch (ReconsiderationStatus.valueOf(row.getRequestStatus())) {
                case OPEN -> CandidateReconsiderationState.OPEN;
                case ACCEPTED -> CandidateReconsiderationState.ACCEPTED;
                case DECLINED -> CandidateReconsiderationState.DECLINED;
            };
            return new CandidateReconsiderationResponse(
                    state,
                    row.getDeadline(),
                    row.getRequestedAt(),
                    row.getReason(),
                    row.getDecidedAt(),
                    row.getHrResponse());
        }
        if (!rejectedAtScreening(row)) {
            return null;
        }
        CandidateReconsiderationState state;
        if (Boolean.TRUE.equals(row.getJobRemoved())) {
            state = CandidateReconsiderationState.JOB_REMOVED;
        } else if (Boolean.TRUE.equals(row.getWithinDeadline())) {
            state = CandidateReconsiderationState.AVAILABLE;
        } else {
            state = CandidateReconsiderationState.EXPIRED;
        }
        return new CandidateReconsiderationResponse(state, row.getDeadline(), null, null, null, null);
    }

    // R-E1 b, c - don dang REJECTED va dong vao REJECTED moi nhat di tu PENDING.
    static boolean rejectedAtScreening(ReconsiderationCandidateRow row) {
        return "REJECTED".equals(row.getApplicationStatus()) && "PENDING".equals(row.getLastRejectedFromStatus());
    }
}
