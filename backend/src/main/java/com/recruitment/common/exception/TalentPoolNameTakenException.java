package com.recruitment.common.exception;

// FR-H15 R-K3 - cong ty da co kho chua xoa cung ten (khong phan biet hoa thuong) (409 TALENT_POOL_NAME_TAKEN).
// Nhanh uq_talent_pool_name_per_company cua GlobalExceptionHandler lay CHINH cau nay (khuon uq_scoring_run_in_progress).
public class TalentPoolNameTakenException extends RuntimeException {

    public TalentPoolNameTakenException() {
        super("Công ty đã có kho ứng viên với tên này.");
    }
}
