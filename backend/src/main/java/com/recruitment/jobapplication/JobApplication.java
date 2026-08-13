package com.recruitment.jobapplication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// job_id/candidate_id/resume_id la UUID thuong, khong dung @ManyToOne (tranh lazy-loading ngoai
// transaction). ai_consent/ai_consent_at KHONG @Generated - DB khong co default, phai set trong
// service luc tao don. updated_at CO trigger set_updated_at (dang ky cho job_applications o V1).
@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
public class JobApplication {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "recruitment_cycle", nullable = false)
    private int recruitmentCycle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "ai_consent", nullable = false)
    private boolean aiConsent;

    @Column(name = "ai_consent_at", nullable = false)
    private Instant aiConsentAt;

    @Column(name = "cover_letter")
    private String coverLetter;

    @Generated(event = EventType.INSERT)
    @Column(name = "applied_at", insertable = false, updatable = false)
    private Instant appliedAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;
}
