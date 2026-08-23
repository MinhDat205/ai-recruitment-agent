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

// Bang hang doi cho F2 (FR-U05) - dong vai tro tuong duong resumes.parse_status cua D1 (xem V6
// migration). resume_id la UUID thuong, khong @ManyToOne - cung ly do voi Resume/ScoringRun (tranh
// lazy-loading ngoai transaction).
@Entity
@Table(name = "cv_improvement_requests")
@Getter
@Setter
@NoArgsConstructor
public class CvImprovementRequest {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CvImprovementRequestStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Generated(event = EventType.INSERT)
    @Column(name = "requested_at", insertable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
