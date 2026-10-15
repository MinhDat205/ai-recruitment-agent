package com.recruitment.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.recruitment.job.Job;
import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.notification.InterviewInvitationResentEvent.SlotInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// FR-H12 T20 - NotificationContentBuilder.forInvitationResent; FR-U10 T11 - forSlotConfirmed (ham thuan, khong Spring context). Gio theo
// Asia/Ho_Chi_Minh, KHONG theo mui gio may chay test.
class NotificationContentBuilderTest {

    private static final UUID APPLICATION_ID = UUID.fromString("e8000000-0000-0000-0000-000000000002");
    private static final String BASE_URL = "http://localhost:5173";

    private static Job job() {
        Job job = new Job();
        job.setTitle("Nhân viên Hành chính văn phòng");
        return job;
    }

    @Test
    void forInvitationResent_oneSlot_bodyIsOneLineWithNewScheduleSentence() {
        NotificationContentBuilder.Content content = NotificationContentBuilder.forInvitationResent(
                job(),
                List.of(new SlotInfo(Instant.parse("2026-10-20T02:00:00Z"), "Tầng 3")),
                APPLICATION_ID,
                BASE_URL);

        assertThat(content.title()).isEqualTo("Nhà tuyển dụng đã gửi lại giấy mời phỏng vấn");
        assertThat(content.body())
                .isEqualTo("Giấy mời phỏng vấn vị trí \"Nhân viên Hành chính văn phòng\" vừa được gửi lại với lịch phỏng vấn "
                        + "mới. Xem chi tiết trong đơn ứng tuyển.")
                .doesNotContain("\n")
                .doesNotContain("09:00");
        assertThat(content.link()).isEqualTo("/candidate/applications/" + APPLICATION_ID);
        assertThat(content.emailBody())
                .isEqualTo(content.body()
                        + "\n\nKhung giờ (giờ Việt Nam):"
                        + "\n1. 09:00 20/10/2026 — Tầng 3"
                        + "\n\nXem giấy mời tại: http://localhost:5173/candidate/applications/" + APPLICATION_ID);
    }

    @Test
    void forInvitationResent_threeSlots_bodyCountsSlots_emailListsSlotsInVietnamTime() {
        NotificationContentBuilder.Content content = NotificationContentBuilder.forInvitationResent(
                job(),
                List.of(
                        new SlotInfo(Instant.parse("2026-10-20T02:00:00Z"), "Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"),
                        new SlotInfo(Instant.parse("2026-10-20T18:30:00Z"), "https://meet.example.com/abc-defg-hij"),
                        new SlotInfo(Instant.parse("2026-10-22T07:30:00Z"), null)),
                APPLICATION_ID,
                BASE_URL);

        assertThat(content.body())
                .isEqualTo("Giấy mời phỏng vấn vị trí \"Nhân viên Hành chính văn phòng\" vừa được gửi lại với 3 khung giờ. "
                        + "Xem chi tiết trong đơn ứng tuyển.")
                .doesNotContain("\n");
        // 18:30Z ngay 20 = 01:30 ngay 21 gio Viet Nam; location null -> "Chưa có dữ liệu".
        assertThat(content.emailBody())
                .isEqualTo(content.body()
                        + "\n\nKhung giờ (giờ Việt Nam):"
                        + "\n1. 09:00 20/10/2026 — Tầng 3, 45 Bạch Đằng, Quận Hải Châu, Đà Nẵng"
                        + "\n2. 01:30 21/10/2026 — https://meet.example.com/abc-defg-hij"
                        + "\n3. 14:30 22/10/2026 — Chưa có dữ liệu"
                        + "\n\nXem giấy mời tại: http://localhost:5173/candidate/applications/" + APPLICATION_ID);
    }

    // FR-U10 T11 - 18:30Z ngay 20 = 01:30 ngay 21 gio Viet Nam; body MOT dong, khong dia diem; link mo tab giay moi cua HR.
    @Test
    void forSlotConfirmed_bodyIsOneLineInVietnamTime_emailHasLocationAndLink() {
        NotificationContentBuilder.Content content = NotificationContentBuilder.forSlotConfirmed(
                job(),
                "Lê Thị Lan",
                Instant.parse("2026-10-20T18:30:00Z"),
                "Phòng họp Sông Hàn, tầng 5",
                APPLICATION_ID,
                BASE_URL);

        assertThat(content.title()).isEqualTo("Ứng viên đã chọn lịch phỏng vấn");
        assertThat(content.body())
                .isEqualTo("Ứng viên Lê Thị Lan đã chọn khung giờ phỏng vấn 01:30 21/10/2026 (giờ Việt Nam) cho vị trí "
                        + "\"Nhân viên Hành chính văn phòng\".")
                .doesNotContain("\n")
                .doesNotContain("Phòng họp Sông Hàn");
        assertThat(content.link()).isEqualTo("/hr/applications/" + APPLICATION_ID + "?tab=invitation");
        assertThat(content.emailBody())
                .isEqualTo(content.body()
                        + "\n\nĐịa điểm: Phòng họp Sông Hàn, tầng 5"
                        + "\n\nXem giấy mời tại: http://localhost:5173/hr/applications/" + APPLICATION_ID + "?tab=invitation");
        assertThat(content.emailBody().lines().reduce((first, second) -> second).orElseThrow())
                .isEqualTo("Xem giấy mời tại: http://localhost:5173/hr/applications/" + APPLICATION_ID + "?tab=invitation");
    }

    @Test
    void forSlotConfirmed_nullLocation_emailShowsMissingData() {
        NotificationContentBuilder.Content content = NotificationContentBuilder.forSlotConfirmed(
                job(), "Lê Thị Lan", Instant.parse("2026-10-20T02:00:00Z"), null, APPLICATION_ID, BASE_URL);

        assertThat(content.body()).contains("09:00 20/10/2026").doesNotContain("\n").doesNotContain("null");
        assertThat(content.emailBody()).contains("\n\nĐịa điểm: Chưa có dữ liệu\n\n").doesNotContain("null");
    }

    @Test
    void oldBuilders_returnNullEmailBody() {
        Job job = job();
        UUID id = UUID.randomUUID();

        assertThat(NotificationContentBuilder.forStatusChanged(job, ApplicationStatus.REJECTED, id).emailBody()).isNull();
        assertThat(NotificationContentBuilder.forApplicationSubmitted(job, "Ung Vien", id).emailBody()).isNull();
        assertThat(NotificationContentBuilder.forApplicationWithdrawn(job, "Ung Vien", id).emailBody()).isNull();
        assertThat(NotificationContentBuilder.forAggregationFinished(job, id).emailBody()).isNull();
        assertThat(NotificationContentBuilder.forNewMessage(job, "Cong ty", true, "Xin chao", id).emailBody()).isNull();
        assertThat(NotificationContentBuilder.forNewMessage(job, "Ung Vien", false, null, id).emailBody()).isNull();
    }
}
