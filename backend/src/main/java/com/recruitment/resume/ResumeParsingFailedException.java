package com.recruitment.resume;

// Message la formatted() cua errorCode (chuoi tieng Viet co dinh) - KHONG bao gio la message goc
// cua exception that bai (Jackson parse error, loi mang...). cause() van giu exception goc de
// log.debug o noi bat, nhung khong bao gio duoc doc ra ngoai qua getMessage().
public class ResumeParsingFailedException extends RuntimeException {

    private final ResumeParsingErrorCode errorCode;

    public ResumeParsingFailedException(ResumeParsingErrorCode errorCode, Throwable cause) {
        super(errorCode.formatted(), cause);
        this.errorCode = errorCode;
    }

    public ResumeParsingErrorCode errorCode() {
        return errorCode;
    }
}
