package com.recruitment.talentpool;

import java.time.Instant;
import java.util.UUID;

// FR-H15 R-C2 - ket qua cau khoa kho (TalentPoolRepository.lockForShare). Service dung de kiem R-P2 buoc 5 (thieu/da xoa
// -> 404) truoc buoc 6 (khac cong ty -> 403) tren toan danh sach.
public interface PoolLockRow {

    UUID getId();

    UUID getCompanyId();

    Instant getDeletedAt();
}
