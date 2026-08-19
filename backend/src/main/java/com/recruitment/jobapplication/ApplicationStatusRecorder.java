package com.recruitment.jobapplication;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Bean GHI rieng cho lich su trang thai don ung tuyen - tach ra tu method private cu
// (recordStatusChange) trong ApplicationService. Ly do tach (xem CLAUDE.md muc 3c va
// ScoringRunStateService/ResumeParsingStateService): tu Dot nay, ApplicationStatusService (E1,
// FR-H07, HR doi trang thai) can goi lai logic ghi lich su nay tu MOT BEAN KHAC voi
// ApplicationService (C2 apply/C4 withdraw) - neu van la method private tu goi trong
// ApplicationService thi khong the tai su dung tu bean khac; con neu chi doi private -> public
// ngay trong ApplicationService thi loi goi tu ben ngoai van la self-invocation ve mat
// @Transactional cua CHINH method do (khong quan trong o day vi method khong tu mang
// @Transactional rieng, nhung tach bean la buoc dung huong cho moi lan ghi trang thai sau nay).
@Service
public class ApplicationStatusRecorder {

    private final ApplicationStatusHistoryRepository statusHistoryRepository;

    public ApplicationStatusRecorder(ApplicationStatusHistoryRepository statusHistoryRepository) {
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional
    public void record(
            UUID applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus, UUID changedBy, String note) {
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplicationId(applicationId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        statusHistoryRepository.save(history);
    }
}
