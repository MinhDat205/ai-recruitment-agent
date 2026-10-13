package com.recruitment.interviewinvitation;

import com.recruitment.common.exception.InvalidInterviewScheduleException;
import com.recruitment.interviewinvitation.dto.InterviewInvitationSendRequest.SlotRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// FR-H12 R-S1..R-S8 - bo kiem khung DUNG CHUNG cho I2 (gui lan dau) va I3 (gui lai). Ham thuan: nhan `now` tu noi goi
// (khong tu goi Instant.now()) de I3 dung dung MOT moc thoi gian (R-G3 buoc 4) va test dat duoc moc.
//
// Thu tu kiem DUNG bang R-S8, tra loi DAU TIEN gap: hai dong dau tren ca danh sach; sau do khung 1..n, moi khung theo
// thu tu: thieu thoi diem -> khong o tuong lai -> trung khung truoc -> thieu dia diem -> qua 500 ky tu -> xuong dong.
// scheduledAt cat ve DAU PHUT TRUOC moi kiem tra (R-S3, Q1) - so tuong lai va so trung deu tren gia tri da cat.
// location bo khoang trang hai dau (String.strip) roi moi kiem rong/do dai/xuong dong (R-S6); gia tri luu la gia tri da
// strip. Khong loi nao ra NullPointerException.
final class InterviewSlotValidator {

    static final int MAX_SLOTS = 5;
    static final int MAX_LOCATION_LENGTH = 500;

    private InterviewSlotValidator() {}

    // Khung da chuan hoa (scheduledAt da cat phut, location da strip), theo dung thu tu HR gui (R-S7).
    record ValidSlot(Instant scheduledAt, String location) {
    }

    static List<ValidSlot> validate(List<SlotRequest> slots, Instant now) {
        if (slots == null || slots.isEmpty()) {
            throw new InvalidInterviewScheduleException("Vui lòng thêm ít nhất một khung giờ phỏng vấn.");
        }
        if (slots.size() > MAX_SLOTS) {
            throw new InvalidInterviewScheduleException("Giấy mời có tối đa " + MAX_SLOTS + " khung giờ.");
        }

        List<ValidSlot> result = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            int n = i + 1;
            SlotRequest slot = slots.get(i);
            if (slot == null || slot.scheduledAt() == null) {
                throw new InvalidInterviewScheduleException("Khung giờ " + n + ": vui lòng chọn thời điểm phỏng vấn.");
            }
            Instant scheduledAt = slot.scheduledAt().truncatedTo(ChronoUnit.MINUTES);
            if (!scheduledAt.isAfter(now)) {
                throw new InvalidInterviewScheduleException(
                        "Khung giờ " + n + ": thời điểm phỏng vấn phải ở trong tương lai.");
            }
            for (int j = 0; j < result.size(); j++) {
                if (result.get(j).scheduledAt().equals(scheduledAt)) {
                    throw new InvalidInterviewScheduleException(
                            "Khung giờ " + n + " trùng thời điểm với khung giờ " + (j + 1) + ".");
                }
            }
            String location = slot.location() == null ? "" : slot.location().strip();
            if (location.isEmpty()) {
                throw new InvalidInterviewScheduleException(
                        "Khung giờ " + n + ": vui lòng nhập địa điểm hoặc link phỏng vấn.");
            }
            if (location.length() > MAX_LOCATION_LENGTH) {
                throw new InvalidInterviewScheduleException(
                        "Khung giờ " + n + ": địa điểm tối đa " + MAX_LOCATION_LENGTH + " ký tự.");
            }
            if (location.indexOf('\r') >= 0 || location.indexOf('\n') >= 0) {
                throw new InvalidInterviewScheduleException(
                        "Khung giờ " + n + ": địa điểm chỉ được nằm trên một dòng.");
            }
            result.add(new ValidSlot(scheduledAt, location));
        }
        return result;
    }
}
