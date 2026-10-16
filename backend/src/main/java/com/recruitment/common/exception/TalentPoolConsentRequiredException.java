package com.recruitment.common.exception;

// FR-H15 R-M3, R-P2 buoc 7 - P7 can them muc nhung dong y luu ho so cua don khong ACTIVE
// (409 TALENT_POOL_CONSENT_REQUIRED). Go muc khong bao gio nem exception nay.
public class TalentPoolConsentRequiredException extends RuntimeException {

    public TalentPoolConsentRequiredException() {
        super("Ứng viên chưa đồng ý cho công ty lưu hồ sơ của đơn này nên không thể thêm vào kho.");
    }
}
