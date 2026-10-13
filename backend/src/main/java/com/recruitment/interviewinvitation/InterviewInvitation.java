package com.recruitment.interviewinvitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// rendered_content la NGUYEN VAN noi dung HR da gui - KHONG FK ve interview_templates, KHONG luu
// templateId. HR sua template sau nay khong duoc lam doi noi dung loi moi da gui (cung tinh than
// rubric_snapshot/weight_snapshot o scoring_runs/criterion_scores - xem CLAUDE.md muc 2 va 4).
// sent_at/sent_by nullable san trong schema (V1) de danh cho tinh nang nhap sau nay - o E1 Dot 2,
// hai cot nay LUON duoc set ngay luc tao dong (gui = tao, khong co trang thai nhap).
//
// FR-H12 (V16) - thoi diem/dia diem nam o interview_invitation_slots (InterviewInvitationSlot), khong con o day.
// confirmed_slot_id la cot UUID THUONG, KHONG @ManyToOne (REQUIREMENT muc 12 L2): Hibernate khong biet FK ghep
// fk_invitation_confirmed_slot nen thu tu ghi do code quyet dinh - giay moi (chua co khung chot) -> khung -> dat khung
// chot. Gan confirmedSlotId TRUOC khi khung duoc INSERT thi cau INSERT giay moi (hoan toi flush) vi pham FK.
@Entity
@Table(name = "interview_invitations")
@Getter
@Setter
@NoArgsConstructor
public class InterviewInvitation {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private String subject;

    @Column(name = "rendered_content", nullable = false)
    private String renderedContent;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_by")
    private UUID sentBy;

    // NULL = chua chot (R-K2). Cung NULL/khac NULL voi confirmedAt (chk_invitation_confirmed_pair).
    @Column(name = "confirmed_slot_id")
    private UUID confirmedSlotId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    // NULL = giay moi dang hieu luc (R-A1, uq_invitation_active).
    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
