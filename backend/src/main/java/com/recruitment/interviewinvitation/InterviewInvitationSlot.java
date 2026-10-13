package com.recruitment.interviewinvitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

// FR-H12 - mot khung gio cua giay moi (REQUIREMENT muc 4.1). BAT BIEN (R-C2): @Immutable, khong setter, khong
// updated_at, khong endpoint sua/xoa. invitation_id la cot UUID thuong (khong @ManyToOne), cung quy uoc
// InterviewInvitation.applicationId.
//
// KHONG @Generated cho created_at (muc 12 L3): Hibernate 7.4.1 doc lai cot generated sau INSERT -> dat khoa WRITE ->
// UnsupportedLockAttemptException voi entity @Immutable (JobAiGeneration.java). DB tu dien DEFAULT now().
@Entity
@Immutable
@Table(name = "interview_invitation_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewInvitationSlot {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "invitation_id", nullable = false, updatable = false)
    private UUID invitationId;

    @Column(name = "display_order", nullable = false, updatable = false)
    private short displayOrder;

    @Column(name = "scheduled_at", nullable = false, updatable = false)
    private Instant scheduledAt;

    // NULL chi o du lieu cu chuyen tu V1 (R-S6).
    @Column(updatable = false)
    private String location;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public InterviewInvitationSlot(UUID invitationId, int displayOrder, Instant scheduledAt, String location) {
        this.invitationId = Objects.requireNonNull(invitationId, "invitationId");
        this.displayOrder = (short) displayOrder;
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt");
        this.location = location;
    }
}
