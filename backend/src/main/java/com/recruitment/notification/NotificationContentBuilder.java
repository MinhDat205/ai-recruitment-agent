package com.recruitment.notification;

import com.recruitment.job.Job;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.notification.InterviewInvitationResentEvent.SlotInfo;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// Chi xay noi dung tu du lieu DA LOAD SAN (Job, ten ung vien) - KHONG tu truy van DB, de test don
// vi khong can Spring context. KHONG doc scoring_runs/criterion_scores o dau trong file nay -
// thong bao cho ung vien tuyet doi khong duoc lo diem/nhan xet noi bo cua HR (CLAUDE.md muc 8).
final class NotificationContentBuilder {

    private static final String MESSAGES_TAB_QUERY = "?tab=messages";
    private static final String INVITATION_TAB_QUERY = "?tab=invitation";

    // FR-H12 R-N3 - gio trong email theo gio Viet Nam, cung dinh dang MessageDraftService.INTERVIEW_TIME.
    private static final DateTimeFormatter SLOT_TIME =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String MISSING_LOCATION = "Chưa có dữ liệu";

    private NotificationContentBuilder() {}

    // emailBody (FR-H12 R-N3): noi dung email rieng, ghi vao notifications.email_body. null = email dung body nhu cu -
    // moi ham cu tra null nen email cua cac loai cu khong doi (R-N3a).
    record Content(String title, String body, String link, String emailBody) {

        Content(String title, String body, String link) {
            this(title, body, link, null);
        }
    }

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
    //
    // FR-H16 L1 - candidateLabel la CA CUM chi ung vien do listener dung: "Ung vien " + ho ten (khong an danh) hoac nhan
    // CandidateAlias "Ung vien #XXXXXXXX" (an danh). Builder KHONG tu ghep tien to "Ung vien " nua - noi dung khi khong an
    // danh giu nguyen tung chu.
    static Content forApplicationSubmitted(Job job, String candidateLabel, UUID applicationId) {
        return new Content(
                "Có đơn ứng tuyển mới",
                candidateLabel + " vừa ứng tuyển vị trí \"" + job.getTitle() + "\"",
                hrApplicationLink(applicationId));
    }

    static Content forApplicationWithdrawn(Job job, String candidateLabel, UUID applicationId) {
        return new Content(
                "Ứng viên đã rút đơn",
                candidateLabel + " đã rút đơn ứng tuyển vị trí \"" + job.getTitle() + "\"",
                hrApplicationLink(applicationId));
    }

    static Content forAggregationFinished(Job job, UUID applicationId) {
        return new Content(
                "Đã chấm điểm xong một đợt hồ sơ",
                "Một đợt chấm điểm cho vị trí \"" + job.getTitle() + "\" đã hoàn tất, mời bạn xem kết quả",
                hrApplicationLink(applicationId));
    }

    // FR-C06 R-N3 - tin nhan moi. recipientIsCandidate: nguoi nhan la ung vien (senderLabel = TEN CONG TY, khong
    // phai ho ten HR - R-I2) hay HR (senderLabel = ca cum "Ung vien ..." do listener dung - FR-H16 L1). excerpt null
    // (tin chi co tep) -> cau "da gui mot tep dinh kem", khong co doan trich (FR-H16 L2: giu cau nay ca khi an danh, chi
    // doi nhan). Link mo thang tab "Trao doi" (?tab=messages).
    static Content forNewMessage(
            Job job, String senderLabel, boolean recipientIsCandidate, String excerpt, UUID applicationId) {
        String subject = senderLabel;
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

    // FR-H16 R-O2 [Q7] - tin co chu cua ung vien gui HR khi don dang an danh: KHONG co doan trich (doan trich da cat san
    // trong su kien - che chuoi tren do co the lot nua ten, a12). Email dung cung body (a11).
    static Content forNewMessageWithoutExcerpt(Job job, String candidateLabel, UUID applicationId) {
        return new Content(
                "Tin nhắn mới từ ứng viên",
                candidateLabel + " đã gửi tin nhắn về đơn ứng tuyển vị trí \"" + job.getTitle() + "\"",
                hrApplicationLink(applicationId) + MESSAGES_TAB_QUERY);
    }

    // FR-H12 R-N2, R-N3 - HR gui lai giay moi. body MOT dong, khong chua khung gio (web); emailBody = body + danh sach
    // khung (gio Viet Nam) + dong lien ket, dung MOT lan luc tao thong bao tu du lieu trong su kien.
    static Content forInvitationResent(Job job, List<SlotInfo> slots, UUID applicationId, String frontendBaseUrl) {
        String change = slots.size() == 1 ? "lịch phỏng vấn mới" : slots.size() + " khung giờ";
        String body = "Giấy mời phỏng vấn vị trí \"" + job.getTitle() + "\" vừa được gửi lại với " + change
                + ". Xem chi tiết trong đơn ứng tuyển.";
        String link = candidateApplicationLink(applicationId);

        StringBuilder email = new StringBuilder(body).append("\n\nKhung giờ (giờ Việt Nam):");
        for (int i = 0; i < slots.size(); i++) {
            SlotInfo slot = slots.get(i);
            String location = slot.location() == null ? MISSING_LOCATION : slot.location();
            email.append('\n').append(i + 1).append(". ").append(SLOT_TIME.format(slot.scheduledAt()))
                    .append(" — ").append(location);
        }
        email.append("\n\nXem giấy mời tại: ").append(frontendBaseUrl).append(link);

        return new Content("Nhà tuyển dụng đã gửi lại giấy mời phỏng vấn", body, link, email.toString());
    }

    // FR-U10 R-N3, R-N4 - ung vien chon khung gio. body MOT dong co gio Viet Nam, KHONG dia diem, KHONG xuong dong (web);
    // emailBody = body + dia diem + dong lien ket. Link mo thang tab giay moi cua ho so don HR.
    static Content forSlotConfirmed(
            Job job,
            String candidateName,
            Instant scheduledAt,
            String location,
            UUID applicationId,
            String frontendBaseUrl) {
        String body = "Ứng viên " + candidateName + " đã chọn khung giờ phỏng vấn " + SLOT_TIME.format(scheduledAt)
                + " (giờ Việt Nam) cho vị trí \"" + job.getTitle() + "\".";
        String link = hrApplicationLink(applicationId) + INVITATION_TAB_QUERY;
        String email = body
                + "\n\nĐịa điểm: " + (location == null ? MISSING_LOCATION : location)
                + "\n\nXem giấy mời tại: " + frontendBaseUrl + link;
        return new Content("Ứng viên đã chọn lịch phỏng vấn", body, link, email);
    }

    private static String hrApplicationLink(UUID applicationId) {
        return "/hr/applications/" + applicationId;
    }

    private static String candidateApplicationLink(UUID applicationId) {
        return "/candidate/applications/" + applicationId;
    }
}
