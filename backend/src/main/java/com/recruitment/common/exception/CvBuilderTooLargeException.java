package com.recruitment.common.exception;

// FR-U12 R-P9 - file PDF dung tu form vuot nguong (400 CV_BUILDER_TOO_LARGE). Cau co dinh muc 4.4.
public class CvBuilderTooLargeException extends RuntimeException {

    public CvBuilderTooLargeException() {
        super("File CV tạo ra vượt quá 10MB. Vui lòng rút gọn nội dung.");
    }
}
