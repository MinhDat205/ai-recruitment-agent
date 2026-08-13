package com.recruitment.interviewtemplate;

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

// Gan 1-1 voi Job (job_id UNIQUE). KHONG co cot ngay gio phong van o day - do la
// interview_invitations.scheduled_at, tao rieng cho tung ung vien o nhanh E1 (FR-H07).
// Template chi la mau dung chung, khong gan voi mot lich hen cu the nao. Neu sau nay co ai
// them field kieu Instant/LocalDate(Time) vao day ngoai created_at/updated_at, do la vi pham
// ranh gioi FR-H02 <-> FR-H07 - xem InterviewTemplateOwnerIntegrationTest.
@Entity
@Table(name = "interview_templates")
@Getter
@Setter
@NoArgsConstructor
public class InterviewTemplate {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "sender_title")
    private String senderTitle;

    private String address;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
