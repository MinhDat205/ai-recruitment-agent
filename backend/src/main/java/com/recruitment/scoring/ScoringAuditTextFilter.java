package com.recruitment.scoring;

import java.util.UUID;
import java.util.function.UnaryOperator;

// FR-H16 L4 (R-A4) - bo loc chuoi cua audit cham diem tra cho HR (ScoringRunAuditService). scoring/ chi biet interface
// nay, KHONG import package an danh cua FR-H16; cai dat duy nhat la BlindReviewScoringAuditFilter (REQUIREMENT 7.3 kiem).
// Chi ap len du lieu tra cho HR o tang dung response - pipeline cham (D2/D3/D4) khong dung toi (R-A2).
public interface ScoringAuditTextFilter {

    // Ham ap len reasoning va evidence[].quote cua MOT don; don khong an danh -> UnaryOperator.identity().
    UnaryOperator<String> forApplication(UUID applicationId);
}
