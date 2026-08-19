package com.recruitment.scoring;

import java.math.BigDecimal;
import java.util.UUID;

// Interface projection cho native query (ScoringRunRepository.findLatestDoneByApplicationIdIn) -
// mau LatestScoringRunView nhung LOC RIENG status='DONE': day la nguon diem cho xep hang (Q5, ke
// hoach D3) - lot DONE MOI NHAT cua moi don, KHONG PHAI lot moi nhat bat ke trang thai
// (LatestScoringRunView chi phuc vu hien thi TIEN DO, hai khai niem khac nhau du cung doc tu
// scoring_runs). totalScore la BigDecimal (khong phai enum nhu status o LatestScoringRunView) nen
// khong can chuyen doi thu cong o tang service.
public interface LatestDoneScoringRunView {

    UUID getApplicationId();

    UUID getId();

    BigDecimal getTotalScore();
}
