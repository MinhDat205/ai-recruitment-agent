package com.recruitment.common.exception;

// FR-H15 P9 - don khong co muc removed_at IS NULL trong kho (404 TALENT_POOL_ENTRY_NOT_FOUND).
public class TalentPoolEntryNotFoundException extends RuntimeException {

    public TalentPoolEntryNotFoundException() {
        super("Hồ sơ này không có trong kho.");
    }
}
