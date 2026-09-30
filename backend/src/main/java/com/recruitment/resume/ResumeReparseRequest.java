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

// Hang doi trich xuat lai CV schema cu (FR-C05 R-R4, bang resume_reparse_requests - V8). Khuon
// CvImprovementRequest (V6) + cot backoff/claim nhu resumes (V7). resume_id la UUID thuong, khong
// @ManyToOne - cung ly do voi CvImprovementRequest (tranh lazy-loading ngoai transaction).
// error_message chi chua ResumeParsingErrorCode.formatted() - khong bao gio e.getMessage()/output LLM.
@Entity
@Table(name = "resume_reparse_requests")
@Getter
@Setter
@NoArgsConstructor
public class ResumeReparseRequest {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResumeReparseRequestStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
