package com.recruitment.common.exception;

// FR-U12 R-P8, L7 - dung PDF loi (IOException cua PDFBox), trich van ban loi (ResumeExtractionException), raw_text rong
// hoac thieu nguyen van mot o (K2 luc chay) -> 500 CV_BUILDER_RENDER_FAILED. Cau co dinh muc 4.4; nguyen nhan goc (neu
// co) chi giu lam cause cho log.debug - KHONG dua vao message.
public class CvBuilderRenderFailedException extends RuntimeException {

    private static final String MESSAGE = "Không tạo được file CV, vui lòng thử lại.";

    public CvBuilderRenderFailedException() {
        super(MESSAGE);
    }

    public CvBuilderRenderFailedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
