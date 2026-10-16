package com.recruitment.talentpool.dto;

import java.util.List;
import java.util.UUID;

// FR-H15 P7 - TOAN BO tap kho mong muon chua don (R-P1). KHONG chuan hoa null -> List.of() o day: poolIds null / phan
// tu null phai ra 400 INVALID_TALENT_POOL_SELECTION (R-P2 buoc 1), khong duoc hieu thanh "go khoi moi kho".
public record ApplicationTalentPoolsRequest(List<UUID> poolIds) {
}
