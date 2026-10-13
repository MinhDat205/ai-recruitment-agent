package com.recruitment.jobdraft.dto;

import com.recruitment.common.exception.AiSyncErrorCode;
import com.recruitment.jobdraft.PartStatus;

// FR-H11 R-N6 - o ket qua cua mot phan. SUCCEEDED: data + generationToken (K4) khac null, error/message null. FAILED:
// error = ten AiSyncErrorCode, message = cau co dinh cua ma do (R-K3H-5 - KHONG e.getMessage()), data/token null.
public record PartResult<T>(PartStatus status, T data, String generationToken, String error, String message) {

    public static <T> PartResult<T> succeeded(T data, String generationToken) {
        return new PartResult<>(PartStatus.SUCCEEDED, data, generationToken, null, null);
    }

    public static <T> PartResult<T> failed(AiSyncErrorCode errorCode) {
        return new PartResult<>(PartStatus.FAILED, null, null, errorCode.name(), errorCode.message());
    }
}
