package com.recruitment.jobapplication;

public enum ApplicationStatus {
    PENDING,
    INTERVIEW_INVITED,
    HIRED,
    REJECTED,
    WITHDRAWN;

    // FR-C07 (muc 4.2) - nhan tieng Viet DUY NHAT o backend, dung chung cho noi dung thong bao (NotificationContentBuilder)
    // va ngu canh AI soan nhap (ai/messagedraft). Khop DUNG wording o frontend/src/features/applications/
    // applicationLabels.ts (APPLICATION_STATUS_LABELS) - doi mot ben phai doi ca hai, khong tach hang so dung chung giua
    // backend/frontend (khac ngon ngu, khong the import). switch phu du 5 gia tri: them trang thai moi ma quen nhan thi
    // khong bien dich duoc.
    public String labelVi() {
        return switch (this) {
            case PENDING -> "Chờ duyệt";
            case INTERVIEW_INVITED -> "Đã mời phỏng vấn";
            case HIRED -> "Trúng tuyển";
            case REJECTED -> "Bị từ chối";
            case WITHDRAWN -> "Đã rút đơn";
        };
    }
}
