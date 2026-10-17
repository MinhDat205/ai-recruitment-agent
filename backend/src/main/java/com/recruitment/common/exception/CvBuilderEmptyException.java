package com.recruitment.common.exception;

// FR-U12 R-F10 - form null, hoac sau chuan hoa khong co ho ten va it nhat mot muc o nam khoi danh sach (400
// CV_BUILDER_EMPTY). Handler o GlobalExceptionHandler (dot 4).
public class CvBuilderEmptyException extends RuntimeException {

    public CvBuilderEmptyException() {
        super("CV cần có họ và tên và ít nhất một mục ở học vấn, kinh nghiệm, kỹ năng, chứng chỉ hoặc dự án.");
    }
}
