package com.recruitment.common.exception;

// FR-C06 R-F2/R-F3 - tep dinh kem khong hop le. Mot ma loi chung (INVALID_MESSAGE_ATTACHMENT), cau thong bao
// khac nhau qua getMessage() - khuon InvalidResumeFileException.
public class InvalidMessageAttachmentException extends RuntimeException {

    public InvalidMessageAttachmentException(String message) {
        super(message);
    }
}
