package com.recruitment.blindreview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

// FR-H16 R-B3, R-B5 - mot dong luu vet moi lan cong tac an danh cua Job DOI gia tri. Chi ghi: khong setter, khong sua,
// khong xoa; khong co endpoint doc (R-B5). changed_at lay statement_timestamp() cua DB.
@Entity
@Table(name = "job_blind_review_changes")
@Getter
@NoArgsConstructor
public class JobBlindReviewChange {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false, updatable = false)
    private boolean enabled;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "changed_at", insertable = false, updatable = false)
    private Instant changedAt;

    public JobBlindReviewChange(UUID jobId, boolean enabled, UUID changedBy) {
        this.jobId = jobId;
        this.enabled = enabled;
        this.changedBy = changedBy;
    }
}
