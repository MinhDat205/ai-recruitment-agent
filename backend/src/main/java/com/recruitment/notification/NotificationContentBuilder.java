package com.recruitment.notification;

import com.recruitment.job.Job;
import com.recruitment.jobapplication.ApplicationStatus;
import java.util.Map;

// Chi xay noi dung tu du lieu DA LOAD SAN (Job, ten ung vien) - KHONG tu truy van DB, de test don
// vi khong can Spring context. KHONG doc scoring_runs/criterion_scores o dau trong file nay -
// thong bao cho ung vien tuyet doi khong duoc lo diem/nhan xet noi bo cua HR (CLAUDE.md muc 8).
final class NotificationContentBuilder {

    // Khop DUNG wording o frontend/src/features/applications/applicationLabels.ts
    // (APPLICATION_STATUS_LABELS) - doi mot ben phai doi ca hai, khong tach hang so dung chung
    // giua backend/frontend (khac ngon ngu, khong the import).
    private static final Map<ApplicationStatus, String> STATUS_LABELS = Map.of(
            ApplicationStatus.PENDING, "Chờ duyệt",
            ApplicationStatus.INTERVIEW_INVITED, "Đã mời phỏng vấn",
            ApplicationStatus.HIRED, "Trúng tuyển",
            ApplicationStatus.REJECTED, "Bị từ chối",
            ApplicationStatus.WITHDRAWN, "Đã rút đơn");

    private NotificationContentBuilder() {}

    record Content(String title, String body, String link) {}

    static Content forStatusChanged(Job job, ApplicationStatus toStatus) {
        String label = STATUS_LABELS.getOrDefault(toStatus, toStatus.name());
        return new Content(
                "Cập nhật đơn ứng tuyển",
                "Đơn ứng tuyển vị trí \"" + job.getTitle() + "\" của bạn đã chuyển sang trạng thái: " + label,
                "/candidate/applications");
    }

    // Link tro ve /hr/jobs - frontend chua co trang chi tiet danh sach ung vien theo tung job
    // (ApplicationOwnerService backend da san sang tu truoc nhung chua co route/trang goi toi),
    // day la gioi han pham vi co chu dich cua FR-C03, khong phai thieu sot.
    static Content forApplicationSubmitted(Job job, String candidateName) {
        return new Content(
                "Có đơn ứng tuyển mới",
                "Ứng viên " + candidateName + " vừa ứng tuyển vị trí \"" + job.getTitle() + "\"",
                "/hr/jobs");
    }

    static Content forApplicationWithdrawn(Job job, String candidateName) {
        return new Content(
                "Ứng viên đã rút đơn",
                "Ứng viên " + candidateName + " đã rút đơn ứng tuyển vị trí \"" + job.getTitle() + "\"",
                "/hr/jobs");
    }

    static Content forAggregationFinished(Job job) {
        return new Content(
                "Đã chấm điểm xong một đợt hồ sơ",
                "Một đợt chấm điểm cho vị trí \"" + job.getTitle() + "\" đã hoàn tất, mời bạn xem kết quả",
                "/hr/jobs");
    }
}
