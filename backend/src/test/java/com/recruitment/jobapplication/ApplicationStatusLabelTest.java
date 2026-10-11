package com.recruitment.jobapplication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

// FR-C07 T21 (phan don vi) - nhan tieng Viet cua ApplicationStatus phai DUNG 5 chuoi cu cua NotificationContentBuilder
// (truoc FR-C07) - noi dung thong bao doi trang thai khong duoc doi mot ky tu. Phan con lai cua T21: toan bo test co san
// cua notification/ pass khong sua.
class ApplicationStatusLabelTest {

    @Test
    void labelVi_matchesExactlyTheFiveExistingLabels() {
        Map<ApplicationStatus, String> expected = Map.of(
                ApplicationStatus.PENDING, "Chờ duyệt",
                ApplicationStatus.INTERVIEW_INVITED, "Đã mời phỏng vấn",
                ApplicationStatus.HIRED, "Trúng tuyển",
                ApplicationStatus.REJECTED, "Bị từ chối",
                ApplicationStatus.WITHDRAWN, "Đã rút đơn");

        assertThat(ApplicationStatus.values()).hasSize(expected.size());
        for (ApplicationStatus status : ApplicationStatus.values()) {
            assertThat(status.labelVi()).as(status.name()).isEqualTo(expected.get(status));
        }
    }
}
