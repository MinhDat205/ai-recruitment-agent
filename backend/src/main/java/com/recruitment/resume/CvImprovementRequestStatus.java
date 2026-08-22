package com.recruitment.resume;

// 4 gia tri PENDING/RUNNING/DONE/FAILED duoc luu that o cot cv_improvement_requests.status (V6).
// NOT_REQUESTED KHONG BAO GIO duoc ghi xuong DB - day la gia tri CHI dung o tang API/DTO
// (CvImprovementSuggestionResponse.status, Dot 5) khi service khong tim thay ca
// cv_improvement_requests lan cv_improvement_suggestions nao cho resumeId - "chua tung yeu cau".
// Dung chung mot enum thay vi tach rieng entity-status/dto-status de tranh mot enum thu hai chi
// khac dung 1 gia tri; noi ghi entity (Dot 4/5) khong bao gio duoc gan NOT_REQUESTED.
public enum CvImprovementRequestStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    NOT_REQUESTED
}
