package com.recruitment.aicontext;

import com.recruitment.jobapplication.ApplicationStatus;
import com.recruitment.messaging.MessageSenderRole;
import java.time.Instant;
import java.util.List;

// FR-C07 K1 (R-K1-2) - ngu canh cuoc trao doi cua MOT don gui cho AI. CHAN BANG KIEU DU LIEU: chi co dung cac thanh phan
// duoi day - KHONG them thanh phan nao chua diem, rubric, giai thich AI, ghi chu, cau hoi phong van; khong Map/Object/
// chuoi "phu" tu do. Khong co ho ten/email HR (C06 R-G2), khong email/dien thoai ung vien, khong subject/rendered_content
// cua giay moi (Q8), khong thu gioi thieu, khong application_status_history.note.
//
// Du lieu THO (L7): text cua tin la noi dung da luu nguyen van - khong cat, khong thay ky tu; viec cat 1000 code point,
// thay < >, ghi "(tep dinh kem)" thuoc MessageDraftService khi dung prompt (R-I5).
public record ConversationContext(
        ContextViewer viewer,
        String candidateName,
        String jobTitle,
        String companyName,
        ApplicationStatus applicationStatus,
        // null khi don chua co giay moi, hoac giay moi hieu luc chua chot khung (FR-H12 R-K1x)
        InterviewSchedule interview,
        // <= 10 tin gan nhat, cu truoc moi sau
        List<RecentMessage> recentMessages) {

    public ConversationContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    // Khung DA CHOT cua giay moi HIEU LUC (FR-H12 R-K1x, M4: hinh dang record giu nguyen, chi doi nguon). location co the
    // null (chi o du lieu cu chuyen tu V1).
    public record InterviewSchedule(Instant scheduledAt, String location) {
    }

    // text null khi tin chi co tep.
    public record RecentMessage(MessageSenderRole senderRole, String text, boolean hasAttachment) {
    }
}
