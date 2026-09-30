package com.recruitment.common.exception;

// FR-C05 R-J5 - Job thieu nganh nghe/tinh thanh theo danh muc (R-J3). Hai ngu canh co thong diep
// rieng: chuyen sang OPEN, va update lam mat dieu kien cua tin DANG mo. REMOTE thi khong nhac tinh.
public class JobCatalogIncompleteException extends RuntimeException {

    private JobCatalogIncompleteException(String message) {
        super(message);
    }

    public static JobCatalogIncompleteException forOpening(boolean remote) {
        return new JobCatalogIncompleteException(remote
                ? "Cần chọn ngành nghề từ danh mục trước khi mở tin tuyển dụng."
                : "Cần chọn ngành nghề và tỉnh/thành từ danh mục trước khi mở tin tuyển dụng.");
    }

    public static JobCatalogIncompleteException forOpenJobUpdate(boolean remote) {
        return new JobCatalogIncompleteException(remote
                ? "Tin đang mở phải giữ ngành nghề từ danh mục."
                : "Tin đang mở phải giữ ngành nghề và tỉnh/thành từ danh mục.");
    }
}
