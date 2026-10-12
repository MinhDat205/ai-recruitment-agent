package com.recruitment.screening;

import com.recruitment.jobapplication.HrApplicationAccess;
import com.recruitment.screening.dto.ApplicationScreeningResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// FR-H10 S6 - HR doc ban chup cau tra loi cua mot don. Chi DOC. Quyen so huu kiem DUY NHAT qua
// HrApplicationAccess.loadOwned (R-A3): cong ty truoc (404 COMPANY_NOT_FOUND), roi don (404 APPLICATION_NOT_FOUND),
// don cua cong ty khac -> 403. Khong trang thai Job/don nao khoa S6 (R-A4).
// FR-U09 R-C5: phan doc + tinh state chuyen sang ApplicationScreeningReader dung chung voi F3; hanh vi S6 khong doi.
@Service
public class ApplicationScreeningHrService {

    private final HrApplicationAccess hrApplicationAccess;
    private final ApplicationScreeningReader reader;

    public ApplicationScreeningHrService(HrApplicationAccess hrApplicationAccess, ApplicationScreeningReader reader) {
        this.hrApplicationAccess = hrApplicationAccess;
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public ApplicationScreeningResponse getAnswers(UUID ownerId, UUID applicationId) {
        hrApplicationAccess.loadOwned(ownerId, applicationId);
        return reader.read(applicationId);
    }
}
