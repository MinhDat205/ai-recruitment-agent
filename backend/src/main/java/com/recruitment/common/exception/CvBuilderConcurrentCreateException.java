package com.recruitment.common.exception;

// FR-U12 R-W5 - race CV dau tien: uq_resume_primary_per_candidate chan mot trong hai request tao CV cung luc (409
// CV_BUILDER_CONCURRENT_CREATE). Cau co dinh muc 4.4.
public class CvBuilderConcurrentCreateException extends RuntimeException {

    public CvBuilderConcurrentCreateException() {
        super("Đang có một CV khác được tạo cùng lúc. Vui lòng tải lại trang để xem danh sách CV.");
    }
}
