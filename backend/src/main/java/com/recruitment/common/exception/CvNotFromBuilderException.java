package com.recruitment.common.exception;

// FR-U12 R-B2 - CV cua chinh ung vien nhung khong co hang cv_builder_documents (CV tai len tu file) -> 409
// CV_NOT_FROM_BUILDER. Cau co dinh muc 4.4.
public class CvNotFromBuilderException extends RuntimeException {

    public CvNotFromBuilderException() {
        super("CV này được tải lên từ file nên không sửa được bằng công cụ tạo CV.");
    }
}
