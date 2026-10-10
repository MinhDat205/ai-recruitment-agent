package com.recruitment.notification;

import com.recruitment.job.Job;
import com.recruitment.jobapplication.ApplicationStatus;
import java.util.UUID;

// Chi xay noi dung tu du lieu DA LOAD SAN (Job, ten ung vien) - KHONG tu truy van DB, de test don
// vi khong can Spring context. KHONG doc scoring_runs/criterion_scores o dau trong file nay -
// thong bao cho ung vien tuyet doi khong duoc lo diem/nhan xet noi bo cua HR (CLAUDE.md muc 8).
final class NotificationContentBuilder {

    private static final String MESSAGES_TAB_QUERY = "?tab=messages";

    private NotificationContentBuilder() {}

    record Content(String title, String body, String link) {}

    // FR-U08 R-N1 - thong bao doi trang thai cua UNG VIEN tro thang trang chi tiet don
    // /candidate/applications/{id}. Thong bao da co trong DB truoc FR-U08 giu nguyen link cu
    // /candidate/applications (R-N2, khong migration). Chi doi link - tieu de, noi dung giu nguyen (R-N3).
    static Content forStatusChanged(Job job, ApplicationStatus toStatus, UUID applicationId) {
        // FR-C07 (muc 4.2) - nhan lay tu ApplicationStatus.labelVi(), khong con map rieng o day; noi dung khong doi.
        String label = toStatus.labelVi();
        return new Content(
                "Cập nhật đơn ứng tuyển",
                "Đơn ứng tuyển vị trí \"" + job.getTitle() + "\" của bạn đã chuyển sang trạng thái: " + label,
                candidateApplicationLink(applicationId));
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

    // FR-C06 R-N3 - tin nhan moi. recipientIsCandidate: nguoi nhan la ung vien (senderName = TEN CONG TY, khong
    // phai ho ten HR - R-I2) hay HR (senderName = ho ten ung vien). excerpt null (tin chi co tep) -> cau "da gui
    // mot tep dinh kem", khong co doan trich. Link mo thang tab "Trao doi" (?tab=messages).
    static Content forNewMessage(
            Job job, String senderName, boolean recipientIsCandidate, String excerpt, UUID applicationId) {
        String subject = recipientIsCandidate ? senderName : "Ứng viên " + senderName;
        String position = "về đơn ứng tuyển vị trí \"" + job.getTitle() + "\"";
        String body = excerpt != null
                ? subject + " đã gửi tin nhắn " + position + ": " + excerpt
                : subject + " đã gửi một tệp đính kèm " + position;
        return recipientIsCandidate
                ? new Content(
                        "Tin nhắn mới từ nhà tuyển dụng",
                        body,
                        candidateApplicationLink(applicationId) + MESSAGES_TAB_QUERY)
                : new Content(
                        "Tin nhắn mới từ ứng viên",
                        body,
                        hrApplicationLink(applicationId) + MESSAGES_TAB_QUERY);
    }

    private static String hrApplicationLink(UUID applicationId) {
        return "/hr/applications/" + applicationId;
    }

    private static String candidateApplicationLink(UUID applicationId) {
        return "/candidate/applications/" + applicationId;
    }
}
