package com.recruitment.interviewinvitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

// subject/content la NGUYEN VAN noi dung HR se gui - CHU Y day la CHU Y THIET KE, khong phai
// thieu sot: backend KHONG render lai tu interview_templates va KHONG so khop lai voi template o
// buoc gui nay. HR da xem ban render o GET .../preview va co quyen sua tu do truoc khi gui (dung
// y SRS) - server chi luu lai chinh xac nhung gi HR gui len. max size khop voi cot that trong V1
// (interview_invitations.subject VARCHAR(255)); content ung voi cot TEXT khong gioi han trong DB,
// 10000 ky tu la chan o tang ung dung de tranh client gui chuoi khong lo, khong phai gioi han schema.
public record InterviewInvitationSendRequest(
        @NotNull Instant scheduledAt,
        String location,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 10000) String content) {
}
