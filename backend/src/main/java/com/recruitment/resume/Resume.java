package com.recruitment.resume;

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

// candidate_id la UUID thuong, khong dung @ManyToOne (tranh lazy-loading ngoai transaction).
// Bang resumes KHONG co updated_at (dung copy nguyen mau Company/Job) - chi co uploaded_at (BAT
// BIEN, @Generated INSERT) va claimed_at moi them o V7 (GHI TAY luc claim(), KHONG @Generated -
// khac uploaded_at, cot nay phai tu code set).
@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor
public class Resume {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private ResumeFileType fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "version_label")
    private String versionLabel;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false)
    private ParseStatus parseStatus;

    @Column(name = "parse_error")
    private String parseError;

    // Ghi luc claimForProcessing() chuyen PENDING->PROCESSING (Dot 4, chore/hardening) - dung cho
    // stale-claim reaper phat hien ban ghi "ket" qua stale-timeout-ms. KHONG @Generated: code phai
    // tu ghi (khac uploaded_at).
    @Column(name = "claimed_at")
    private Instant claimedAt;

    // So lan da thu tu dong khi gap loi LLM tam thoi (timeout/429/5xx) hoac stale-claim - dat ten
    // trung voi score_explanation_attempts.attempt_count (V5) cho nhat quan thuat ngu. int nguyen
    // thuy (khong wrapper): cot NOT NULL DEFAULT 0, luon co gia tri.
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    // Moc backoff truoc lan thu ke tiep - NULL nghia la san sang ngay (chua tung loi tam thoi lan
    // nao, hoac da FAILED han). Scheduler chi claim khi NULL hoac da qua moc nay.
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "uploaded_at", insertable = false, updatable = false)
    private Instant uploadedAt;
}
