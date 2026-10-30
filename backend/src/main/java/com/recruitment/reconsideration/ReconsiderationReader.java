package com.recruitment.reconsideration;

import com.recruitment.reconsideration.dto.CandidateReconsiderationResponse;
import com.recruitment.reconsideration.dto.CandidateReconsiderationState;
import java.util.UUID;
import org.springframework.stereotype.Component;

// FR-C09 Plan Mode L4 - noi DUY NHAT dung khoi xem xet lai tu du lieu DB, de jobapplication/ (E1 ung vien, E1 FR-H09)
// goi ma khong tu viet lai logic han 168 gio. Chi doc; khong @Transactional: tham gia transaction cua noi goi.
@Component
public class ReconsiderationReader {

    private final ReconsiderationRequestRepository repository;

    public ReconsiderationReader(ReconsiderationRequestRepository repository) {
        this.repository = repository;
    }

    // R-C1 - khoi o E1 ung vien; null khi don khong co yeu cau va chua tung bi tu choi o vong ho so (hoac khong phai don
    // cua candidateId - noi goi da kiem quyen truoc).
    public CandidateReconsiderationResponse candidateBlock(UUID applicationId, UUID candidateId) {
        return repository
                .findCandidateRow(applicationId, candidateId)
                .map(ReconsiderationReader::toCandidateBlock)
                .orElse(null);
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
