package com.recruitment.common.exception;

// FR-C06 R-F8 - tin khong co tep, hoac tep khong con trong kho luu tru.
public class MessageAttachmentNotFoundException extends RuntimeException {

    public MessageAttachmentNotFoundException() {
        super("Không tìm thấy tệp đính kèm");
    }
}
