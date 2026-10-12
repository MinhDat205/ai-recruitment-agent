package com.recruitment.common.exception;

// FR-U09 R-K7 - rut dong y khi don khong o trang thai ACTIVE (NOT_GIVEN, da REVOKED, hoac hai tab bam cung luc).
public class TalentPoolConsentNotActiveException extends RuntimeException {

    public TalentPoolConsentNotActiveException() {
        super("Đơn này hiện không có đồng ý lưu hồ sơ để rút.");
    }
}
