package com.recruitment.common.exception;

// FR-U16 R-S3 - luu tin ma findOpenJobById rong: het han, tam dung, da dong, nhap, xoa mem, UUID khong ton tai, va ca
// tin dang luu nhung khong con mo (B7). Mot ma, mot cau, khong phan biet - khong lo tin nhap co ton tai hay khong.
public class JobNotSaveableException extends RuntimeException {

    public JobNotSaveableException() {
        super("Tin tuyển dụng này hiện không nhận lưu vì không còn mở.");
    }
}
