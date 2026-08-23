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

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    private String location;

    @Column(nullable = false)
    private String subject;

    @Column(name = "rendered_content", nullable = false)
    private String renderedContent;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "sent_by")
    private UUID sentBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
