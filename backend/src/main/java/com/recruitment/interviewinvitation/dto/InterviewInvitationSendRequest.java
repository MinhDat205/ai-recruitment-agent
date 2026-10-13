package com.recruitment.interviewinvitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// subject/content la NGUYEN VAN noi dung HR se gui - CHU Y day la CHU Y THIET KE, khong phai
// thieu sot: backend KHONG render lai tu interview_templates va KHONG so khop lai voi template o
// buoc gui nay. HR da xem ban render o GET .../preview va co quyen sua tu do truoc khi gui (dung
// y SRS) - server chi luu lai chinh xac nhung gi HR gui len. max size khop voi cot that trong V1
// (interview_invitations.subject VARCHAR(255)); content ung voi cot TEXT khong gioi han trong DB,
// 10000 ky tu la chan o tang ung dung de tranh client gui chuoi khong lo, khong phai gioi han schema.
//
// FR-H12 (muc 4.4) - than request chung cua I2 (gui lan dau) va I3 (gui lai). slots KHONG @NotEmpty/@Size, location
// KHONG @NotBlank: kiem o service (InterviewSlotValidator, R-S8) de tra dung cau theo tung khung. KHONG chuan hoa
// slots null -> List.of() o day: "slots null/thieu" phai den duoc bo kiem (dong 1 R-S8).
public record InterviewInvitationSendRequest(
        List<SlotRequest> slots,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 10000) String content) {

    public record SlotRequest(Instant scheduledAt, String location) {
    }
}
