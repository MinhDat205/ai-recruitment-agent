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
        // null khi don chua co giay moi
        InterviewSchedule interview,
        // <= 10 tin gan nhat, cu truoc moi sau
        List<RecentMessage> recentMessages) {

    public ConversationContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    // Giay moi MOI NHAT cua don. location co the null. FR-H12 tu mo rong khi co nhieu khung gio (R-K1-5).
    public record InterviewSchedule(Instant scheduledAt, String location) {
    }

    // text null khi tin chi co tep.
    public record RecentMessage(MessageSenderRole senderRole, String text, boolean hasAttachment) {
    }
}
