package com.recruitment.notification;

import com.recruitment.job.Job;
import com.recruitment.jobapplication.ApplicationStatus;
import java.util.Map;
import java.util.UUID;

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

    // FR-H09 R-N1 - ca 3 thong bao cua HR deu gan voi MOT don (NotificationEventListener luu
    // applicationId vao entity_id) nen link tro thang trang ho so don /hr/applications/{id}. Thong
    // bao da co trong DB truoc FR-H09 giu nguyen link cu /hr/jobs (R-N2, khong migration). Chi doi
    // link - tieu de, noi dung giu nguyen (R-N3).
    static Content forApplicationSubmitted(Job job, String candidateName, UUID applicationId) {
        return new Content(
                "Có đơn ứng tuyển mới",
                "Ứng viên " + candidateName + " vừa ứng tuyển vị trí \"" + job.getTitle() + "\"",
                hrApplicationLink(applicationId));
    }

    static Content forApplicationWithdrawn(Job job, String candidateName, UUID applicationId) {
        return new Content(
                "Ứng viên đã rút đơn",
                "Ứng viên " + candidateName + " đã rút đơn ứng tuyển vị trí \"" + job.getTitle() + "\"",
                hrApplicationLink(applicationId));
    }

    static Content forAggregationFinished(Job job, UUID applicationId) {
        return new Content(
                "Đã chấm điểm xong một đợt hồ sơ",
                "Một đợt chấm điểm cho vị trí \"" + job.getTitle() + "\" đã hoàn tất, mời bạn xem kết quả",
                hrApplicationLink(applicationId));
    }

    private static String hrApplicationLink(UUID applicationId) {
        return "/hr/applications/" + applicationId;
    }
}
